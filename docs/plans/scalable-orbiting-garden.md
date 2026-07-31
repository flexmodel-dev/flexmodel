# Flexmodel Pages — 静态站点托管（Cloudflare Pages 风格）

> feat 编号取 **feat-012**（feat-011 已被 Vector 占用）。

## Context

用户希望给 Flexmodel 增加类似 Cloudflare Pages 的能力：托管前端静态页面（构建产物），通过子域名公开访问，支持部署历史与预览。项目已有可复用基础设施：

- **边缘函数**（`feat-010`）作为最接近的范本：`f_function` 实体 + `FunctionResource`/`FunctionService`/
  `FunctionRepository`+
  `FunctionFmRepository`（`AbstractRepository`/`Session` DSL）+ DTO + `@RequiresPermissions` 鉴权。
- **认证授权**：`AuthFilter`（`@PermitAll` 放行）/ `PermissionFilter`（`@RequiresPermissions` 注解）/ `SessionContext`
  （@RequestScoped）。
- **FML codegen**：`project.fml` 中声明 `model f_xxx` → 生成 `dev.flexmodel.codegen.entity.Xxx` 与 `System.xxx` 静态元模型；
  `replaceString` 去除 `f_` 前缀。
- **前端**：React + AntD v6 + Vite，`routes.tsx` 注册 projectRoutes，`src/services/*.ts` 封装 `api`（baseURL `/api`）。

用户决策与约束：

1. **每项目一个站点**（1:1），站点身份 = 项目身份，用 `projectId` 作为标识。创建项目时自动生成 `f_page_site` 记录和默认欢迎页。
2. 资源来源 = **上传 + Git 两者都要**（本期落地「直接上传」，Git 构建保留数据模型字段，构建器延后）。
3. URL 模型 = **子域名**（`{projectId}.example.com/` 访问 production，`preview.{projectId}.example.com/` 访问预览，
   `k7x8.{projectId}.example.com/` 短别名预览）。去掉 `/p/` 路径预览路由。
4. 本期范围 = **后端 + 基础 UI**。
5. **性能要求**：上传产物落本地磁盘文件， **不入库**；资源路径挂载给 nginx，nginx 直接读文件服务， **Java 不在读热路径**。

预期成果：创建项目时自动生成 Pages 站点配置和默认欢迎页 → 用户上传 zip 部署 → 获取 `{projectId}.example.com/` 与
`preview.{projectId}.example.com/` 预览 URL；公开访问由 nginx 读本地文件输出，带 SPA fallback / 正确 MIME / ETag。 回滚 =
切软链指针，零文件移动。

---

## 设计要点

### 服务架构（nginx-direct，性能优先）

单一本地文件树（由 Java 写、nginx 读）：

```
{pages-root}/
  myproject/                    # projectId（站点身份 = 项目身份）
    dep_a1b2c3/                 # 不可变部署目录
      index.html, assets/..., ...
    dep_d3e4f5/
      index.html, ...
    production -> dep_a1b2c3     # 相对软链（原子切换）
    preview     -> dep_d3e4f5
    k7x8        -> dep_d3e4f5     # 短预览别名
```

- **部署**：解包 → 写 `{root}/{projectId}/{deploymentId}/...`（仅本地 FS，不走 StorageBackend/DB）。
- **发布/切别名**：创建临时相对软链 → `Files.move(ATOMIC_MOVE)` 原子覆盖 `{root}/{projectId}/{alias}`。
- **回滚**：把 `production` 软链原子重指到旧 deploymentId，零拷贝。
- **DB** 仅存元数据：站点配置 + 当前部署状态镜像（`production_deployment_id`/`status`/`file_count`/`size_bytes`/
  `error_message`），服务真相以软链为准。 **资产字节不进 DB，部署历史不入库**。
- **公开访问全在 nginx**：Java 不参与读路径。dev/测试用 gated fallback handler 例外。

### alias 模型

- 每项目下：`production`（生产）、`preview`（最近一次预览）、若干短 id 别名（每次预览部署生成）。
- 别名按项目命名空间，免全局唯一。

### 子域名解析

- **根子域名** `{projectId}.example.com/`：nginx `server_name ~^(?<projectId>[^.]+)\.example\.com$`，
  `root {pages-root}/$projectId/production` + SPA 回退 `/index.html`。最短 URL，最常用。
