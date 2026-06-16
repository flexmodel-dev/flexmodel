# docker-compose

Flexmodel 生产部署，包含以下服务：

| 服务 | 镜像 | 端口 |
|------|------|------|
| `mysql` | `mysql:8.0` | 3306 (内部) |
| `flexmodel` | `cjbi/flexmodel:latest` | 8080 (内部) |
| `flexmodel-sidecar` | `cjbi/flexmodel-sidecar:latest` | 9999 (内部) |
| `nginx` | `nginx:1.25.3` | 80 / 443 |

## 部署命令

* 拉取最新镜像

```shell
docker-compose pull flexmodel flexmodel-sidecar
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
