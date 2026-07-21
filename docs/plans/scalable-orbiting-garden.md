# Flexmodel Pages — 静态站点托管（Cloudflare Pages 风格）

> **本次交付物**：将本设计文档落盘到仓库 `docs/plans/pages-static-hosting.md`（与现有
> `docs/plans/tingly-gliding-parasol.md` 同目录、同风格的设计方案文件）。feat 编号取 **feat-012**（feat-011 已被 Vector
> 占用）。不进入代码实现。

## Context

用户希望给 Flexmodel 增加类似 Cloudflare Pages 的能力：托管前端静态页面（构建产物），通过部署别名/子域名/自定义域名公开访问，支持部署历史与预览。项目已有可复用基础设施：

- **云函数**（`feat-010`）作为最接近的范本：`f_function` 实体 + `FunctionResource`/`FunctionService`/`FunctionRepository`+
  `FunctionFmRepository`（`AbstractRepository`/`Session` DSL）+ DTO + `@RequiresPermissions` 鉴权。
- **认证授权**：`AuthFilter`（`@PermitAll` 放行）/ `PermissionFilter`（`@RequiresPermissions` 注解）/ `SessionContext`
  （@RequestScoped）。
- **FML codegen**：`project.fml` 中声明 `model f_xxx` → 生成 `dev.flexmodel.codegen.entity.Xxx` 与 `System.xxx` 静态元模型；
  `replaceString` 去除 `f_` 前缀。
- **前端**：React + AntD v6 + Vite，`routes.tsx` 注册 projectRoutes，`src/services/*.ts` 封装 `api`（baseURL `/api`）。

用户决策与约束：

1. 资源来源 = **上传 + Git 两者都要**（本期落地「直接上传」，Git 构建保留数据模型字段，构建器延后）。
2. URL 模型 = **子域名 + 路径预览**（`*.pages.<base>` 与 `/p/{site}/{alias}/...` 双轨，单一 Host+Path 规则）。
3. 本期范围 = **后端 + 基础 UI**。
4. **性能要求（本次更新核心）**：上传产物落本地磁盘文件， **不入库**；资源路径挂载给 nginx，nginx 直接读文件服务， **Java
   不在读热路径**。

预期成果：用户在项目内创建 Pages 站点 → 上传 zip 部署 → 获取 `/p/{site}/production/` 与 `/p/{site}/{alias}/` 预览
URL；public/直接由 nginx 读本地文件输出，带 SPA fallback / 正确 MIME / ETag。回滚 = 切软链指针，零文件移动。

---

## 设计要点

### 服务架构（nginx-direct，性能优先）

单一本地文件树（由 Java 写、nginx 读）：

```
{pages-root}/
  foo/                          # 站点 slug
    dep_a1b2c3/                 # 不可变部署目录
      index.html, assets/..., ...
    dep_d3e4f5/
      index.html, ...
    production -> dep_a1b2c3     # 相对软链（原子切换）
    preview     -> dep_d3e4f5
    k7x8        -> dep_d3e4f5     # 短预览别名
```

- **部署**：解包 → 写 `{root}/{site}/{deploymentId}/...`（仅本地 FS，不走 StorageBackend/DB）。
- **发布/切别名**：创建临时相对软链 → `Files.move(ATOMIC_MOVE)` 原子覆盖 `{root}/{site}/{alias}`。
- **回滚**：把 `production` 软链原子重指到旧 deploymentId，零拷贝。
- **DB** 仅存元数据：站点配置、部署记录（含 `status`/`file_count`/`size_bytes`/错误信息）、`production_deployment_id`（UI
  展示用镜像，服务真相以软链为准）。 **资产字节不进 DB**。
- **公开访问全在 nginx**：Java 不参与读路径。dev/测试用 gated fallback handler 例外。

### alias 模型

- 每站点下：`production`（生产）、`preview`（最近一次预览）、若干短 id 别名（每次预览部署生成）。
- 别名按站点命名空间，免全局唯一。DB 镜像唯一约束 `(site_name, alias)`。

### 子域名 / 路径双轨解析

- 路径预览 `GET /p/{site}/{alias}/*`：nginx 正则 `location` 切出 `site/alias/subpath`，`root {pages-root}` + `try_files`
  重构路径 + SPA 回退到该站点 alias 的 `index.html`。
- 子域名 `*.pages.<base>`：nginx `~^(?<site>[^.]+)\.pages\.<base>$`，`root {pages-root}/$site/production` + SPA 回退
  `/index.html`。
- 自定义域名：nginx `map $host $pages_site { ... }`（Java 按 `f_page_site.custom_domains` 生成片段 + reload 信号）；MVP 提供
  map 生成器与 nginx reload 脚本，自动化校验（CNAME/TLS）延后。