- **嵌套子域名** `preview.{projectId}.example.com/` / `k7x8.{projectId}.example.com/`： nginx
  `server_name ~^(?<alias>[^.]+)\.(?<projectId>[^.]+)\.example\.com$`，
  `root {pages-root}/$projectId/$alias` + SPA 回退 `/index.html`。
- 自定义域名：nginx `map $host $pages_site { ... }`（Java 按 `f_page_site.custom_domains` 生成片段 + reload 信号）；MVP 提供
  map 生成器与 nginx reload 脚本，自动化校验（CNAME/TLS）延后。

### 项目创建时自动初始化

- `ProjectService.createProject()` 中调用 `PageService.initPageSite(projectId)`：
    1. 创建 `f_page_site` 记录（`project_id` = projectId，其余字段用默认值）
    2. 生成默认欢迎页 HTML → 写到 `{root}/{projectId}/{defaultDepId}/index.html`
    3. 创建 deployment 记录（`alias=production`，`environment=PRODUCTION`，`status=READY`）
    4. 切 `production` 软链指向 `defaultDepId`
- 用户访问 `{projectId}.example.com/` → 立即看到欢迎页，而非 404

### 鉴权与可见性

- 管理 API（`PageResource`，JAX-RS `/api/projects/{projectId}/page`）：`@RequiresPermissions("pages:view")`/
  `"pages:deploy"`，走 `AuthFilter`。
- 公开内容：完全在 nginx（非 JAX-RS，非 `AuthFilter` 范围），公开可读。MVP 所有 deployment 视为
  PUBLIC；PRIVATE/AUTHENTICATED/密码保护延后。
- 权限项 `pages:*` 需登记到项目权限体系（同步 `function:*` 模式；实现时检查 auth 包内权限种子/常量并补 `pages:view`/
  `pages:deploy`）。

### 复用而非新造

- 数据访问复用 `AbstractRepository.getProjectSession(projectId)` + `Session.dsl()` + `System.xxx` 元模型（同
  `FunctionFmRepository`）。
- 鉴权复用 `@RequiresPermissions` + `SessionContext`。
- UI 复用 `api` + `routes.tsx` + services 模式，新页面为单页面（非列表+详情）。
- 文件 IO 用 `java.nio.file.Files`（含 zip 解包、相对软链、原子 rename）。
- Windows 软链 fallback：`PageAliasManager` 在软链不可用时 fallback 到硬链（`Files.createLink`）或目录 copy。

---

## Phase 1 后端

### 1. 数据模型（`flexmodel-server/src/main/resources/project.fml` 追加）

合并站点配置与部署状态为一张表，每项目一条记录。部署历史不入库（真相在文件树软链），当前部署状态镜像到站点记录。

去掉 Phase 1 不用的字段：`framework`（仅提示）、`build_command`/`build_output_dir`（Git 预留）、`source_type`/`source_repo`/
`source_branch`（Git 预留）。后续需要时再追加。

```fml
model f_page_site {
  id : String @id @default(uuid()),
  project_id : String @comment("项目ID"),
  custom_domains : JSON @comment("自定义域名列表"),
  production_deployment_id? : String @comment("当前生产部署ID(UI镜像,真相=软链)"),
  status : PageDeploymentStatus @default("READY") @comment("当前部署状态"),
  file_count : Int @default(0) @comment("当前部署文件数"),
  size_bytes : Long @default(0) @comment("当前部署总大小(字节)"),
  error_message? : String @length("2000") @comment("部署错误信息"),
  created_by? : String, updated_by? : String,
  created_at? : DateTime @default(now()), updated_at? : DateTime @default(now()),
  @index(name: "UQ_PAGE_SITE_PROJECT", unique: true, fields: [project_id]),
  @system, @comment("Pages 站点(项目级,每项目自动生成,含当前部署状态)")
}

enum PageDeploymentStatus { READY, FAILED, @system }
```

codegen `replaceString` 去前缀 → 实体 `PageSite`，元模型 `System.pageSite`，枚举 `PageDeploymentStatus`。

