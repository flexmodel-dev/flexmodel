# docker-compose

Flexmodel 生产部署，包含以下服务：

| 服务 | 镜像 | 端口 |
|------|------|------|
| `mysql` | `mysql:8.0` | 3306 (内部) |
| `flexmodel-server` | `cjbi/flexmodel-server:latest` | 8080 (内部) |
| `flexmodel-ui` | `cjbi/flexmodel-ui:latest` | 80 |
| `flexmodel-functions-runtime` | `cjbi/flexmodel-functions-runtime:latest` | 9999 (内部) |

## 部署命令

* 拉取最新镜像

```shell
docker-compose pull flexmodel-server flexmodel-ui flexmodel-functions-runtime
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
