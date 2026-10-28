# 表单流程与审批接入

## 边界

应用内流程由表单集成层管理。草稿、接口和发布快照统一使用 Flow 结构；`flow_model` 为 JSON 字符串，内部是 `flowElementList`
。FlowGram 节点树仅作为编辑器及业务配置校验的内存视图，由适配器转换。Flow 负责启动、挂起、继续、条件分流、结束和通用事件；`flow`
包不依赖审批或表单模块。

草稿直接存储在既有 `f_em_flow_definition`，发布直接调用 Flow 的部署能力，版本存储在 `f_em_flow_deployment`
；同一流程保存、反复发布均复用同一个 `flow_module_id`。不创建审批专用定义表或发布表。`flow_key` 新建时可指定，未填写则生成，后续保存保持稳定。

开始节点的 `properties.approval` 仅保存业务接入元数据：定义中的 `revision` 用于并发控制，`deploymentId` 指向当前启用的部署；部署快照中的
`children` 按子流程节点关联发布时选中的子流程部署 ID。客户端不能写入这些受信任元数据。原生 Flow 状态保持初始化/编辑/下线语义；审批
API 为现有界面映射为 1=草稿、2=已启用、3=已停用，停用同步使用原生定义的下线状态。

节点使用 Flow 的 key/type/incoming/outgoing/properties：开始 2、结束 3、人工或数据任务 4、排他网关 6、条件连线
1。审批人、签核方式和数据操作配置放在 `properties.config`；表单绑定放在开始节点 `properties.form`；设计器父节点、顺序和空容器标记放在
`properties.designer`，不再另存完整节点树。分支本身是条件连线，分支名称和配置保存在其
properties。服务端检查图连线与设计器结构一致，并从类型化条件配置生成执行表达式；客户端不能注入发布处理器归属。

这是审批设计器的 Flow 模型协议：普通 Flow 模型若缺少审批配置和设计器层级元数据，不能直接作为审批定义导入。保存草稿起即注入受信任的
handler/handlerOwner，通用 Flow 写入入口不能绕过审批服务。运行实例通过 `flow_deploy_id` 读取部署配置，修改草稿、重新发布、停用或删除定义均不改写已有部署。

- `approval`：以 `Approval*` 类统一承载审批定义、发布校验、数据操作、子流程、成员解析、待办签核和审批中心；通用引擎继续使用
  `Flow` 术语。
- `common/messaging`：事务 Outbox、按根审批实例顺序投递、失败重试。
- Flow 外部任务带有通用 `handler`，受 `handlerOwner` 保护。只有服务端受信任处理器能够继续所属实例；REST 参数不能冒充处理器。

记录详情的“提交审批”是本版触发入口，不自动拦截每次表单新增或修改来发起流程。

## 持久化

| 存储                                            | 职责                                                                      |
|-------------------------------------------------|---------------------------------------------------------------------------|
| `f_em_flow_definition` / `f_em_flow_deployment` | 复用 Flow 草稿与不可变部署                                                |
| `f_ei_flow_instance` / `f_ei_node_instance`     | 复用 Flow 运行状态与节点关系                                              |
| `f_ei_instance_data`                            | 唯一运行变量存储；启动前由启动命令承载初始变量                            |
| `f_ei_node_instance_log`                        | 复用执行日志，`event_key` 保证业务节点幂等，`attributes` 保存业务输入输出 |
| `f_app_approval_instance`                       | 业务记录与 Flow 实例/部署关联、提交快照、幂等键和审批结果                 |
| `f_app_approval_wait`                           | 冻结执行人和签核聚合状态，节点名称、配置和变量从 Flow 读取                |
| `f_app_approval_task`                           | 每个人的待办、顺序及决定                                                  |
| `f_app_approval_record_lock`                    | 审批期间的业务记录锁                                                      |
| `f_runtime_message`                             | 通用可靠事件与命令 Outbox                                                 |

审批实例运行中只保存业务状态 ACTIVE，接口根据 Flow 实例关联显示 SUBMITTING/RUNNING。父子关系只保存父任务实例
ID，通过原生节点实例反查父流程；不保存重复变量、父流程 ID、父节点 Key 或独立节点日志表。

提交与数据修改均先锁定实际业务记录；签核和实例管理锁定根审批关联行；Flow 修改/部署/删除锁定定义行，继续/终止锁定引擎实例行。锁范围覆盖所操作的记录或实例。

## 可靠性与幂等

提交事务同时写入实例、记录快照、记录锁和启动命令。Flow 状态变化与事件写入同一个项目数据库事务。消费者在同一事务中创建待办或执行数据节点，并确认消息；异常全部回滚。