> **设计说明**：部署历史不入库。每次部署生成一个不可变目录 `{root}/{projectId}/{deploymentId}/`，别名（`production`/
> `preview`/短 ID）通过软链指向部署目录。DB 只镜像当前 production 的 deploymentId 和统计信息，真相以软链为准。回滚 =
> 重指软链 + 更新 DB 镜像。

### 2. `pages/` 特性包（`flexmodel-server/src/main/java/dev/flexmodel/pages/`）

仿 `functions/` 结构：

| 文件                                                       | 职责                                                                                                                                                                                                    |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PageResource.java`                                        | `@Path("/projects/{projectId}/page")`（单数）：获取配置 + 更新配置 + 上传部署 + 切生产别名。`@RequiresPermissions`                                                                                      |
| `PageService.java`                                         | 站点配置读写、部署编排、`initPageSite()`（项目创建时调用）、默认欢迎页生成。调 `PageDeployer` → 切软链 → 更新 DB 镜像（`production_deployment_id`/`status`/`file_count`/`size_bytes`）。失败记 `FAILED` |
| `PageSiteRepository.java` + `PageSiteFmRepository.java`    | `AbstractRepository` + `Session.dsl()`（同 `FunctionFmRepository`）                                                                                                                                     |
| `PageDeployer.java`                                        | 接收 `InputStream`(zip) → 解包 → 写 `{root}/{projectId}/{deploymentId}/`（`Files`）→ 统计 `file_count`/`size_bytes`；过滤 `..` 路径与绝对路径条目（防穿越）→ 返回部署信息                               |
| `PageAliasManager.java`                                    | 创建/原子切换相对软链 `{root}/{projectId}/{alias}→{deploymentId}`；`Files.createSymbolicLink` + 临时名 + `Files.move(ATOMIC_MOVE)`；Windows fallback（硬链/copy）                                       |
| `PageDevHandler.java`                                      | **仅 dev/测试**：`@Observes StartupEvent, Router`，`flexmodel.pages.dev-fallback=true` 时注册路由复刻 nginx 的解析+SPA 回退（读同一文件树），供 `quarkus:dev` 无 nginx 时验证。生产关闭                 |
| `PageException.java`                                       | 继承 `BusinessException`（同 `FunctionException`）                                                                                                                                                      |
| `dto/PageSiteResponse.java` / `PageSiteUpdateRequest.java` | Lombok `@Data @Builder` + `static from()`，仿 `FunctionResponse`                                                                                                                                        |
| `config/PagesConfig.java`                                  | `@ConfigMapping(prefix="flexmodel.pages")`：`root-path`（默认 `./pages`）、`base-domain`（默认 `pages.local`）、`dev-fallback`（默认 `false`）                                                          |

**PageResource 端点**（4 个，简化版）：

```
GET  /projects/{projectId}/page              → 获取 Pages 配置
PUT  /projects/{projectId}/page              → 更新配置（custom_domains 等）
POST /projects/{projectId}/page/upload  → multipart 上传 zip 部署
PUT  /projects/{projectId}/page/production   → 切生产别名（指定 deploymentId）
```

文件上传端点：`POST /projects/{projectId}/page/upload`，`@Consumes(MediaType.MULTIPART_FORM_DATA)`，
`@FormParam("file") InputStream file`（zip）+ 可选 `@FormParam("environment")`。服务端流式解包，避免大 zip 占内存。

### 3. 项目创建时自动初始化

在 `ProjectService.createProject()` 中追加调用 `PageService.initPageSite(projectId)`：

1. 创建 `f_page_site` 记录（`project_id` = projectId，其余字段用默认值）
2. 生成默认欢迎页 HTML → 写到 `{root}/{projectId}/{defaultDepId}/index.html`
3. 切 `production` 软链指向 `defaultDepId`
4. 更新 DB 镜像：`production_deployment_id = defaultDepId`，`status = READY`，`file_count = 1`

### 4. 公开服务（nginx-direct，配置由后端提供）

技术真相： **nginx 直接服务**。Java 端只生成 nginx 所需配置片段（子域/自定义域）与文件树 + 软链。dev/测试由
`PageDevHandler` 复刻。

**根子域名**（production，最短 URL）：

```nginx
server {
    listen 80;
    server_name ~^(?<projectId>[^.]+)\.pages\.local$;   # 生产替换 base-domain
    root /data/pages/$projectId/production;
    location / {
        try_files $uri $uri/ /index.html =404;
        # MIME 由 nginx mime.types + 显式补充
        types { application/javascript js mjs; text/css css; text/html html; ... }
    }
    # 资产可加长缓存（hash 文件名）；HTML 不缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|woff2?|ttf|ico)$ {
        expires 30d; add_header Cache-Control "public, immutable";
    }
}
```

**嵌套子域名**（preview + 短别名）：

```nginx
server {
    listen 80;
    server_name ~^(?<alias>[^.]+)\.(?<projectId>[^.]+)\.pages\.local$;
    root /data/pages/$projectId/$alias;
    location / {
        try_files $uri $uri/ /index.html =404;
        types { ... }
    }
}
```

自定义域名（Java 按 `f_page_site.custom_domains` 生成可 include 片段 + `nginx -s reload`）：

```nginx
map $host $pages_site { default ""; app.example.com myproject; blog.example.com myproject; }
server {
    server_name app.example.com blog.example.com;
    root /data/pages/$pages_site/production;
    location / { try_files $uri $uri/ /index.html =404; }
}
```

> path `..` 穿越：`projectId`/`alias` 为 `[^/]+` 无斜杠；软链已锚定 deployment 目录，无法越出 root。上传时 `PageDeployer`
> 额外拒绝含 `..`/绝对路径的 zip 条目。

### 5. 配置（`application.properties` 追加）

```properties
flexmodel.pages.root-path=${FLEXMODEL_PAGES_ROOT:./pages}
flexmodel.pages.base-domain=${FLEXMODEL_PAGES_BASE_DOMAIN:pages.local}
flexmodel.pages.dev-fallback=false   # 仅 quarkus:dev/测试设 true
```

---

## Phase 1 前端（基础 UI）

`flexmodel-ui/src/`：

| 文件                          | 职责                                                                                                                                                                                                               |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `services/pages.ts`           | 仿 `services/function.ts`：`getPageSite`/`updatePageSite`/`deployUpload`（`FormData` 上传 zip）/`setProductionDeployment`。复用 `api`                                                                              |
| `pages/Pages/index.tsx`       | **单页面**（非列表+详情）：站点配置区 + 预览 URL 显示（`{projectId}.pages.local/`）+ 上传 zip 按钮（AntD `Upload` before-upload 拦截走自定义请求）+ 当前部署信息（status/file_count/size_bytes）+ 「设为生产」操作 |
| `routes.tsx`                  | projectRoutes 追加：`/project/:projectId/page`（`<Pages/>`，icon `GlobalOutlined`，`translationKey: "pages.title"`）                                                                                               |
| `locales/zh.json` / `en.json` | 追加 `pages.*` 翻译                                                                                                                                                                                                |

---

## 部署配置

docker-compose：给 `flexmodel-server` 与 `nginx` 共享挂载 pages 卷（server 写、nginx 读）：

```yaml
volumes:
  pages_data:
