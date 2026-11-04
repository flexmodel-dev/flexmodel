# AI 写操作后的页面同步

AI 对话复用现有 `POST /api/ai/conversations/{id}/messages` SSE。在共享工具执行完成后，服务端按工具的真实返回结果产生
`change` 事件；不依赖模型回答文案，也不使用当前页面的 appId 推测目标。

```json
{"type":"change","change":{"appId":"crm","resource":"form","formName":"orders","entityId":null}}
```

资源类型为 `app`、`form`、`navigation`、`workbench`、`record`、`approval`。表单的字段、布局和视图均归属 `form`
。事件仅包含失效范围，不下发业务记录和完整工具参数。共享工具原有的登录用户与应用所有者/管理员鉴权保持生效。

## 后端

`AiToolChanges` 显式登记现有写工具与资源类型；只读工具不产生事件。工具执行框架的失败标记，以及共享工具返回的 `Error:`
文本，均不能当作写入成功。创建应用使用服务端实际返回的应用 ID；表单与其他资源使用实际工具参数和返回值。

示例记录按条提交，第 N 条失败时前 N-1 条可能已保存，因此 N > 1 的批量失败仍产生 `record` 变更。`change`
在工具执行后即时下发，后续模型失败不会撤回已成功的业务写入。

新增写工具时须补充 `AiToolChanges` 的范围映射。此阶段尚未引入服务层统一事件发布或常驻应用变更流，因此不覆盖外部
MCP、其他浏览器或其他用户的变更。

## 前端

`appDataCache` 为缓存登记应用、资源和可选表单范围。收到变更后立即失效匹配的缓存，最长每 350ms 合并通知一次；已挂载组件通过
`useAppDataRevision` 订阅自身关心的范围并后台取数。记录查询和工作台组件数据使用已有的 reloadKey/refreshKey 机制。

应用管理、应用启动器、应用资料与主题、导航、表单管理与设计、记录列表、工作台和审批流程列表均接入此订阅。应用内的列表、菜单和工作台不因后台刷新而卸载；筛选、路由、AI
会话和下一条消息草稿保留。

记录弹窗和配置编辑期间延后替换表单或工作台配置，结束编辑后同步最新版本。表单设计器有未保存修改时显示待同步提示。缓存用失效版本判断过期请求，防止旧响应返回后覆盖新数据；本地
CRUD 派生 Promise 缓存的行为仍可正常完成，不产生循环等待。

对话成功、失败、主动停止或意外断流时，前端均核对已收到的变更并立即通知页面；没有 `done`/`error`
的意外断流显示错误，避免一直停留在生成中。此机制只能核对浏览器已经收到的变更：若连接在某次写操作执行期间中断，尚未收到的写入不能保证即时同步，可重新进入页面核对；完整覆盖需后续独立变更流与服务层发布能力。

## 验证

```bash
mvn test -pl 'flexmodel-server,!flexmodel-engine/flexmodel-maven-plugin' -am \
  -Dtest=AiToolChangesTest,AppAiModelFlowTest,AppAiResourceTest,AppToolsTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dquarkus.scheduler.enabled=false
cd flexmodel-apps-ui
npm run build
npm test
npx playwright test --config playwright.ai.config.ts
```

自动化覆盖成功写入与失败写入、只读查询、部分记录成功、后续模型失败、流式变更解析、取消与断流、缓存过期响应竞态，以及浏览器中的应用列表、导航、表单、记录、工作台刷新与记录草稿保护。