消息以 `ordering_key` 按根审批实例分组，同组按自增 ID
顺序投递，延迟消息只阻塞所属组。消费者锁定该组最早的消息行作为稳定协调点，再锁定待投递消息，在事务内重新确认顺序和状态。不同组通过虚拟线程并行处理，支持多服务实例共同消费；没有项目级锁表。失败指数退避，最多
8 次后进入 `FAILED`；管理员可重试或终止实例。已完成消息暂不清理，因为组内最早消息仍是协调点。

- 发起使用“用户 + requestKey”，相同参数返回原实例，复用键提交其他记录或流程返回 409。
- 待办以引擎任务 ID 和人员序号唯一标识；重复挂起事件不会重复创建待办。
- 签核校验实际登录人、任务状态、requestKey 和内容；重复同一决定返回原结果，冲突决定返回 409。
- 数据节点日志以引擎任务 ID 唯一标识，执行与完成命令原子提交。
- 子流程使用确定性实例 ID，并固定父流程发布时选中的子流程版本。

这是一种至少一次投递加幂等消费的实现，不承诺任意外部 HTTP 调用只发生一次。原有 Vert.x/RabbitMQ 通知保持提交后的兼容通知，可靠业务消费应实现
`DurableFlowListener`，不要依赖通知总线确认。

## 执行语义

执行人从实际启用用户及其角色、部门、直属上级关系解析。每组先解析来源，再计算本人、直接下级、所有下级或直接上级，最后按规则顺序稳定去重。角色和部门展开按用户
ID 排序，指定用户保留选择顺序。单节点最多 1000 人；空人员集使消息失败，不能自动通过。

会签全部同意、或签任一同意、依次审批按顺序同意；任一有效拒绝终止审批。下游“流程节点执行人”引用实际同意的签核人，或签未参与者不包含在内。

网关从上到下匹配第一个成立条件，未命中进入唯一默认分支。数据节点通过当前应用真实表单元数据和现有表单服务验证写入；查询和变更单节点最多
1000 条。完成的数据节点独立提交，后续拒绝或终止不会自动撤销已经发生的业务数据变更。

提交时保留原记录快照。服务端通过 `ServerSessions` 打开的数据会话检查审批记录锁；REST、GraphQL、批量/筛选写入及 Flow
数据任务均使用该入口。人工更新和删除锁定记录返回 409；GraphQL 通过标准 errors
返回拒绝原因。所属根流程及其子流程可执行配置的数据操作；其他流程仍被锁阻止，结束后解除锁。独立使用的引擎 SessionFactory、原生
SQL、管理 DDL 和外部直接写数据库属于底层或管理能力，不承载审批业务规则。

审批接入不再修改 `flexmodel-engine`。`common/persistence` 在服务端使用引擎现有公开 SQL API
管理会话、事务状态、运行时行锁、提交后通知和回滚事件丢弃；服务端 GraphQL 复用生成的 Schema、查询及指令处理，仅替换 SQL
项目的写执行器。借用事务的 Repository 不能提前提交或关闭外层会话。回滚事件和事务归属是各 SQL 数据库共有的正确性要求，不按
SQLite 特例放宽。

SQLite 仅作为开发环境：服务端连接设置 1 秒 busy_timeout，锁竞争超过等待时间返回可重试的 503（或 GraphQL
errors）。不自动重放业务操作、不跳过审批锁，也不吞掉其他 SQL 错误。MySQL 等数据库保持其自身锁等待策略；不为 SQLite
改动引擎或增加生产级写并发补偿。

当前可靠事务实现要求 SQL 项目数据库。非 SQLite 连接使用 READ_COMMITTED，避免锁等待后读到旧快照。提交/回滚事件及同记录等待、不同记录独立提交使用
SQLite 和 MySQL 8.0 验证，完整审批浏览器闭环使用隔离 SQLite。Mongo 会话尚无真实事务和行锁实现，不能用于此审批运行时；普通
Mongo 数据访问和 GraphQL 保持原有实现。目标数据库的完整业务闭环仍应在部署前验证。

## 执行人来源

审批执行人支持指定用户、角色、组织架构、发起人、上游审批节点和流程变量。自定义执行人脚本已移除，审批功能无需配置脚本密钥或依赖
Functions Runtime。发布和解析仅接受这六种人员来源。

## 接口与前端

以下路径均位于 `/api` 下：