services:
  flexmodel-server:
    volumes: [ pages_data:/data/pages ]
    environment: [ FLEXMODEL_PAGES_ROOT=/data/pages ]
  nginx:
    volumes: [ pages_data:/data/pages:ro ]
```

`deploy/docker-compose/nginx/conf.d/default.conf` 追加根子域名与嵌套子域名两个 server block。自定义域名 map 片段由
`PageService` 写入 nginx include 目录并提供 `nginx -s reload` 触发（MVP 可手动 reload，自动化延后）。

---

## 延后项（明确不在本期）

- **Git 连接 + 真构建器**（拉仓库→跑 `build_command`→取 `build_output_dir`）。后续需要时追加 `source_type`/`source_repo`/
  `source_branch`/`build_command`/`build_output_dir` 字段到 `f_page_site`，接 worker（可复用 functions-runtime 沙箱或新建容器）。
- **Next.js SSR 支持**：当前只支持 STATIC 类型（纯静态文件）。SSR 需引入 Node.js Runtime 作为计算层，nginx
  做路由分发（静态直挂 + SSR 反代）。后续需要时追加 `site_type` 字段（`STATIC`/`SSR`）到 `f_page_site`。
- 子域名/自定义域名的 **TLS 证书**与 **CNAME 校验**自动化。
- PRIVATE/AUTHENTICATED 可见性、密码保护、访问日志。
- **S3 后端**支持（S3 无软链 → 需 Java 路由模式，与本地直挂 nginx 互斥；列为后续可选路径）。
- 部署 diff / 预览对比、rollback UI、自定义域名绑定预览部署。

---

## 验证

1. **全模块编译**（含 codegen 生成新实体）：`mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'`
2. **引擎测试**：`mvn test -pl flexmodel-engine`
3. **服务端测试**（新建 `PageResourceTest` + `PageDevHandlerTest`，QuarkusTest + RestAssured，
   `flexmodel.pages.dev-fallback=true` 以便无 nginx 验证解析/SPA 回退）：
    - 创建项目 → 自动生成 `f_page_site` 记录 + 默认欢迎页 → 断言 DB `status=READY`、磁盘存在
      `{root}/{projectId}/{defaultDepId}/index.html`、`{root}/{projectId}/production` 为指向该目录的 **软链**。
    - `multipart` 上传含 `index.html`+`assets/app.js` 的 zip → 断言 DB `status=READY`、`file_count=2`、磁盘存在
      `{root}/{projectId}/{deploymentId}/index.html`、`{root}/{projectId}/production` 软链指向新部署。
    - 切 `production` 到新部署 → 软链原子换向，旧部署文件仍在（可回滚）。
    - 经 dev-fallback：`GET /{projectId}/production/index.html` → 200 + `text/html`；
      `GET /{projectId}/production/assets/app.js` → 200 + `application/javascript`；
      `GET /{projectId}/production/missing.js` → 404；`GET /{projectId}/production/users/123` → 200（SPA 回退
      `index.html`）；`GET /{projectId}/production/`（尾斜杠）→ 200。
    - 路径穿越：`GET /{projectId}/production/../../etc/passwd` → 404（不越 root）。 运行：
      `mvn test -pl flexmodel-server -am -Dtest=PageResourceTest,PageDevHandlerTest`
