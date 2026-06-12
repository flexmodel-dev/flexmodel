# 对象存储功能重构：配置预设 + Bucket 管理

## 现状分析

当前 `f_storage` 实体混合了"后端配置"和"逻辑容器"两个概念，用户需手动输入 S3 凭证或本地路径。

## 目标架构

**Bucket = Prefix**，参考 Supabase Storage：

```
StorageProvider (持有 StorageBackend)
    |
StorageBackend (底层存储引擎抽象)
    |-- LocalBackend
    |-- S3Backend
    |
StorageOperationsFactory (Bucket -> StorageOperations)
    |
StorageOperations (文件操作接口，不变)
    |
BucketService (数据库事务 + 权限校验 + 元数据)
    |
BucketRepository
    |
BucketResource (REST API)
```

### 真实存储路径统一

```
Local:  ./storage/{ownerType}/{ownerId}/{bucketName}/file
S3:     s3://{s3-bucket}/{ownerType}/{ownerId}/{bucketName}/file
```

---

## Task 1: 更新 FML 数据模型

**文件**: `flexmodel-server/src/main/resources/project.fml`

移除 `f_storage` model 和 `StorageType` 枚举，替换为：

```fml
model f_bucket {
  id : String @id @default(uuid()),
  name : String @comment("Bucket 名称"),
  description? : String @length("500") @comment("Bucket 描述"),
  visibility : BucketVisibility @default("PRIVATE") @comment("访问可见性"),
  max_file_size? : Long @comment("单文件大小限制(字节)"),
  owner_type : String @comment("归属类型(如 PROJECT)"),
  owner_id : String @comment("归属ID"),
  created_by?: String @comment("创建人"),
  updated_by?: String @comment("更新人"),
  created_at?: DateTime @default(now()) @comment("创建时间"),
  updated_at?: DateTime @default(now()) @comment("更新时间"),
  @index(name: "IDX_OWNER", fields: [owner_type, owner_id]),
  @index(name: "UQ_BUCKET_NAME", unique: true, fields: [owner_type, owner_id, name]),
  @comment("存储 Bucket")
}

enum BucketVisibility {
  PRIVATE,
  AUTHENTICATED,
  PUBLIC
}
```

设计要点：
- `id`（uuid）为主键，避免跨项目同名冲突
- `owner_type` + `owner_id` 泛化归属关系（当前用 PROJECT，未来可扩展 USER/TEAM 等）
- `visibility` 预留 AUTHENTICATED，避免未来数据迁移
- 移除 `allowed_mime_types`（策略问题，非 Bucket 元数据，未来可提取为独立 BucketPolicy 表）

## Task 2: 新增 StorageBackend 接口和实现

**新文件**:
- `flexmodel-server/.../storage/config/StorageBackend.java`
- `flexmodel-server/.../storage/config/LocalBackend.java`
- `flexmodel-server/.../storage/config/S3Backend.java`

StorageBackend 包含文件操作 + 容器生命周期：

```java
public interface StorageBackend {
  // 文件操作
  StorageOperations createOperations(String prefixPath);
  // 容器生命周期
  void createContainer(String prefix);
  void deleteContainer(String prefix);
  boolean containerExists(String prefix);
  // 后端管理
  void validate();
  String getType();
}
```

- `LocalBackend.createContainer(prefix)` -- mkdir 创建目录
- `LocalBackend.deleteContainer(prefix)` -- 递归删除目录
- `S3Backend.createContainer(prefix)` -- 创建前缀占位对象
- `S3Backend.deleteContainer(prefix)` -- 删除前缀下所有对象

`readOnly` 模式下，`LocalBackend`/`S3Backend` 的写操作（createContainer/deleteContainer 及 createOperations 返回的写方法）抛出 `StorageReadOnlyException`。

## Task 3: 新增 StorageProviderConfig 和 StorageProvider

**新文件**:
- `flexmodel-server/.../storage/config/StorageProviderConfig.java`
- `flexmodel-server/.../storage/config/StorageProvider.java`
- `flexmodel-server/.../storage/config/StorageProviderInitializer.java`
- `flexmodel-server/.../storage/config/StorageReadOnlyException.java`

StorageProviderConfig：

```java
@ConfigMapping(prefix = "flexmodel.storage")
public interface StorageProviderConfig {
  Optional<String> type();            // "local" (default) or "s3"
  Optional<Boolean> readOnly();       // default: false, 只读模式
  Optional<String> localPath();       // default: "./storage"
  Optional<String> s3AccessKey();
  Optional<String> s3SecretKey();
  Optional<String> s3Bucket();        // 真实 S3 Bucket 名称，如 "flexmodel"
  Optional<String> s3Region();        // default: "us-east-1"
  Optional<String> s3Endpoint();
  Optional<Boolean> s3PathStyle();    // default: false
}
```

StorageProvider（`@ApplicationScoped`，持有 StorageBackend）：

