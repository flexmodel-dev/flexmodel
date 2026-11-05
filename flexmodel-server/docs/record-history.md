# Apps 记录操作历史

Apps 记录详情抽屉的“历史”侧栏展示真实操作时间线。历史采用轻量日志方案：
业务操作成功提交后尽力写入，历史失败只记录结构化日志，不影响业务结果；因此允许漏记，不提供恢复能力。

## 事件类型

| 事件                                                        | 说明                                         |
|-------------------------------------------------------------|----------------------------------------------|
| RECORD_CREATED                                              | 创建记录，包含 Excel 导入创建                |
| RECORD_UPDATED                                              | 业务字段发生实际变化（仅系统时间变化不记录） |
| RECORD_DELETED                                              | 删除记录                                     |
| APPROVAL_SUBMITTED                                          | 成功发起审批                                 |
| APPROVAL_TASK_APPROVED / APPROVAL_TASK_REJECTED             | 某个执行人签核当前任务，与流程最终结果区分   |
| APPROVAL_APPROVED / APPROVAL_REJECTED / APPROVAL_TERMINATED | 整个审批的最终状态                           |

来源分为 `APPS`（手动操作）、`IMPORT`（Excel 导入，关联 importId）、`APPROVAL`（提交、签核与自动数据修改）。
操作人分为 USER（真实登录用户）与 SYSTEM（流程自动执行）。操作上下文全部由服务端创建，不接受客户端提交的操作人、时间或事件类型。

## 持久化

平台库两张系统表（`platform.fml` 定义，随应用删除由 `ProjectPlatformDataCleaner` 清理）：

- `f_app_record_history`：事件主表，保存表单/记录/操作人快照、UTC 的
  occurred_at/recorded_at、changes（字段差异）、context（主子表、导入、审批扩展信息）。唯一索引 `project_id + event_key`，
  `event_key` 为 SHA-256 摘要去重键，重复写入视为已记录。
- `f_app_record_history_target`：时间线索引，SELF（记录自身）与 PARENT（主记录，用于子表事件汇总）两类目标。查询先按目标索引游标分页，再批量读取事件。

写入时机：业务事务提交后（afterCommit）在独立平台事务中写入事件与全部目标索引；异常在 `AppRecordHistoryRecorder`
内部捕获，不向已成功的业务请求传播。导入按批（200/批）采集，整包回滚不产生历史。

## 采集规则

- 修改差异来自实际保存前后的记录值：SQL 数据源使用行锁事务（复用 `ProjectUnitOfWork.lock`）读取前值，保证并发下前值不被污染；非
  SQL 数据源不强制事务，历史能力返回不支持。
- 差异计算区分 `null`/空串/`0`/`false`，数值按语义比较（`1` 与 `1.0` 不产生伪变化），标签/多选无序比较，JSON
  对象键排序后比较；展示值保存操作时快照（选项标签、引用记录名称、操作人姓名），后续改名不覆盖。
- 主子表只汇总父表单布局中声明的 SUBFORM 关系；子记录事件在主记录时间线显示为“新增/修改/删除了子记录”，关联从 A 改为 B 时
  A 显示移出、B 显示移入。
- 附件以记录字段保存成功为准，只记录文件 ID/名称差异，不保存内容或下载链接。
- 删除表单时清理对应历史；应用删除随项目清理。

## HTTP 接口

| 接口                                                                          | 说明                                                                               |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| GET /api/apps/{appId}/forms/{formName}/records/{recordId}/histories           | `size`（默认 20，最大 50）、不透明 `cursor`；按 occurred_at DESC, id DESC 稳定排序 |
| GET /api/apps/{appId}/forms/{formName}/records/{recordId}/histories/{eventId} | 完整变更详情，先校验事件属于当前记录                                               |

- 列表返回 `supported`（内置表单与不支持的数据源为 false，前端显示明确空态）、`list`、`nextCursor`、`hasMore`；预览最多 3
  个可见字段变化，不返回全量总数。
- 权限复用登录态与 `canAccessForm`：每次查询限制 project_id，事件必须属于当前记录；返回值按当前布局做可见字段投影，已删除或未布局字段不泄露名称与值；审批意见沿用审批详情访问判断。
- 时间统一 UTC 传输（带 `Z` 的 ISO 字符串），前端按浏览器本地时区分组展示。

不提供客户端创建、修改、删除历史或恢复的接口；本期数据仅用于解释操作，不能作为恢复依据。

## 前端接入

服务层 `src/services/recordHistory.ts` 与 `useRecordHistory` Hook：

- 历史类型定义在服务层；已删除 `recordHistoryMock` 与 `useRecordHistoryMock`。
- 首次打开“历史”加载，切换记录清空状态；请求带序号隔离迟到响应。
- 保存记录、子表新增/编辑/删除、提交审批、签核成功后通过信号触发首屏静默刷新；页面获得焦点时同样刷新首屏，不使用 SSE 或轮询。
- 支持加载中、失败重试、空态（“暂无操作历史。历史功能启用后的操作会记录在这里。”）与加载更多（失败保留已加载内容）；长变更先展示摘要，点击后读取详情，按纯文本渲染。
