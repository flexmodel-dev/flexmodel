# 记录讨论功能真实化设计

## Context

记录详情抽屉的「记录动态」侧栏目前是：流程 Tab 接真实审批数据，讨论 Tab 用前端内存 mock（`useRecordActivityMock` /
`recordActivityMock`），历史 Tab 仍为 mock。

本次只把 **讨论**换成真实接口，范围按已确认结论：

- 两级回复：顶层评论 + 对顶层评论的回复，回复的回复归入同一顶层线程。
- 权限：可访问该表单的登录成员可查看/评论；仅作者本人可删除（应用所有者、管理员也不能删除他人评论）。
- @提及：输入 @ 选择应用成员，提交被提及用户 id，被提及人收到站内通知。
- 刷新：侧栏挂载时每 3 秒轮询；侧栏收起或进入编辑态随组件卸载停止。
- 历史 Tab 继续 mock，不在本次范围。

## 一、后端设计

### 1. 数据表（`flexmodel-server/src/main/resources/platform.fml`）

新增平台表，放在其他 `f_app_*` 模型附近：

```fml
model f_app_record_comment {
  id: String @id,
  project_id: String @length("64"),
  form_name: String @length("64"),
  record_id: String @length("64"),
  parent_id?: String @length("64"),
  content: String @length("2000"),
  author_id: String @length("64"),
  author_name: String @length("128"),
  mentioned_user_ids?: JSON,
  created_at: DateTime,
  @index(name: "IDX_APP_COMMENT_RECORD", fields: [project_id, form_name, record_id, created_at]),
  @index(name: "IDX_APP_COMMENT_PARENT", fields: [project_id, parent_id]),
  @system,
  @comment("记录讨论评论")
}
```

约定：

- `parent_id` 为空表示顶层评论；回复存 **顶层评论 id**，服务端收到任意 parentId 时向上解析到根，保证最多两层。
- `author_name` 为创建时快照，避免每次列表请求加载目录；用户改名后旧评论保留旧名。
- `mentioned_user_ids` 存被提及用户 id 数组。
- 采用硬删除：删除顶层评论级联删除其回复；删除回复只删单条。列表接口不再过滤删除标记。

### 2. REST 接口

新增 `AppRecordCommentResource`，路径与现有记录接口对齐（`/api` 前缀由配置提供）：

| 方法   | 路径                                                                        | 说明                                                        |
|--------|-----------------------------------------------------------------------------|-------------------------------------------------------------|
| GET    | `/apps/{appId}/forms/{formName}/records/{recordId}/comments?page=1&size=20` | 顶层评论分页；回复随顶层返回                                |
| POST   | 同路径                                                                      | `{content, parentId?, mentionedUserIds?}`，201 返回单条评论 |
| DELETE | `.../comments/{commentId}`                                                  | 204，仅作者本人                                             |

GET 响应：

```json
{
  "list": [{
    "id": "c1",
    "parentId": null,
    "content": "……",
    "authorId": "admin",
    "authorName": "Admin",
    "createdAt": "2026-10-30T10:00:00",
    "canDelete": true,
    "replyCount": 2,
    "replies": [{ "id": "c2", "parentId": "c1", "content": "……", "authorId": "u2", "authorName": "李娜", "createdAt": "…", "canDelete": false }]
  }],
  "total": 42,
  "rootTotal": 12
}
```

- 排序：顶层 `created_at DESC, id DESC`；回复 `created_at ASC`。
- `total` 为全部评论数（顶层 + 回复），供 Tab 角标；`rootTotal` 供“加载更早”判断。
- 校验：内容 trim 后 1~2000 字；parentId 必须属于同一 app/form/record；提及 ids 最多 20、去重、必须是目录中 ACTIVE
  成员，作者本人从通知接收人剔除。
- 权限：必须个人登录会话（`SessionContext.isAuthenticatedUser()`），表单可访问，记录存在；删除额外要求作者本人。审批锁定期间允许评论。

### 3. 权限辅助（`apps/AppMenuService.java`）

新增两个 public 方法，复用现有菜单可见性逻辑：

- `canAccessForm(appId, formName)`：应用所有者直接放行；表单未被任何菜单绑定则放行；被菜单绑定时复用
  `visibleMenus/authorized` 判断当前用户是否能看到对应 FORM 菜单。先判断是否存在受限菜单，未受限时不会加载目录。
- `isOwnerOrAdmin(appId)`：`project.ownerId`、会话权限包含 `*`、或目录 `roleIds` 包含 `admin`。

### 4. 后端类

新增：

- `apps/AppRecordCommentResource.java`
- `apps/AppRecordCommentService.java`：鉴权、根评论归并、DTO 组装、提及校验、通知触发。
- `apps/AppRecordCommentRepository.java`（继承 `AbstractRepository`）：按 app/form/record 分页、查回复、删除、清理。
- `apps/dto/AppRecordCommentView.java`、`AppRecordCommentCreateRequest.java`、`AppRecordCommentPage.java`。