### 鉴权与可见性

- 管理 API（`PageResource`，JAX-RS `/api/projects/{projectId}/pages`）：`@RequiresPermissions("pages:view")`/
  `"pages:deploy"`，走 `AuthFilter`。
- 公开内容：完全在 nginx（非 JAX-RS，非 `AuthFilter` 范围），公开可读。MVP 所有 deployment 视为
  PUBLIC；PRIVATE/AUTHENTICATED/密码保护延后。
- 权限项 `pages:*` 需登记到项目权限体系（同步 `function:*` 模式；实现时检查 auth 包内权限种子/常量并补 `pages:view`/
  `pages:deploy`）。

### 复用而非新造

- 数据访问复用 `AbstractRepository.getProjectSession(projectId)` + `Session.dsl()` + `System.xxx` 元模型（同
  `FunctionFmRepository`）。
- 鉴权复用 `@RequiresPermissions` + `SessionContext`。
- UI 复用 `api` + `routes.tsx` + services 模式，新页面照搬 `Functions` 列表/详情结构。
- 文件 IO 用 `java.nio.file.Files`（含 zip 解包、相对软链、原子 rename）。

---

## Phase 1 后端

### 1. 数据模型（`flexmodel-server/src/main/resources/project.fml` 追加）

```fml
model f_page_site {
  id : String @id @default(uuid()),
  name : String @comment("站点 slug，URL 用"),
  framework? : String @length("50") @comment("框架标识(vite/next/...)，仅提示"),
  build_command? : String @length("255") @comment("构建命令(Git 模式预留)"),
  build_output_dir? : String @length("255") @default("dist") @comment("构建产物目录"),
  source_type : PageSource @default("UPLOAD") @comment("资源来源"),
  source_repo? : String @length("500") @comment("Git 仓库地址(预留)"),
  source_branch? : String @length("100") @comment("Git 分支(预留)"),
  custom_domains : JSON @comment("自定义域名列表"),
  production_deployment_id? : String @comment("当前生产部署ID(UI镜像,真相=软链)"),
  created_by? : String, updated_by? : String,
  created_at? : DateTime @default(now()), updated_at? : DateTime @default(now()),
  @index(name: "UQ_PAGE_SITE_NAME", unique: true, fields: [name]),
  @system, @comment("Pages 站点(项目级资源)")
}

model f_page_deployment {
  id : String @id @default(uuid()),
  site_name : String @comment("站点 slug"),
  alias : String @length("50") @comment("部署别名(production/preview/<shortId>)"),
  environment : PageEnvironment @comment("环境(PRODUCTION/PREVIEW)"),
  status : PageDeploymentStatus @default("QUEUED") @comment("部署状态"),
  source_type : PageSource @default("UPLOAD"),
  file_count : Int @default(0),
  size_bytes : Long @default(0),
  error_message? : String @length("2000"),
  created_by? : String, updated_by? : String,
  created_at? : DateTime @default(now()), updated_at? : DateTime @default(now()),
  @index(name: "IDX_PAGE_DEPLOY_SITE", fields: [site_name, alias]),
  @index(name: "UQ_PAGE_DEPLOY_ALIAS", unique: true, fields: [site_name, alias]),
  @system, @comment("Pages 部署记录")
}

enum PageSource { UPLOAD, GIT, @system }
enum PageEnvironment { PRODUCTION, PREVIEW, @system }
enum PageDeploymentStatus { QUEUED, READY, FAILED, @system }
```

codegen `replaceString` 去前缀 → 实体 `PageSite`/`PageDeployment`，元模型 `System.pageSite`/`System.pageDeployment`，枚举
`PageSource`/`PageEnvironment`/`PageDeploymentStatus`。

### 2. `pages/` 特性包（`flexmodel-server/src/main/java/dev/flexmodel/pages/`）

仿 `functions/` 结构：

