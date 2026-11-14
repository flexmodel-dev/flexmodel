# Engine 事务 UnitOfWork 下沉技术方案

## 一、目标与边界

将 server 自建事务层（`ProjectUnitOfWork` / `ServerSessions.openOwned` / `ServerSqlSession` / `ManagedSession`）的全部能力下沉为
flexmodel-core 的一等能力。下沉后 server 的事务代码收敛为两个职责极薄的组件：projectId→schema 解析门面、REST 异常映射；其余事务语义全部由
engine 提供并被 server 直接消费。

### 1.1 本期范围

| 能力                   | 说明                                                                                                 |
|------------------------|------------------------------------------------------------------------------------------------------|
| 事务传播               | 同 schema 嵌套加入当前事务；跨 schema 嵌套挂起外层、开独立事务、结束后恢复                           |
| afterCommit 回调       | 注册到当前事务，提交成功且回调所在上下文结束后按序执行；回滚丢弃；无事务上下文时立即执行             |
| 事务感知事件           | 后置 ChangedEvent 仅在"操作成功且所在事务提交"后发布；回滚丢弃；发布时点保持在连接归还之后           |
| 行锁 API               | `lockRow`（占位更新锁）与 `lockRows`（FOR UPDATE 批量锁，方言降级）；必须在事务内                    |
| 会话借出（lease）      | 从当前事务上下文借出禁 close/commit/rollback 的代理 Session                                          |
| 会话创建选项           | 隔离级别（READ_COMMITTED / SERIALIZABLE）+ 方言连接准备钩子（SQLite busy_timeout）                   |
| 写拦截器 SPI           | 工厂级注册，拦截 DataService 全部写方法；server 审批锁 Guard 改造为拦截器实现                        |
| Session 契约扩展       | `isTransactionActive` / `afterCommit` / `lockRow` / `lockRows` / `mergeUpdate` 平移至 engine Session |
| SQLite busy 类型化异常 | engine 抛 `DatabaseBusyException`，REST 503 映射留在 server                                          |

### 1.2 本期不包含

| 项                           | 去向                                                                                                         |
|------------------------------|--------------------------------------------------------------------------------------------------------------|
| 业务库与平台库跨库原子性     | 维持独立事务 + outbox（ReliableMessages）现状                                                                |
| REQUIRES_NEW 显式传播        | 现有全部调用模式（46 处事务入口）仅需要"加入 + 跨 schema 挂起"；出现真实需求再扩展                           |
| 乐观锁 / @version            | 独立改善项（P1）                                                                                             |
| 批量 updateAll / ID 批量回填 | 独立改善项（P0）                                                                                             |
| 错误分类全体系               | 独立改善项（P2），本期仅新增 `DatabaseBusyException` 一个类型                                                |
| Mongo 事务                   | 明确不支持：UnitOfWork 遇非 JdbcSchemaProvider 抛 `UnsupportedOperationException`，与现有 openOwned 行为一致 |

## 二、现状契约盘点

### 2.1 server 影子层事实（下沉后必须逐条等价保留）

| 组件                                                      | 现有契约                                                                                                                                                                                        |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ProjectUnitOfWork.execute(projectId)`                    | 解析 project.databaseName 为 schema，openOwned 自建会话 + startTransaction；13 处调用                                                                                                           |
| `ProjectUnitOfWork.executePlatform(projectId)`            | schema 固定为 `system`；27 处调用；跨 schema 嵌套开独立事务并恢复外层                                                                                                                           |
| `ProjectUnitOfWork.adopt(projectId, session, action)`     | 3 处：两处 SERIALIZABLE 导出快照会话、一处 Guard 拦截路径；session 无事务则为其开启并拥有提交权，有事务则仅加入并挂 afterCommit                                                                 |
| `ProjectUnitOfWork.afterCommit(Runnable)`                 | ThreadLocal 注册；无上下文立即执行；6 处（历史落库、通知、SSE wake、Vert.x 通知），其中历史落库回调内再开新的平台事务                                                                           |
| `ProjectUnitOfWork.lock(projectId, model, column, value)` | 校验当前事务属于该项目后行锁；13 处直调 + 8 处包装方法                                                                                                                                          |
| `borrowPlatform()` / `borrowSchema(schema)`               | lease 代理（close/commit/rollback/startTransaction 为 no-op）；AbstractRepository（28 个平台仓储子类）、通知、Quartz 任务仓库使用                                                               |
| `ServerSessions.openOwned`                                | 直取 `DataSource.getConnection()`；非快照设 READ_COMMITTED；SQLite 打 `PRAGMA busy_timeout=5000`                                                                                                |
| `ServerSessions.openSnapshot`                             | 设 SERIALIZABLE，先校验数据源支持，不支持即抛出                                                                                                                                                 |
| `ServerSqlSession`                                        | 继承 engine `SqlSession`：事件边界记录、回滚丢弃事务内事件、commit 迁移回调、close 归还连接后依次执行已提交回调；data() 上动态代理拦截 insert\*/update\*/delete\* 交给 Guard；SQLite busy → 503 |
| `ManagedSession.mergeUpdate`                              | 读-合并-写部分更新（AppFlowDefinitionRepository 等使用）                                                                                                                                        |

### 2.2 时序基线（ServerSqlSession + ProjectUnitOfWork 实测顺序，新实现必须保持）

```text
execute:
  取连接(设隔离级别/PRAGMA) → startTransaction(记录事件边界)
  → 业务操作(pre事件即时、post事件缓冲) → commit(确认事件、迁移回调)
  → close: 连接归还 → flush 后置事件 → 执行 afterCommit 回调(逐个吞异常)
