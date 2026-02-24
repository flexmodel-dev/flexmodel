# docker

## 启动命令

```shell
docker run -p 8080:8080 -e JAVA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=GMT+08 -Dflexmodel.datasource.dk-kind=mysql -Dflexmodel.datasource.url=jdbc:mysql://mysql:3306/${MYSQL_DATABASE} -Dflexmodel.datasource.username=root -Dflexmodel.datasource.password=${MYSQL_ROOT_PASSWORD}" -t cjbi/flexmodel:latest 
```
