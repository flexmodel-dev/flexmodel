# Apps 实时站内通知

Apps 的铃铛与通知中心使用真实个人收件箱。审批任务进入 PENDING
时通知当前执行人，依次审批仅在轮到用户时通知；根实例通过、拒绝、终止时通知发起人。子流程不重复发送结果通知。读取消息不会完成待办或授予业务记录访问权限。

## 流程设计

现有流程设计器及 `get_app_approval_capabilities` 支持 `notification` 节点：

```json
{
  "kind": "notification",
  "rules": [{"id": "initiator", "source": "initiator", "values": [], "scope": "self", "includeChildren": false}],
  "titleTemplate": "记录更新",
  "contentTemplate": "记录 {{record.name}} 已处理"
}
```

接收人规则复用通用 DirectoryService，支持用户、角色、组织、发起人、上游审批人及变量，稳定去重且最多 1000 人。标题/正文为纯文本（200/5000
字），只插入变量路径，空值替换为空串，不执行 JavaScript。发布校验变量作用域及模板语法；渲染为空、超长或人员解析失败会记录节点失败原因并继续流程；空接收人记为
skipped。模拟器仅预览。

## 持久化与恢复

项目事务写入 `notification.deliver` Outbox，独立 ordering key 不阻塞业务流转。后台按首次执行时确定的收件人与内容投递到系统库：

- `f_notification`：每用户一条消息，唯一约束为 project_id/event_key/recipient_id；保留收到时的应用名称和内容。
- `f_notification_stream`：用户事件版本与未读数。
- `f_notification_event`：有序事件日志，也是实时分发的持久化待投递记录。

系统库写入独立于源项目事务。每位收件人的写入锁定其现有 f_user 行，在同一事务中更新消息、未读数和事件游标，避免并发提交导致断线游标跳过消息。跨库投递重试由唯一约束去重，不重置
read_at。内部 is_read 索引用列与 read_at 同事务维护，以避免当前查询 DSL 的 NULL 等值比较。

源投递复用 ReliableMessages 的退避与 8
次失败上限；管理员可在审批详情重试失败通知，即使审批已结束。通知投递失败和业务流程恢复分别展示。实时分发失败保留未发布事件继续尝试；收件箱记录不受浏览器连接影响。当前不回填历史消息、不自动清理消息和事件。

## HTTP 与 SSE

所有个人接口需要启用的真实个人账号，不接受 API Key，不接受客户端指定 recipientId；其他用户的通知 ID 返回 404。

| 接口                                                               | 说明                                                                          |
|--------------------------------------------------------------------|-------------------------------------------------------------------------------|
| GET /api/notifications                                             | status=unread/all、category=approval/workflow、appId、page、size，默认每页 30 |
| GET /api/notifications/summary                                     | 字符串 version、unread、byCategory、apps                                      |
| GET /api/notifications/{id}                                        | 正文、关联审批及当前可用操作                                                  |
| PUT /api/notifications/{id}/read                                   | 幂等已读                                                                      |
| POST /api/notifications/read-all                                   | JSON appId/category，作用于该范围所有消息                                     |
| GET /api/notifications/stream                                      | Bearer 鉴权的 SSE，Last-Event-ID 为用户事件版本                               |
| POST /api/apps/{appId}/approval-instances/{id}/retry-notifications | 流程管理员重试失败通知                                                        |

SSE 包含 snapshot、ready、notification.created、notification.read、notification.read_all，事件版本始终为十进制字符串。首次连接只返回快照，重连补发最多
1000 条并标记 replay；超过上限或游标无效时以快照重新同步。15 秒心跳，发送超时关闭慢连接，令牌到期关闭连接；前端 1～30
秒随机退避重连，401/403 停止。补发事件只同步状态，不再次弹提醒。

前端没有定时拉取通知列表。REST 只在首次加载、筛选/分页、收到事件、用户主动刷新和打开铃铛时查询。Web Locks
按用户选出连接持有页，BroadcastChannel 同步事件和焦点；有前台页时只在该页显示可点击提示，否则由连接持有页显示一次系统通知。退出账号取消连接并清理内存状态。

## 部署

单实例默认使用本地分发，无需 RabbitMQ。多实例必须开启 `flexmodel.events.rabbitmq.enabled=true`，并配置现有
`mp.messaging.connector.smallrye-rabbitmq.host/port/username/password/virtual-host`。通知使用独立的持久 fanout 交换机
`flexmodel.notifications`，每实例独立排他、自动删除队列；连接恢复时重建队列并断开本机 SSE，促使客户端从数据库游标补齐。发布确认后才标记事件已发布。

前端使用 fetch 流读取 SSE，请勿将令牌放入 URL。HTTPS 部署后，用户主动点击“开启浏览器提醒”申请权限；被拒绝时只显示指引。当前系统提醒以桌面
Chrome/Edge 为验收环境；不支持时仍保留站内收件箱。不提供页面全部关闭后的 Web Push。

反向代理示例：

```nginx
location /api/notifications/stream {
    proxy_pass http://flexmodel_backend;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 60s;
}
```

服务端同时返回 Cache-Control: no-cache, no-transform 和 X-Accel-Buffering: no。

## 验证

```bash
mvn test -pl flexmodel-server -am \
  -Dtest=NotificationResourceTest,NotificationRelayTest,ApprovalFlowResourceTest,AppApprovalToolsTest,ApprovalPolicyTest,ApprovalFlowModelTest \
  -Dquarkus.scheduler.enabled=false -Dquarkus.rabbitmq.devservices.enabled=false \
  -Dsurefire.failIfNoSpecifiedTests=false
cd flexmodel-apps-ui
npm test
npm run build
```

NotificationResourceTest 自动运行隔离后端上的真实 Playwright 浏览器闭环，需安装 Apps 依赖与 Chromium，测试前端占用 5184
端口。NotificationRelayTest 使用本地 Testcontainers RabbitMQ 验证两个独立分发实例及 broker 故障恢复。浏览器截图在忽略目录
artifacts/notifications-light.png、notifications-dark.png。

无界面 Chromium 的系统通知使用 Notification API 测试桩，验证授权分支、调用去重和点击导航；不声称自动观察到了操作系统桌面弹窗。实际桌面提醒显示需在
HTTPS 环境下用 Chrome/Edge 授权验收。