| 路径                                                            | 用途                                      |
|-----------------------------------------------------------------|-------------------------------------------|
| `/apps/{appId}/approval-flows`                                  | 列表、新建；`/{id}` 读取、保存、删除      |
| `/apps/{appId}/approval-flows/validate`                         | 发布前服务端校验，返回可定位节点的问题    |
| `/apps/{appId}/approval-flows/{id}/status`                      | 带 revision 发布启用或停用                |
| `/apps/{appId}/approval-directory`                              | 真实成员、角色和组织；`/resolve` 解析预览 |
| `/apps/{appId}/forms/{form}/records/{recordId}/approvals`       | POST 幂等发起，GET 历史及锁状态           |
| `/apps/{appId}/approval-instances/{id}`                         | 实例快照、签核、日志、子流程和失败消息    |
| `/apps/{appId}/approval-instances/{id}/{retry\|terminate}`      | 管理恢复操作                              |
| `/apps/{appId}/approval-tasks/{id}/decision`                    | 本人同意或拒绝                            |
| `/approvals/tasks`、`/approvals/instances`、`/approvals/counts` | 审批中心、分页与数量                      |

保存允许不完整草稿，使用 revision 检测并发修改。保存已启用流程使其转为草稿，重新校验发布后才能发起新实例；已运行实例固定使用原发布版本。示例表单仅可保存草稿，不能用于真实发布。

设计器的“模拟预览”继续使用前端独立数据副本，不调用真实记录写入接口。真实执行通过记录提交与审批中心验证。

## MCP 应用审批工具

审批定义操作统一在 `AppTools`：`get_app_approval_capabilities`、`list_app_approval_flows`、`get_app_approval_flow`、
`save_app_approval_flow`、`validate_app_approval_flow`、`set_app_approval_flow_status`。能力目录返回实际表单字段、可引用子流程、节点配置、枚举、约束及由
`AppApprovalFlowModel` 生成的完整简单/网关示例。

工具沿用 REST 的 Flow 契约。`save_app_approval_flow` 省略 flowId 创建草稿；更新需 flowId 与最新 revision。校验返回
`{nodeId,message}[]`，发布/停用分别使用 status=2/3 并携带 revision。工具调用既有审批服务，不直接操作 Flow 表，也不增加另一套定义或发布模型。

通用人员能力位于独立的 `directory/DirectoryService`，审批编译、运行时和既有目录 REST 接口共享该服务；
`DirectoryTools.get_directory` 与 `resolve_members` 不需要 appId。目录提供真实启用成员、角色、组织和直属上级关系，只暴露选择所需字段；预览变量和发起人不授予任何签核身份。

MCP 入口将每次校验通过的 API Key 放入请求上下文，新工具显式校验应用范围与写入限制，并填充 `SessionContext` 的项目数据库和服务身份
`api_key:<keyId>`。只读 Key 可查询/校验，不能保存、发布或停用；不同请求复用 MCP 会话时仍以本次 HTTP
凭证为准。当前新增工具仅负责流程设计与发布，实际记录提交及签核继续使用真实个人会话。

针对性验证：

```bash
mvn test -pl flexmodel-server -am \
  -Dtest=AppApprovalToolsTest,ApprovalPolicyTest,ApprovalFlowModelTest \
  -Dquarkus.scheduler.enabled=false -Dquarkus.rabbitmq.devservices.enabled=false \
  -Dsurefire.failIfNoSpecifiedTests=false
```

`AppApprovalToolsTest` 使用真实 MCP HTTP 初始化、工具发现和工具调用，覆盖示例校验、草稿保存/读取/更新、发布/停用、revision
冲突、无效模型、通用目录、解析预览、项目范围和只读凭证。

## 启动与验证

启动时平台级模型由 `platform.fml` 统一导入 system schema，项目业务库仅承载用户自定义模型；`SchemaRegistry.registerSchema`
只负责注册主应用及分支的数据源。审批按当前模型初始化，不包含历史数据迁移、旧字段回退或旧处理器别名。

```bash
mvn test -pl flexmodel-server -am \
  -Dtest=EngineConfigTest,ApprovalPolicyTest,ApprovalFlowResourceTest,FlowEventPublisherTest,FlowDefinitionResourceTest \
  -Dquarkus.scheduler.enabled=false -Dsurefire.failIfNoSpecifiedTests=false
# 服务端事务、回滚事件与跨连接行锁；MySQL 8.0 使用本地 Testcontainers
mvn test -pl flexmodel-server -am \
  -Dtest=ServerSessionsTest -Dserver.sessions.mysql=true \
  -Dsurefire.failIfNoSpecifiedTests=false
cd flexmodel-apps-ui
npm test
npm run build
npm run test:approvals
```

`AppApprovalFlowResourceTest` 会启动隔离的 SQLite 后端，并运行 `flexmodel-apps-ui/e2e/approval-backend.mjs`
的真实浏览器闭环，需要先安装
Apps UI 依赖和 Playwright Chromium。该脚本使用独立的 5183 端口，测试登录凭证只通过子进程环境传递。截图保存在 Apps UI 的忽略目录
`artifacts/approvals-backend-*.png`。
