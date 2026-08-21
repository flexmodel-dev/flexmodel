# docker-compose

Flexmodel 生产部署，包含以下服务：

| 服务                          | 镜像                                      | 端口         |
|-------------------------------|-------------------------------------------|--------------|
| `mysql`                       | `mysql:8.0`                               | 3306 (内部)  |
| `rabbitmq`                    | `rabbitmq:3.13-management`                | 5672 / 15672 |
| `rustfs`                      | `rustfs/rustfs:latest`                    | 9000 / 9001  |
| `flexmodel`                   | `cjbi/flexmodel-server:latest`            | 8080 (内部)  |
| `flexmodel-ui`                | `cjbi/flexmodel-ui:latest`                | 80           |
| `flexmodel-functions-runtime` | `cjbi/flexmodel-functions-runtime:latest` | 9999 (内部)  |
| `nginx`                       | `nginx:1.25.3`                            | 80 (内部)    |

## 部署命令

* 拉取最新镜像

```shell
docker-compose pull
```

* 启动

```shell
docker-compose up
```

* 后台启动

```shell
docker-compose up -d
```

* 停止

```shell
docker-compose down
```

## RustFS 对象存储

本部署使用 [RustFS](https://rustfs.com)（S3 兼容的高性能对象存储）作为 `flexmodel-server` 的存储后端， 替代默认的本地磁盘存储。
`flexmodel.storage.type` 被置为 `s3`，所有 Bucket / 文件操作经由 S3 API 写入 RustFS。

| 服务     | 说明                                                                |
|----------|---------------------------------------------------------------------|
| `rustfs` | RustFS 主服务，S3 API `9000`，Web 控制台 `9001`，使用命名卷存储数据 |

### 关键配置（`.env`）

| 变量                  | 说明                        | 默认值            |
|-----------------------|-----------------------------|-------------------|
| `RUSTFS_ACCESS_KEY`   | S3 Access Key               | `flexmodel`       |
| `RUSTFS_SECRET_KEY`   | S3 Secret Key               | `flexmodel123456` |
| `RUSTFS_BUCKET`       | flexmodel-server 使用的桶名 | `flexmodel`       |
| `RUSTFS_S3_API_PORT`  | 对外暴露的 S3 API 端口      | `9000`            |
| `RUSTFS_CONSOLE_PORT` | 对外暴露的 Web 控制台端口   | `9001`            |

### 注意事项

- 单盘单节点模式下已开启 `RUSTFS_UNSAFE_BYPASS_DISK_CHECK=true`；生产环境建议挂载多盘并关闭该选项以启用纠删码。
- `flexmodel-server` 依赖 `rustfs` 健康检查通过后启动；启动时若桶不存在会自动创建（见下条）。
- `flexmodel-server` 的 `S3Backend` 对所有 S3 兼容存储通用：启动校验时桶不存在则自动 `createBucket`
  ，只读模式下不自动创建并报错提示运维预先建桶。因此本部署无需额外的初始化容器。
- Web 控制台访问：`http://<host>:${RUSTFS_CONSOLE_PORT}`，使用 `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY` 登录。
- 切回本地存储：删除 `flexmodel-server` 环境变量中的 `FLEXMODEL_STORAGE_TYPE` 及 `QUARKUS_S3_*` 配置即可回退到本地存储。