```java
@ApplicationScoped
public class StorageProvider {
  private StorageBackend backend;
  private StorageProviderConfig config;

  public void initialize(StorageProviderConfig config) { ... }
  public StorageBackend getBackend() { return backend; }
  public boolean isReadOnly() { return config.readOnly().orElse(false); }
  public Map<String, Object> getProviderInfo() {
    // 返回 { type, bucket(如有), endpoint(如有), readOnly }
  }
}
```

StorageProviderInitializer（`@Observes StartupEvent`）：
- 未配置时默认 local + `./storage`
- 启动时 `backend.validate()` 验证可用性

## Task 4: 重构 StorageOperationsFactory

**修改文件**: `flexmodel-server/.../storage/StorageOperationsFactory.java`

接受 Bucket 对象，内部构建前缀路径（路径规则只维护一份）：

```java
@ApplicationScoped
public class StorageOperationsFactory {
  @Inject StorageProvider storageProvider;

  /** 构建存储前缀：{ownerType}/{ownerId}/{bucketName} */
  private String buildPrefix(Bucket bucket) {
    return bucket.getOwnerType() + "/" + bucket.getOwnerId() + "/" + bucket.getName();
  }

  /** 根据 Bucket 创建带路径前缀的 StorageOperations */
  public StorageOperations forBucket(Bucket bucket) {
    return storageProvider.getBackend().createOperations(buildPrefix(bucket));
  }
}
```

## Task 5: 新增 BucketService 和 BucketRepository

**新文件**:
- `flexmodel-server/.../storage/BucketService.java`
- `flexmodel-server/.../storage/BucketRepository.java`
- `flexmodel-server/.../storage/BucketFmRepository.java`
- `flexmodel-server/.../storage/BucketNotEmptyException.java`

BucketService 核心职责（数据库事务 + 权限校验 + 元数据，不关心底层存储实现）：

```java
public class BucketService {
  // CRUD
  Bucket createBucket(String ownerType, String ownerId, Bucket bucket);
  List<Bucket> listBuckets(String ownerType, String ownerId);
  Optional<Bucket> getBucket(String ownerType, String ownerId, String bucketName);
  Bucket updateBucket(String ownerType, String ownerId, String bucketName, Bucket bucket);
  void deleteBucket(String ownerType, String ownerId, String bucketName, boolean force);

  // 文件操作委托
  List<FileItem> listFiles(Bucket bucket, String path);
  void uploadFile(Bucket bucket, String path, InputStream in, long size);
  InputStream downloadFile(Bucket bucket, String path);
  void deleteFile(Bucket bucket, String path);
  void createFolder(Bucket bucket, String path);
  boolean exists(Bucket bucket, String path);
  long getFileSize(Bucket bucket, String path);
}
```

删除逻辑（预留异步扩展点）：
- 提取 `deleteBucketContents(Bucket bucket)` 为独立方法
- 默认同步实现；非 force 时先检查是否为空
- 未来可替换为异步 `BucketDeleteJob`，Service 层不硬编码删除细节

createBucket 时调用 `backend.createContainer(prefix)` 创建底层容器。

## Task 6: 重构 Resource 层

**修改文件**: `flexmodel-server/.../storage/StorageResource.java`

重命名为 `BucketResource`，路径 `/projects/{projectId}/buckets`：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/buckets` | 列出 buckets |
| POST | `/buckets` | 创建 bucket |
| GET | `/buckets/{bucketName}` | 获取 bucket 详情 |
| PUT | `/buckets/{bucketName}` | 更新 bucket |
| DELETE | `/buckets/{bucketName}?force=false` | 删除 bucket |
| GET | `/buckets/{bucketName}/files` | 列出文件 |
| POST | `/buckets/{bucketName}/files/upload` | 上传文件 |
| GET | `/buckets/{bucketName}/files/download` | 下载文件 |
| DELETE | `/buckets/{bucketName}/files/delete` | 删除文件 |
| POST | `/buckets/{bucketName}/folders/create` | 创建文件夹 |
| GET | `/buckets/{bucketName}/files/exists` | 检查文件存在 |
| GET | `/buckets/{bucketName}/files/size` | 获取文件大小 |

Resource 层使用 `ownerType="PROJECT"`, `ownerId=projectId` 调用 BucketService。

新增 **StorageProviderResource**（或放在同一 Resource 中）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/storage/provider` | 获取当前存储后端信息 |

返回：

```json
{
  "type": "s3",
  "bucket": "flexmodel",
  "endpoint": "http://minio:9000",
  "readOnly": false
}
```

移除旧的 `validate` 端点。

## Task 7: 更新 application.properties

**修改文件**: `flexmodel-server/src/main/resources/application.properties`

```properties
# Storage Provider Configuration
# type: local (default) or s3
# flexmodel.storage.type=local
# flexmodel.storage.local-path=./storage
# flexmodel.storage.read-only=false

# S3 Configuration (uncomment to use S3)
# flexmodel.storage.type=s3
# flexmodel.storage.s3-access-key=your-access-key
# flexmodel.storage.s3-secret-key=your-secret-key
# flexmodel.storage.s3-bucket=flexmodel
# flexmodel.storage.s3-region=us-east-1
# flexmodel.storage.s3-endpoint=http://localhost:9000
# flexmodel.storage.s3-path-style=true
```