修改：

- `apps/AppRecordService.java`：新增 public `requireRecordExists(appId, formName, recordId)`
  ；删除记录后调用评论仓储清理该记录评论（尽力而为，失败仅告警）。
- `apps/AppFormService.java`：删除表单后按 `project_id + form_name` 清理评论。
- `project/ProjectPlatformDataCleaner.java`：`PLATFORM_MODELS` 加入 `AppRecordComment`。

评论写入用 `ProjectUnitOfWork.executePlatform(appId, ...)`，与通知 enqueue 处于同一平台事务。

### 5. @提及通知

补充回复通知：回复时读取具体目标评论的作者ID，通知该作者；`replyToId` 缺省时以请求的 `parentId` 为目标，
即使目标为二级回复也保持正确接收人。目标作者已停用或为当前作者时不通知；同时回复和提及同一人只发送一条回复通知，
其他被提及者发送提及通知。通知统一使用 `discussion` 分类，分别以 `discussion:{id}:reply` 和 `discussion:{id}:mention`
为事件键，
写入与评论相同的平台事务并通过既有 Outbox/SSE 投递，不补发历史回复。

`f_notification` 增加可选列：`form_name`、`record_id`、`comment_id`，用于通知跳转。

- `NotificationService` 新增 `commentMention(...)`：category `comment`，`event_key = comment:{commentId}:mention`，标题
  `{作者} 在「{表单名}」中提到了你`，正文为评论摘要（截断 500 字），接收人为提及用户去重后剔除作者。
- `NotificationRepository.dto` 增加新字段映射；`summary` 默认分类增加 `comment`。
- `NotificationService.detail`：`comment_id` 存在且评论仍存在、当前用户仍可访问该表单时 `canNavigate=true`。
- `NotificationResource`：list/read-all 分类白名单增加 `comment`；detail 仅在存在 `instanceId` 时加载审批详情。
- 前端 `AppNotification` 增加 `'comment'` 类型及 `formName/recordId/commentId`；通知中心增加“讨论”筛选，详情页提供“查看讨论”按钮，打开
  `AppRecordDrawer` 并定位到讨论 Tab。

## 二、前端设计

### 1. Service 与轮询 Hook

新增 `src/services/recordComments.ts`：

```ts
listRecordComments(appId, formName, recordId, page, size)
createRecordComment(appId, formName, recordId, {content, parentId?, mentionedUserIds?})
deleteRecordComment(appId, formName, recordId, commentId)
```

新增 `src/components/biz/useRecordComments.ts`：

- 首次加载 page=1，size=20；`loading/error` 只在首屏和失败时展示。
- 每 3 秒静默刷新：请求 `size = min(50, 20 * loadedPages)` 的 page=1，覆盖已加载窗口；`document.hidden` 时跳过。
- “加载更早”：请求下一页并追加，按 id 去重。
- `create` 成功后插入服务端返回项并刷新；失败抛出由 UI 保留草稿；`remove` 成功后刷新。
- 卸载清理定时器；请求带序号防止旧响应覆盖新状态。

### 2. 讨论 UI（`RecordActivityAside.tsx` + 新 `CommentComposer.tsx`）

`RecordActivityAside` props 改为 `{flow, appId, formName, recordId, history, initialTab?}`，内部调用 `useRecordComments`
；侧栏卸载即停止轮询。

- Tab 角标使用 `total`。
- 侧栏收起时，仅在总数大于零时，在抽屉右上角展开按钮旁展示“X条讨论”（含回复）；零条时只展示展开图标。初次打开记录读取一次总数，展开时由讨论面板同步最新总数；收起仍卸载侧栏并停止轮询，切换记录隔离计数与迟到响应。读取失败保留图标入口。
- 顶层评论：作者头像（按 `authorId` 从缓存成员目录读取 `avatar` 并传入 `UserAvatar`，复用附件鉴权加载，无头像或图片加载失败走姓名首字）、姓名、时间、正文；
  `@姓名` 文本高亮；操作区“回复”及本人的“更多 → 删除评论”。二级回复使用相同头像解析方式。
- 二级回复：缩进展示在顶层下，可继续回复（parentId 仍为顶层 id）。
- 回复交互：点击“回复”在对应评论下方展开独立输入框，使用“回复 xxx
  的讨论”占位提示，不显示独立目标横条；重复点击同一条的“回复”收起输入框，点击另一条则切换目标并自动聚焦。发送时带 `parentId`
  和具体目标 `replyToId`，失败保留草稿；顶部新讨论输入框保留独立草稿。
