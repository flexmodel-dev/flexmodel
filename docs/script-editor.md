# 脚本编辑器

## 请求上下文示例

```js
context = {
  request: {
    method,
    url,
    headers,
    body,
    query
  },
  response: {
    status,
    headers,
    body
  },
  dbs: {
    "datasource_name": {
      findById(modelName, id) {
      },
      find(modelName, ...query: {
        filter: {},
        page: number,
        size: number,
        sort: [{field: string, order: "ASC" | "DESC" }],
      }) {
      },

    }
  },
  log: {
    info(msg, ...arg) {
    },
    error(msg, ...arg) {
    },
    debug(msg, ...arg) {
    },
    warn(msg, ...arg) {
    }
  },
  utils: {
    md5(str) {
    },
  }
}
```


## 流程节点上下文示例

```js
context = {
  data: {},
  log: {
    info(msg, ...arg) {
    },
    error(msg, ...arg) {
    },
    debug(msg, ...arg) {
    },
    warn(msg, ...arg) {
    }
  },
  utils: {
    md5(str) {
  },
  }
}
```

## 示例

1. 网络请求示例
    ```js
    const data = context.request.body;
    // 打印日志
    context.log.info("请求数据为：{}", data);
    // 请求响应处理
    context.response.body = {"success": true, "msg": "ok"};
   ```
2. 增删改查
    ```js
    const data = context.request.body;
    // 打印日志
    context.log("请求数据为：{}", data);
    // 请求响应处理
    context.response.body = {"success": true, "msg": "ok"};
   ```

3. 节点处理
   ```js
   // 获取节点数据
    const data = context.data;
    data.name = "张三";
   ```