## Task 8: 清理旧代码

删除：
- `StorageService.java`, `StorageRepository.java`, `StorageFmRepository.java`, `StorageResource.java`, `dto/ValidateStorageResult.java`

保留并修改：
- `StorageOperationsFactory.java` -- 职责变为 `forBucket(Bucket)`
- `StorageOperations.java` -- 接口不变
- `FileItem.java` -- 不变
- `config/LocalStorageOperations.java` -- 构造函数调整
- `config/S3StorageOperations.java` -- 构造函数调整

## Task 9: 更新前端类型定义

**修改文件**: `flexmodel-ui/src/types/storage.d.ts`

移除 `StorageType`、`S3Config`、`LocalConfig`、`StorageConfig`、`StorageSchema`，替换为：

```typescript
export type BucketVisibility = 'PRIVATE' | 'AUTHENTICATED' | 'PUBLIC';

export interface BucketSchema {
  id: string;
  name: string;
  description?: string;
  visibility: BucketVisibility;
  maxFileSize?: number;
  ownerType: string;
  ownerId: string;
  createdAt: string;
  updatedAt: string;
}

export interface StorageProviderInfo {
  type: string;
  bucket?: string;
  endpoint?: string;
  readOnly: boolean;
}

// 保留 FileItem、FileType 不变
```

## Task 10: 更新前端 Service 层

**修改文件**: `flexmodel-ui/src/services/storage.ts`

- 所有路径从 `/storages/` 改为 `/buckets/`
- `getBucketList(projectId)`, `createBucket`, `updateBucket`, `deleteBucket(projectId, name, force?)`
- 文件操作中 `storageName` 改为 `bucketName`
- 移除 `validateStorage`
- 新增 `getStorageProviderInfo(): Promise<StorageProviderInfo>`

## Task 11: 重构前端页面

**修改/新增文件**（`flexmodel-ui/src/pages/Storage/`）：

- `index.tsx` -- Bucket 管理页面（左侧 Bucket 列表 + 右侧文件浏览器/设置，顶部显示 Provider 信息）
- `components/BucketExplorer.tsx` -- 替代 StorageExplorer
- `components/CreateBucketDrawer.tsx` -- 简化表单（name/description/visibility）
- `components/BucketForm.tsx` -- 替代 StorageForm，移除 S3/本地配置
- `components/FileBrowser.tsx` -- props `storageName` 改为 `bucketName`
- `components/BucketView.tsx` -- 替代 StorageView，展示 bucket 元数据
- 移除 `CreateStorageDrawer.tsx`

## Task 12: 更新国际化文件

**修改文件**: `flexmodel-ui/src/locales/zh.json` 和 `en.json`

移除 S3/本地存储相关 key，新增：
- `bucket`, `create_bucket`, `delete_bucket`, `delete_bucket_confirm`
- `bucket_name`, `bucket_description`, `bucket_visibility`
- `visibility_private`, `visibility_authenticated`, `visibility_public`
- `max_file_size`, `bucket_not_empty`, `force_delete`, `force_delete_desc`
- `storage_provider`, `storage_provider_info`, `read_only`

## Task 13: 编译验证和测试更新

- `mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'`
- 更新 `LocalStorageOperationsTest.java`（适配构造函数变更）
- `cd flexmodel-ui && npx tsc --noEmit`

---

## 影响范围

| 层面 | 新增 | 修改 | 删除 |
|------|------|------|------|
| FML | f_bucket, BucketVisibility(PRIVATE/AUTHENTICATED/PUBLIC) | project.fml | f_storage, StorageType |
| 后端配置 | StorageProviderConfig(+readOnly), StorageProvider, StorageProviderInitializer, StorageBackend(+容器生命周期), LocalBackend, S3Backend, StorageReadOnlyException | application.properties | - |
| 后端业务 | BucketService, BucketRepository, BucketFmRepository, BucketResource, StorageProviderResource, BucketNotEmptyException | StorageOperationsFactory(forBucket(Bucket)), LocalStorageOperations, S3StorageOperations | StorageService, StorageRepository, StorageFmRepository, StorageResource, ValidateStorageResult |
| 前端类型 | BucketSchema, BucketVisibility, StorageProviderInfo | storage.d.ts | StorageType, S3Config, LocalConfig, StorageConfig, StorageSchema |
| 前端服务 | getStorageProviderInfo | storage.ts | validateStorage |
| 前端页面 | BucketExplorer, CreateBucketDrawer, BucketForm, BucketView | index.tsx, FileBrowser | StorageExplorer, CreateStorageDrawer, StorageForm, StorageView |
| 国际化 | bucket/visibility/provider 相关 key | zh.json, en.json | S3/本地配置相关 key |