rollback:
  super.rollback → 丢弃事务边界之后的事件 → 清空回调 → close: 连接归还（无事件、无回调）
close 时仍有未提交事务:
  丢弃事务内事件与回调 → 连接归还
```

关键点：事件与回调都在 **连接归还之后**执行。engine 现有 `EventAwareDataService` 注释明确了该设计动机——监听器重入取连接时业务线程已释放连接，避免小连接池耗尽。

## 三、新契约设计

### 3.1 Session 接口扩展（dev.flexmodel.session.Session）

| 方法                                                            | 语义                                                                                                  | SQL 实现                         | 默认实现（Mongo 等）               |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|----------------------------------|------------------------------------|
| `boolean isTransactionActive()`                                 | 当前会话是否处于显式事务中                                                                            | `!connection.getAutoCommit()`    | 返回 false                         |
| `void afterCommit(Runnable callback)`                           | 无事务 → 立即执行；有事务 → 缓冲，提交确认后于连接归还后执行；回滚丢弃                                | 见 3.3 回调规则                  | 无事务语义，立即执行               |
| `int lockRow(String model, String column, Object value)`        | 占位更新行锁 `update t set k=k where k=:v`；要求事务内；校验字段存在                                  | 校验 EntityDefinition 字段后执行 | 抛 `UnsupportedOperationException` |
| `List<Map<String,Object>> lockRows(String model, Query query)`  | `query.setForUpdate(true)` 后 `data().findAll`；方言不支持 FOR UPDATE → 按 id 排序后逐行 lockRow 降级 | 见左                             | 抛 `UnsupportedOperationException` |
| `default void mergeUpdate(String model, String id, Map values)` | findById → 合并 → updateById（自 ManagedSession 平移，纯组合现有 API）                                | 默认方法即可                     | 默认方法                           |

约束：

- `lockRow` / `lockRows` 在事务外调用抛 `IllegalStateException("行锁必须位于事务内")`，与现行为一致。
- `lockRows` 返回锁定的行集，调用方仅可基于该集合继续变更（Guard 依赖此契约：把 update/delete 的过滤条件重写为
  `id in (已锁行)`）。

### 3.2 事件与提交时序（engine EventAwareDataService + SqlSession 改造）

| 事件点                                    | 新语义                                                                                   |
|-------------------------------------------|------------------------------------------------------------------------------------------|
| pre 事件（PreInsert/PreUpdate/PreDelete） | 即时发布，允许监听器改写数据（现有行为不变）                                             |
| post 事件（成功操作）                     | 缓冲至 pending；事务提交确认后，仍保持"连接归还后 flush"的发布时点                       |
| post 事件（失败操作）                     | 不再发布。事件可见性口径升级为"操作成功且事务已提交"；`success` 标志字段保留在事件对象上 |
| rollback                                  | 丢弃自 `startTransaction` 边界以来的全部 pending 事件                                    |
| close（事务未提交）                       | 等同 rollback 的丢弃语义                                                                 |

实现契约：`EventAwareDataService` 增加事务边界协作面（`markEventBoundary()` / `discardEventsSince(boundary)`），由
`SqlSession.startTransaction` / `rollback` 驱动；`flushPendingEvents` 发布时点不变（连接归还后）。

验收前置清单：实施时逐个确认 `ChangedEvent` 现有监听器（Realtime 广播、AI 工具变更等）均只消费成功事件，再移除失败事件发布。

### 3.3 UnitOfWork（dev.flexmodel.session.UnitOfWork）

构造：由 `SessionFactory` 创建（`sessionFactory.unitOfWork()`）；server 在 EngineConfig 中注册为 CDI bean。engine 不引入 CDI
依赖。

#### API

| 成员       | 签名                                                          | 语义                                                                                                               |
|------------|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| 事务模板   | `<T> T execute(String schema, Supplier<T> action)`            | 传播矩阵见下；自建会话采用 `SessionOptions.readCommitted()` + 方言连接准备                                         |
| 接管会话   | `<T> T adopt(Session session, Supplier<T> action)`            | 以传入会话为主体压入新上下文帧；其无事务 → 为其开启并拥有提交权；有事务 → 加入，回调走该会话；结束后恢复外层上下文 |
| 上下文查询 | `static boolean isActive()` / `static String currentSchema()` | ThreadLocal 自省；Quartz/定时线程自含上下文，无需 CDI 请求上下文                                                   |
| 提交后回调 | `static void afterCommit(Runnable action)`                    | 有上下文 → 注册到当前帧；无上下文 → 立即执行                                                                       |
| 行锁       | `static int lock(String model, String column, Object value)`  | 当前上下文内 `session.lockRow`；无上下文抛 `IllegalStateException`                                                 |
| 借出       | `static Session borrow(String schema)`                        | 当前帧 schema 匹配 → 返回 lease 代理；否则 null                                                                    |

#### 传播矩阵

| 当前上下文     | `execute(同 schema)`                                                                                                      | `execute(异 schema)`                             |
|----------------|---------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| 无             | 新开事务                                                                                                                  | 新开事务                                         |
| 有             | **加入**：直接执行 action，不取新连接（DataFmRepository.updateRecords 这类"事务内自管 commit"的旧写法在新契约下不再需要） | **挂起**：压入新帧开独立事务，finally 恢复外层帧 |
| adopt 目标会话 | ——                                                                                                                        | 上下文压入该会话帧，同上恢复规则                 |

#### 上下文与回调规则

- ThreadLocal 保存栈式帧：`Frame(schema, session, afterCallbacks)`；跨 schema 嵌套压栈/恢复，禁止跨项目由 server
  门面校验（engine 不感知 projectId）。
- 回调执行时点：`commit` 成功 → 会话 close（连接归还）→ 逐个执行回调；单个回调异常记录 warn 后继续，不阻断其余回调（现
  ServerSqlSession 行为）。
- 异常契约：action 抛 RuntimeException/Error → rollback + 丢弃事件/回调 + 原样重抛。
- 非 JdbcSchemaProvider：抛 `UnsupportedOperationException("事务工作单元需要 SQL 数据源")`。

### 3.4 SessionOptions 与方言连接准备

`SessionFactory.createSession(String schema, SessionOptions options)`（原单参重载语义不变，等价于
`SessionOptions.none()`）：

| 选项         | 取值                                            | 行为                                                                                                                                                                         |
|--------------|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 隔离级别     | `none()` / `readCommitted()` / `serializable()` | none=沿用池默认（独立 engine 客户端行为不变）；serializable 先查询 `supportsTransactionIsolationLevel`，不支持即抛出（现 openSnapshot 行为）                                 |
| 方言连接准备 | 自动                                            | 新增 `SqlDialect.prepareConnection(Connection)` 钩子；`SQLiteSqlDialect` 实现执行 `PRAGMA busy_timeout=5000`。engine 全部 SQL 会话统一应用（现仅 server openOwned 路径应用） |

### 3.5 WriteInterceptor SPI

```java
public interface WriteInterceptor {
  Object intercept(Session session, String operation, Object[] args, Supplier<Object> proceed);
}
```

| 契约点 | 语义                                                                                                            |
|--------|-----------------------------------------------------------------------------------------------------------------|
| 注册   | `SessionFactory.Builder.addWriteInterceptor(...)`；会话创建时（含 failsafe 会话）对 `data()` 统一包装           |
| 拦截面 | DataService 方法名以 `insert` / `update` / `delete` 开头的全部写方法；`operation` 为方法名，`args` 为原参数数组 |
| 改写   | 拦截器可原地改写 args（Guard 现将过滤参数改写为 `id in (已锁行)`），`proceed()` 使用同一数组                    |
| 返回   | 拦截器返回值即调用结果；不拦截直接 `proceed.get()`                                                              |

### 3.6 lease 借出契约

`UnitOfWork.borrow(schema)` / server 门面 `borrowPlatform()` 返回的代理：

| 方法                                                                                                         | 行为                            |
|--------------------------------------------------------------------------------------------------------------|---------------------------------|
| close / commit / rollback / startTransaction                                                                 | no-op（借用方不得影响宿主事务） |
| data() / schema() / dsl() / getName() / isTransactionActive / afterCommit / lockRow / lockRows / mergeUpdate | 原样委托宿主会话                |

## 四、关键决策与取舍

| # | 决策                                                                          | 备选                            | 理由                                                                                                     |
|---|-------------------------------------------------------------------------------|---------------------------------|----------------------------------------------------------------------------------------------------------|
| 1 | ThreadLocal 栈式上下文                                                        | 显式 Context 对象传参           | 与现状一致；Quarkus 阻塞线程与虚拟线程（ReliableMessages worker）均自含；显式传参需改 46 处调用入口      |
| 2 | engine 按 schema，server 保留 projectId 门面                                  | engine 引入 SchemaResolver SPI  | engine 无项目概念；门面仅剩"projectId→schema 解析 + 跨项目校验"，40 处调用点签名不变                     |
| 3 | 事件发布时点维持"连接归还后"                                                  | commit 后立即发布               | 保留现有池耗尽规避设计（EventAwareDataService 注释记载）；顺序契约固化为 commit → 连接归还 → 事件 → 回调 |
| 4 | 失败操作不再发布 post 事件                                                    | 保留 success=false 事件         | 事件口径升级为"成功且已提交"；广播通道仅消费成功事件，实施时逐监听器确认                                 |
| 5 | 仅实现 REQUIRED + 跨 schema 挂起                                              | 完整传播枚举（REQUIRES_NEW 等） | 全部现有调用模式（13 execute + 27 executePlatform + 3 adopt）不需要 REQUIRES_NEW；避免过度设计           |
| 6 | Guard 从"每会话动态代理"改为"工厂级拦截器"                                    | 保留代理                        | 拦截点单点化、全部 SQL 会话一致生效；内部模型白名单留在 Guard 实现内                                     |
| 7 | SQLite busy 抛 engine 类型化 `DatabaseBusyException`，503 映射留 server       | engine 依赖 JAX-RS              | flexmodel-core 保持零 Web 依赖                                                                           |
| 8 | ServerSqlSession 的 openOwned 直连绕道收敛为 `createSession(schema, options)` | engine 开放裸 Connection 构造   | 路径统一后 isSchemaExists 校验对 server 事务路径同样生效                                                 |
| 9 | 跨库原子性不做                                                                | 两阶段提交 / XA                 | server 注释已声明平台库与业务库非原子；outbox（ReliableMessages）承担跨库最终一致                        |

## 五、server 迁移映射

### 5.1 旧 API → 新 API

| 旧（server）                                                  | 新                                                                                   | 迁移动作                                                                                                         |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| `ProjectUnitOfWork.execute / executePlatform`                 | engine `uow.execute(schema)`（门面解析 databaseName / "system"）                     | 门面保留签名，方法体改为一行委托；46 处调用点不改                                                                |
| `ProjectUnitOfWork.adopt`                                     | engine `uow.adopt(session, ...)`（门面用 `session.getName()` 对齐 schema）           | 同上                                                                                                             |
| `ProjectUnitOfWork.afterCommit`                               | engine `UnitOfWork.afterCommit`                                                      | 门面静态转发                                                                                                     |
| `ProjectUnitOfWork.lock`                                      | engine `UnitOfWork.lock`（门面保留"当前项目"校验）                                   | 同上                                                                                                             |
| `borrowPlatform / borrowSchema`                               | engine `UnitOfWork.borrow("system" / schema)`                                        | AbstractRepository.openSession 改一行；28 个子类不改                                                             |
| `ServerSessions.openOwned / openSnapshot`                     | engine `createSession(schema, readCommitted()/serializable())` + 工厂级 Guard 拦截器 | ServerSessions 收敛为 `open = borrow ?: createSession(options)`                                                  |
| `ServerSqlSession`（事件边界/回调/锁/代理）                   | engine `SqlSession` + `Session` 扩展 + `WriteInterceptor`                            | 整类删除                                                                                                         |
| `ManagedSession`（接口 + managed() 强转）                     | engine `Session` 扩展方法                                                            | 接口与 `ServerSessions.managed` 删除；`ServerSessions.managed(session).lockRow(...)` 等改 `session.lockRow(...)` |
| `ServerSqlSession.databaseFailure`（busy→503）                | engine `DatabaseBusyException` + server 异常映射器（复用 `SqlErrors` 判定）          | 映射器挂 REST 层                                                                                                 |
| Guard 动态代理包装                                            | Guard 实现 `WriteInterceptor`，EngineConfig 注册                                     | `ServerSessions.managed(session).lockRow/lockRows` 改 `session.lockRow/lockRows`；adopt 走门面                   |
| `DataFmRepository.updateRecords` 自管 startTransaction/commit | 事务内直接执行（借出会话上这些调用本就是 no-op）                                     | 删除自管代码                                                                                                     |
| 历史落库 afterCommit 内开新平台事务                           | 不变（回调在上下文清空后执行，新事务自然独立）                                       | 无                                                                                                               |

### 5.2 可删除清单

`ServerSqlSession`、`ManagedSession`、`ProjectUnitOfWork.lease`、`ServerSessions.openOwned/openSnapshot/managed` 的实现体、
`DataFmRepository.updateRecords` 事务自管段。`ProjectUnitOfWork` 保留为约 30 行门面；`ServerSessions` 保留 `open()` 一行策略。

## 六、验收标准

### 6.1 engine 单元测试矩阵（flexmodel-core 新增）

| 场景             | 断言                                                                                         |
|------------------|----------------------------------------------------------------------------------------------|
| 传播-加入        | 同 schema 嵌套 execute 两个 action，仅一次连接获取、一次 commit                              |
| 传播-挂起        | 跨 schema 嵌套，内层 rollback 不影响外层；外层提交后内层上下文已恢复                         |
| adopt-开启       | 无事务会话被 adopt 后由 UoW 提交/回滚                                                        |
| adopt-加入       | 有事务会话被 adopt 后 UoW 不重复提交，回调注册到该会话                                       |
| 事件-提交        | 事务内成功写入 → commit → close 后事件按序发布且仅一次                                       |
| 事件-回滚        | rollback 后事务内产生的 post 事件全部丢弃                                                    |
| 事件-失败        | 单条写入抛异常 → 该操作不产生 post 事件；pre 事件已发布（现行为）                            |
| 回调-afterCommit | 提交后按注册序执行；单个回调抛异常不影响后续；rollback 全部丢弃                              |
| 回调-无上下文    | `UnitOfWork.afterCommit` 无事务上下文时立即执行                                              |
| 行锁             | lockRow/lockRows 事务外抛 IllegalStateException；lockRows 在 SQLite 方言下降级为逐行 lockRow |
| 借出             | borrow 代理 close/commit/rollback 无副作用；宿主事务随后正常提交                             |
| 选项             | serializable 在不支持的数据源上抛出；SQLite 会话连接自动带 busy_timeout                      |
| Mongo            | uow.execute 对 Mongo schema 抛 UnsupportedOperationException                                 |

### 6.2 server 行为对照（迁移后逐项回归）

应用整包导入 records 阶段单事务 + 历史 afterCommit 落库；transfer 导出 SERIALIZABLE 快照 + 事务外写回执；审批中记录编辑/删除返回
409；流程定义/实例并发流转行锁互斥；记录批量更新原子；ReliableMessages outbox 锁与重试；GraphQL 单 mutation 事务内完成；Quartz
定时任务线程（无 CDI 请求上下文）正常开启平台事务。

### 6.3 删除证明与验证命令

- `grep` 无残留：`ServerSqlSession`、`ManagedSession`、`borrowPlatform`、`openOwned`、`managed(`。
- `mvn test -pl flexmodel-engine`（新增矩阵全绿）。
- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'`（全模块编译）。
- `mvn clean test -pl flexmodel-server -am`（既有已知失败范围不变，无新增失败）。
- flexmodel-core 的 pom 依赖集合不新增 Web/CDI 依赖。

## 七、实施阶段

| 阶段 | 内容                                                                                                                                               | 产出                          |
|------|----------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|
| 一   | engine：Session 契约扩展 + 事件边界/失败语义 + SessionOptions + 方言 prepareConnection + WriteInterceptor SPI + UnitOfWork + DatabaseBusyException | 6.1 全部单测绿                |
| 二   | server：EngineConfig 注册 UoW 与 Guard 拦截器；门面瘦身；按 5.1 映射迁移；删除 5.2 清单                                                            | 编译通过 + 6.2 回归           |
| 三   | 验收：6.3 命令全量执行；更新 feature_list.json 与 progress.md                                                                                      | 会话可由 `./init.sh` 直接接续 |