4. **前端**：`cd flexmodel-ui && npm run build`（tsc -b + vite build，零 TS 错误）。
5. **手动 e2e**（可选，记录入 progress）：`./mvnw quarkus:dev` + 起 nginx 挂载 `./pages` → 创建项目 → 浏览器开
   `http://{projectId}.pages.local/`（经 nginx）或 dev-fallback 模式验证。

## 关键文件清单

- 追加：`flexmodel-server/src/main/resources/project.fml`
- 新建：`flexmodel-server/src/main/java/dev/flexmodel/pages/**`（约 10 个 Java 文件，见上表）
- 修改：`flexmodel-server/src/main/java/dev/flexmodel/project/ProjectService.java`（`createProject()` 中调用
  `PageService.initPageSite()`）
- 追加：`flexmodel-server/src/main/resources/application.properties`
- 新建测试：`flexmodel-server/src/test/java/dev/flexmodel/pages/PageResourceTest.java`、`PageDevHandlerTest.java`
- 新建：`flexmodel-ui/src/services/pages.ts`、`flexmodel-ui/src/pages/Pages/index.tsx`
- 追加：`flexmodel-ui/src/routes.tsx`、`flexmodel-ui/src/locales/zh.json`、`flexmodel-ui/src/locales/en.json`
- 部署配置：`deploy/docker-compose/docker-compose.yml`（pages 卷 + 挂载）、`deploy/docker-compose/nginx/conf.d/default.conf`
  （追加根子域名与嵌套子域名 server block）

## 收尾（遵循 AGENTS.md）

- `feature_list.json` 追加 `feat-012 Pages — 静态站点托管`，status `in-progress`（端到端 + Git 构建补齐后转 `done`
  ），evidence 记录编译/测试/UI 构建结果。
- `progress.md` 记录本期产出、已落地（上传部署 + nginx-direct 服务 + 子域名访问 + 软链回滚 + 项目创建时自动初始化 +
  单表模型）、延后项（Git 构建器/Next.js SSR/自定义域 TLS/S3 后端/部署历史入库）。
- 提交：`feat(pages): 实现静态站点托管（本地上传 + nginx 直挂 + 软链别名 + 项目自动初始化）`。
