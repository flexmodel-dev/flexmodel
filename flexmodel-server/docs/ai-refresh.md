# AI 对话后的全局刷新

刷新逻辑集中在两个入口：

- `AiProvider` 收到现有 SSE `tool` 事件后标记本轮调用过工具；本轮结束时（含失败、停止），统一调用 `clearAppDataCache()` 并递增
  `refreshVersion`。
- `main.tsx` 的 `AppPage` 使用 `refreshVersion` 作为业务页面的 React key，重新挂载当前路由页面，让原有加载逻辑重新取数。AI
  Provider、侧栏和会话位于这个 key 之外，继续保留。

`AiComposer` 同时根据版本重置应用提及列表，避免引用旧应用列表。

不区分资源或工具的读写类型，不维护工具映射，不增加后端协议、缓存订阅或页面级刷新 Hook。多个工具调用合并为每轮一次刷新；未调用工具的纯对话不刷新。后端
SSE 继续使用 `token/reasoning/tool/done/error`。

全局刷新会重置业务页面内的筛选、弹窗和未保存编辑；AI 侧栏中未发送的输入保留。这里只覆盖当前浏览器内的 AI 对话，不提供外部
MCP、其他用户或标签页同步。连接中断前未收到 `tool` 的执行无法判断，本轮不会据此自动刷新。

验证：`npm run build` 与 `npx playwright test --config playwright.ai.config.ts`；浏览器用例检查工具调用完成或后续模型失败时只重新加载一次页面，并保留
AI 侧栏及消息草稿。