| 文件                                                                                                                  | 职责                                                                                                                                                                                                  |
|-----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PageResource.java`                                                                                                   | `@Path("/projects/{projectId}/pages")`：站点 CRUD + 触发部署 + 列部署 + 切生产别名。`@RequiresPermissions`                                                                                            |
| `PageService.java`                                                                                                    | 站点 CRUD、部署编排：调 `PageDeployer` → 写 deployment 记录 → 切软链 → 镜像 `production_deployment_id`。失败记 `FAILED`                                                                               |
| `PageSiteRepository.java` + `PageSiteFmRepository.java`                                                               | `AbstractRepository` + `Session.dsl()`（同 `FunctionFmRepository`）                                                                                                                                   |
| `PageDeploymentRepository.java` + `PageDeploymentFmRepository.java`                                                   | 同上                                                                                                                                                                                                  |
| `PageDeployer.java`                                                                                                   | 接收 `InputStream`(zip) 或单文件 → 解包 → 写 `{root}/{site}/{deploymentId}/`（`Files`）→ 统计 `file_count`/`size_bytes`；过滤 `..` 路径与绝对路径条目（防穿越）→ 返回 deployment                      |
| `PageAliasManager.java`                                                                                               | 创建/原子切换相对软链 `{root}/{site}/{alias}→{deploymentId}`；`Files.createSymbolicLink` + 临时名 + `Files.move(ATOMIC_MOVE)`                                                                         |
| `PageDevHandler.java`                                                                                                 | **仅 dev/测试**：`@Observes` Vert.x `Router`，`flexmodel.pages.dev-fallback=true` 时注册 `/p/...` 复刻 nginx 的解析+SPA 回退（读同一文件树），供 `quarkus:dev` 无 nginx 时验证。生产关闭              |
| `dto/PageSiteRequest.java` / `PageSiteResponse.java` / `PageDeploymentResponse.java` / `PageDeployUploadRequest.java` | Lombok `@Data`，仿 `FunctionDeployRequest`/`FunctionResponse`                                                                                                                                         |
| `config/PagesConfig.java`                                                                                             | `@ConfigMapping(prefix="flexmodel.pages")`：`root-path`（默认 `./pages`）、`base-domain`（默认 `pages.local`）、`preview-path-prefix`（默认 `/p`）、`public-base-url`、`dev-fallback`（默认 `false`） |

文件上传端点：`POST /projects/{projectId}/pages/{siteName}/deployments`，`@Consumes(MediaType.MULTIPART_FORM_DATA)`，
`@FormParam("file") InputStream file`（zip）+ 可选 `@FormParam("environment")`。服务端流式解包，避免大 zip 占内存。

### 3. 公开服务（nginx-direct，配置由后端提供）

技术真相： **nginx 直接服务**。Java 端只生成 nginx 所需配置片段（路径预览/子域/自定义域）与文件树 + 软链。dev/测试由
`PageDevHandler` 复刻。

nginx 路径预览（`root` + 重构 `try_files`，支持 SPA 回退到站点 alias 的 `index.html`）：

```nginx
location ~ ^/p/(?<site>[^/]+)/(?<alias>[^/]+)(?<subpath>/.*)?$ {
    root /data/pages;
    try_files /$site/$alias$subpath /$site/$alias$subpath/ /$site/$alias/index.html =404;
    # MIME 由 nginx mime.types + 显式补充
    types { application/javascript js mjs; text/css css; text/html html; ... }
    # 资产可加长缓存（hash 文件名）；HTML 不缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|woff2?|ttf|ico)$ {
        expires 30d; add_header Cache-Control "public, immutable";
    }
}
```

子域名：

```nginx
server {
    listen 80;
    server_name ~^(?<site>[^.]+)\.pages\.local$;   # 生产替换 base-domain
    root /data/pages/$site/production;
    location / {
        try_files $uri $uri/ /index.html =404;
        types { ... }
    }
}
```

自定义域名（Java 按 `f_page_site.custom_domains` 生成可 include 片段 + `nginx -s reload`）：

```nginx
map $host $pages_site { default ""; app.example.com foo; blog.example.com bar; }
server {
    server_name app.example.com blog.example.com;
    root /data/pages/$pages_site/production;
    location / { try_files $uri $uri/ /index.html =404; }
}
```

> path `..` 穿越：`site`/`alias` 为 `[^/]+` 无斜杠；`subpath` 经 nginx 规范化且软链已锚定 deployment 目录，无法越出
> root。上传时 `PageDeployer` 额外拒绝含 `..`/绝对路径的 zip 条目。

### 4. 配置（`application.properties` 追加）

```properties
flexmodel.pages.root-path=${FLEXMODEL_PAGES_ROOT:./pages}
flexmodel.pages.base-domain=${FLEXMODEL_PAGES_BASE_DOMAIN:pages.local}
flexmodel.pages.preview-path-prefix=/p
flexmodel.pages.public-base-url=${FLEXMODEL_PAGES_PUBLIC_BASE_URL:http://localhost:8080}
flexmodel.pages.dev-fallback=false   # 仅 quarkus:dev/测试设 true
```

---

## Phase 1 前端（基础 UI）

`flexmodel-ui/src/`：

| 文件                          | 职责                                                                                                                                                                                         |
|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `services/pages.ts`           | 仿 `services/function.ts`：`getPageSites`/`createPageSite`/`deletePageSite`/`getDeployments`/`deployUpload`（`FormData` 上传 zip）/`setProductionDeployment`。复用 `api`                     |
| `pages/Pages/index.tsx`       | 站点列表 + 「新建站点」Modal（name/framework/build_output_dir/source_type）                                                                                                                  |
| `pages/Pages/SiteDetail.tsx`  | 站点详情 + 部署历史表格 + 「上传 zip 部署」按钮（AntD `Upload` before-upload 拦截走自定义请求）+ 显示 `/p/{site}/production/` 与各 alias 预览链接 + 「设为生产」操作                         |
| `routes.tsx`                  | projectRoutes 追加：`/project/:projectId/pages`（`<Pages/>`，icon `GlobalOutlined`，`translationKey: "pages.title"`）与 `/project/:projectId/pages/:siteName`（`<SiteDetail/>`，hideInMenu） |
| `locales/zh.json` / `en.json` | 追加 `pages.*` 翻译                                                                                                                                                                          |

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

`deploy/docker-compose/nginx/conf.d/default.conf` 追加上述 `/p/` location 与 `*.pages.<base>` server。自定义域名 map 片段由
`PageService` 写入 nginx include 目录并提供 `nginx -s reload` 触发（MVP 可手动 reload，自动化延后）。

---

## 延后项（明确不在本期）

- **Git 连接 + 真构建器**（拉仓库→跑 `build_command`→取 `build_output_dir`）。数据模型已留
  `source_type/source_repo/source_branch/build_command/build_output_dir`，后续接 worker（可复用 functions-runtime
  沙箱或新建容器）。
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
    - 创建站点 → `multipart` 上传含 `index.html`+`assets/app.js` 的 zip → 断言 DB `status=READY`、`file_count=2`、磁盘存在
      `{root}/{site}/{deploymentId}/index.html`、`{root}/{site}/production` 为指向该目录的 **软链**。
    - 切 `production` 到新部署 → 软链原子换向，旧部署文件仍在（可回滚）。
    - 经 dev-fallback：`GET /p/{site}/production/index.html` → 200 + `text/html`；
      `GET /p/{site}/production/assets/app.js` → 200 + `application/javascript`；`GET /p/{site}/production/missing.js` →
      404；`GET /p/{site}/production/users/123` → 200（SPA 回退 `index.html`）；`GET /p/{site}/production/`（尾斜杠）→ 200。
    - 路径穿越：`GET /p/{site}/production/../../etc/passwd` → 404（不越 root）。 运行：
      `mvn test -pl flexmodel-server -am -Dtest=PageResourceTest,PageDevHandlerTest`
4. **前端**：`cd flexmodel-ui && npm run build`（tsc -b + vite build，零 TS 错误）。
5. **手动 e2e**（可选，记录入 progress）：`./mvnw quarkus:dev` + 起 nginx 挂载 `./pages` → 创建站点 → curl 上传 zip → 浏览器开
   `http://localhost:8080/p/{site}/production/`（dev-fallback）或经 nginx 的 `http://localhost/p/{site}/production/`。

## 关键文件清单

- 追加：`flexmodel-server/src/main/resources/project.fml`
- 新建：`flexmodel-server/src/main/java/dev/flexmodel/pages/**`（约 10 个 Java 文件，见上表）
- 追加：`flexmodel-server/src/main/resources/application.properties`
- 新建测试：`flexmodel-server/src/test/java/dev/flexmodel/pages/PageResourceTest.java`、`PageDevHandlerTest.java`
- 新建：`flexmodel-ui/src/services/pages.ts`、`flexmodel-ui/src/pages/Pages/index.tsx`、
  `flexmodel-ui/src/pages/Pages/SiteDetail.tsx`
- 追加：`flexmodel-ui/src/routes.tsx`、`flexmodel-ui/src/locales/zh.json`、`flexmodel-ui/src/locales/en.json`
- 部署配置：`deploy/docker-compose/docker-compose.yml`（pages 卷 + 挂载）、`deploy/docker-compose/nginx/conf.d/default.conf`
  （追加 `/p/` 与 `*.pages.<base>` server）

## 收尾（遵循 AGENTS.md）

- `feature_list.json` 追加 `feat-011 Pages — 静态站点托管`，status `in-progress`（端到端 + Git 构建补齐后转 `done`
  ），evidence 记录编译/测试/UI 构建结果。
- `progress.md` 记录本期产出、已落地（上传部署 + nginx-direct 服务 + 路径预览/子域 + 软链回滚）、延后项（Git 构建器/自定义域
  TLS/S3 后端）。
- 提交：`feat(pages): 实现静态站点托管（本地上传 + nginx 直挂 + 软链别名）`。