- 删除：仅本人评论的“更多”菜单中显示“删除评论”；顶层删除弹确认框并提示“会同时删除该讨论下的回复”。
- 发送失败保留草稿并提示；加载失败显示重试；空态沿用现有 `EmptyState`。

新增 `CommentComposer.tsx`：

- 复用 `getDirectory(appId)` 成员目录缓存和 `UserAvatar`。
- 检测光标前 `@关键词`，在输入框上方弹出成员候选（Popover/绝对定位，避免被侧栏滚动裁剪）；支持上下键、Enter 选择、Esc
  关闭，处理中文输入法 `isComposing`。
- 选中后插入 `@姓名 `，记录 `{id,name}`；提交时只发送正文中仍存在的提及 id，最多 20 个。
- Enter 发送、Shift+Enter 换行；提交中禁用发送。

### 3. 抽屉接线（`AppRecordDrawer.tsx`）

- 删除 mock 评论相关接线，改为向 `RecordActivityAside` 传 `appId/formName/recordId={activeRecordId}`。
- 历史 mock 收窄为 `useRecordHistoryMock`（原 `useRecordActivityMock` 只保留历史返回），`data/recordActivityMock.ts`
  删除评论部分并改名为 `recordHistoryMock.ts`。
- 新增可选 prop `initialAsideTab?: 'flow' | 'discussion' | 'history'`，供通知中心打开讨论；存在该值时：
  - `BizDrawer` 的 `asideDefaultOpen` 为 true；
  - `RecordActivityAside` 初始 Tab 使用该值，并把 `tabTouched` 初始化为 true，避免审批实例异步到达后抢回流程 Tab。

### 4. 通知中心（`pages/Notifications.tsx`）

- 分类筛选增加“讨论”。
- 通知详情当 `category === 'comment'` 且存在 `formName/recordId` 时显示“查看讨论”，打开 `AppRecordDrawer` 并传
  `initialAsideTab="discussion"`。
- 更新 `services/notifications.ts` 的 `AppNotification` 类型。

## 三、实施顺序

1. **后端表与基础接口**：platform.fml、DTO、Repository、Service、Resource、菜单权限方法、清理逻辑。
   验收：自动建表；顶层评论 CRUD；分页/排序/权限/清理通过资源测试。
2. **前端真实接入**：recordComments service、useRecordComments、RecordActivityAside 基础列表/发送/删除/轮询、AppRecordDrawer
   接线、历史 mock 收窄。
   验收：抽屉中真实读写评论；3 秒轮询；收起/编辑停止；失败保留草稿。
3. **两级回复**：parent 解析、replies 展示、回复目标条、级联删除。
   验收：回复与回复的回复归并到同一顶层；删除顶层级联。
4. **@提及与通知**：成员选择器、提及校验、通知表字段、NotificationService/Resource/Repository、通知中心筛选与“查看讨论”。
   验收：@ 插入与提交 ids；被提及人收到通知并可跳转记录讨论；自我提及/重复去重。
5. **测试与回归**：补齐后端资源测试、前端 e2e、全量编译。

## 四、验证

后端：

```bash
mvn -pl flexmodel-server -am test -Dtest=AppRecordCommentResourceTest,NotificationResourceTest -Dsurefire.failIfNoSpecifiedTests=false
mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'
```

前端：

```bash
cd flexmodel-apps-ui
npx tsc --noEmit -p tsconfig.json
npm run test:records
```

E2E 补充用例（`e2e/records.spec.ts` 的 route mock 中增加 comments/directory 接口）：

- 展开“记录动态”→ 发送顶层评论 → 断言列表与 Tab 计数。
- 回复顶层评论、回复的回复归并展示。
- @ 选择成员 → 断言请求体 `mentionedUserIds`。
- 删除评论确认与级联提示。
- 轮询拉到他人新评论。
- POST 失败保留草稿。
- 无权限/加载失败显示重试。

真实后端手工验证：创建评论、@ 另一个账号、对方通知中心可跳转到记录讨论；删除评论；审批锁定期间仍可评论。

## 五、风险与范围外

- 当前系统没有运行时记录级/表单数据权限；评论访问控制只能按“登录 + 菜单可见性/所有者/管理员”判断，未绑菜单的表单对所有登录用户放行，与现有记录接口一致。
- 菜单受限时 `canAccessForm` 会加载目录；评论轮询每 3 秒调用一次，规模大时可加 30 秒访问缓存。
- 顶层评论硬删除会连带删除他人回复，已通过确认框提示；若产品不接受，后续改软删除占位。
- 回复全量内嵌，超长讨论线程会使单页响应变大；后续可限制每线程回复数或分页。
- 评论删除不撤回已投递的通知；历史 Tab 仍为 mock，不记录评论事件。
- 本次不做评论编辑、附件、表情、敏感词、未读已读、滚动定位到具体评论；通知跳转只打开记录并选中讨论 Tab。
