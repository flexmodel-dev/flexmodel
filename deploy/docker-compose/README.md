# docker-compose

Flexmodel 生产部署，提供两个编排文件：

* `docker-compose.yml` — 标准部署（本地文件存储）
* `docker-compose.rustfs.yml` — 附加 [RustFS](https://rustfs.com/)（S3 兼容对象存储），附件/文件存入 S3

## 服务

| 服务                       | 镜像                                              | 端口                                                                          |
|----------------------------|---------------------------------------------------|-------------------------------------------------------------------------------|
| `mysql`                    | `mysql:8.0`                                       | 3306 (内部)                                                                   |
| `flexmodel-server`         | `${FLEXMODEL_SERVER_IMAGE}`（native 或 jvm 镜像） | 8080 (内部)                                                                   |
| `flexmodel-ui`             | `cjbi/flexmodel-ui:latest`                        | 80 (内部，经 nginx 反代)                                                      |
| `flexmodel-apps-ui`        | `cjbi/flexmodel-apps-ui:latest`                   | `${APPS_UI_HTTP_PORT}`（默认 180）                                            |
| `nginx`                    | `nginx:1.25.3`                                    | `${NGINX_HTTP_PORT}` / `${NGINX_HTTPS_PORT}`（默认 80/443）                   |
| `rustfs`（仅 rustfs 编排） | `rustfs/rustfs:latest`                            | S3 API `${RUSTFS_S3_API_PORT}` (9000)、控制台 `${RUSTFS_CONSOLE_PORT}` (9001) |

## 配置

首次部署前，复制 `.env.template` 为 `.env` 并按需修改：

```shell
cp .env.template .env
```

关键配置：

* `FLEXMODEL_SERVER_IMAGE` — 服务端镜像，native（`cjbi/flexmodel-server-native:latest`）或 jvm（
  `cjbi/flexmodel-server:latest`）模式
* `MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` — MySQL 密码与库名
* `NGINX_HTTP_PORT` / `NGINX_HTTPS_PORT` — nginx 对外端口
* `APPS_UI_HTTP_PORT` — 零代码前端对外端口
* `FLEXMODEL_AI_BASE_URL` / `FLEXMODEL_AI_API_KEY` / `FLEXMODEL_AI_MODEL` — AI 能力接入（可选）

使用 rustfs 编排时还需在 `.env` 中补充 `RUSTFS_ACCESS_KEY`、`RUSTFS_SECRET_KEY`、`RUSTFS_BUCKET`。

数据持久化目录（宿主机挂载）：

* `./mysql/data` — MySQL 数据
* `./flexmodel/storage`、`./flexmodel/database`、`./flexmodel/logs` — 服务端存储/数据库/日志
* `./rustfs/data` — RustFS 数据（仅 rustfs 编排）

## 部署命令

* 拉取最新镜像

```shell
docker-compose pull
```

* 启动（标准部署）

```shell
docker-compose up -d
```

* 启动（rustfs 编排）

```shell
docker-compose -f docker-compose.rustfs.yml up -d
```

* 停止

```shell
docker-compose down
```
