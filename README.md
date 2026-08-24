# jetlinks-capability-marketplace
JetLinks 能力市场SDK

## 能力操作上下文与事件上报

能力下载、安装、升级等操作通过 `CapabilityOperationContext` 串联同一次操作的
`operationId`。运行时安装入口可使用 `CapabilityOperationContext.currentOrCreate()`
读取或创建上下文，并通过 `CapabilityOperationContext.makeCurrent(...)` 传递到下载、
安装 Provider、资源保存和进度回调链路。

`CapabilityMarketplaceClient.reportOperationEvent(CapabilityOperationEvent event)` 是统一
的操作事件上报入口。默认实现为空操作，HTTP client 会调用
`POST /marketplace/operations/_report`，命令 client 可通过
`ReportCapabilityOperationEventCommand` 转发到服务端。上报失败应只影响操作流水，不应中断
实际安装流程。

当前标准事件类型包括：

- `download`：开始下载能力包，只作为操作流水。
- `installing`：进入安装流程，可用于服务端维护当前安装状态。
- `action`：操作过程中 `ActionRecorder` 记录的动作摘要事件。
- `progress` / `log`：进度与日志流水。
- `success` / `failed`：安装最终状态。

Provider 可通过 `CapabilityContext.monitor().recorder()` 记录结构化动作信息。Recorder
产出的完整 `ActionRecord` 会放到安装进度流 `ProgressState.extra` 中，便于前端按结构化数据引导跳转；
`CapabilityOperationEvent` 仍只上报消息摘要和状态，不承载完整结构化细节。

`CapabilityOperationContext` 只保存 `operationId`，不承载项目、用户、来源或运行时等业务字段；
这些信息由调用侧已有的租户、认证或链路上下文解析。

## 能力依赖安装

能力包中的 `CapabilityInfo.dependencies` 会在主能力 Provider 执行前处理。依赖能力按声明顺序安装，
依赖安装或升级失败时会阻断主能力安装；`optional=true` 当前不改变失败策略。

依赖版本通过 `CapabilityMarketplaceClient.getVersions(capabilityId)` 获取，并从满足
`CapabilityDependency.versionRange` 的可用版本中选择最高版本。`versionRange` 为空时选择最高可用版本；
非空时支持逗号分隔的 AND 条件：`>=`、`>`、`<=`、`<`、`=`、`==`，裸版本按精确匹配处理。

当前上下文中全部可见的依赖安装记录会逐条与 `versionRange` 比较，所有满足范围的记录都会作为覆盖目标进入
依赖 Provider，由 Provider 根据安装请求和旧资源判断是否更新；没有满足范围的记录时按首次安装处理，不覆盖旧记录。
依赖安装复用同一个 `CapabilityOperationContext`，并会检测循环依赖以避免递归安装。
主能力 Provider 执行时，可通过 `CapabilityContext.loadDependencyResources()` 获取本次依赖安装后可见的依赖资源，
或通过 `CapabilityContext.loadDependencyResources(type)` 按资源类型过滤依赖资源，用于读取依赖资源的 `dataId`。

依赖 Provider 的返回结果会完整替换本次传入的覆盖目标绑定。Provider 只更新部分目标时，必须同时返回
未更新的旧资源；安装完成后，主能力上下文只加载版本仍满足 `versionRange` 的可见依赖资源。

## 能力最新版本详情查询

运行时能力市场只通过 `CapabilityMarketplaceClient` 访问 SaaS 能力市场，不直接依赖 SaaS 管理端的
`MarketplaceResourceDetail`。公共契约新增 `CapabilityLatestVersionInfo`，在 `CapabilityInfo` 基础上携带最新
`CapabilityVersion`；版本信息补充 `publishTime`、`others` 和 `dependencyDetails`，其中依赖详情仍使用
`CapabilityLatestVersionInfo` 表达启用的依赖能力，并在其 `versionRange` 范围内选择最高版本。

查询沿用 `CapabilitySearchRequest` 的分页和筛选语义；`paging` 默认为 `true`，设置为 `false` 时忽略
`pageIndex` 和 `pageSize` 返回全部匹配结果。HTTP 与 Command 两条现有边界返回相同结构。
运行时入口为 `POST /marketplace/capabilities/version/_search`。
SaaS Manager 内部复用资源详情查询和直接依赖装配逻辑，在 `MarketplacePublicService` 统一转换为公共契约；
Controller、Command Handler 和 Client 只负责参数与结果转发，不暴露 SaaS 内部实体和 DTO。

本次只展开当前版本的直接依赖，依赖项自身的 `dependencyDetails` 不递归填充；发布时间原样返回版本
`publishTime` 与 `others.contentPublishedAt`，由调用方按 `publishTime` 优先、`contentPublishedAt` 兜底的规则
判断同版本内容是否更新。不改变现有能力安装时的 `versionRange` 解析和依赖安装行为。

测试覆盖：

- 公共查询返回能力基本信息、最新版本、发布时间、`others`，以及启用的直接依赖在版本范围内的最高版本。
- 没有最新版本时 `version` 为 `null`；没有依赖时 `dependencyDetails` 为空集合，不产生递归查询或阻塞调用。
- HTTP Client、Command Client、内部 Client 与运行时 Controller 使用同一 `CapabilityLatestVersionInfo` 契约。
- SaaS 公开查询继续只返回启用资源，并沿用既有认证、可用性判断和数据脱敏逻辑。

跨服务 Provider 安装时，`CapabilityContext.loadInstallResources()` 和
`CapabilityContext.loadDependencyResources()` 只以 `marketplace-core` 中的
`InstalledResource` 作为资源契约。安装编排侧会在进入 Provider 前把本地持久化实体转换为
`InstalledResource`，集群命令回调侧也会在返回 RPC 前复制为基础 `InstalledResource`，避免远端服务加载
`marketplace-client` 内部实体类。

## 已安装资源绑定主键

`capability_resource_install` 的主键使用 `capabilityId + type + resourceId + dataId` 生成稳定摘要，
用于表达一条逻辑安装绑定，而不是一次安装流水。相同能力资源重新安装、升级或补写版本时应复用同一主键，
避免同一逻辑绑定因重复保存落成多条记录；版本号仍单独存储，不参与绑定主键生成。

## 已安装资源详情

`CapabilityResourceInstallEntity#toResource()` 返回 `InstalledResourceDetail`，
在基础 `InstalledResource` 字段之外额外暴露 `providerId` 以及审计字段
`creatorId`、`createTime`、`modifierId`、`modifyTime`。前端做安装状态判断时，
直接读取安装记录的 `modifyTime`，不从版本详情字段推断更新时间。

## 安装资源资产类型转换与绑定

能力资源的 `type` 表示能力 Provider 暴露的资源类型。安装资源资产类型优先通过 `CapabilityProvider`
的 `resolveAssetType(String)` 转换；Provider 缺失或返回空 Mono 时，marketplace-client 的安装资源解析工具会回退使用
`CapabilityResourceInstallEntity.type`。

安装记录额外保存 Provider ID，资产权限过滤时据此解析当前 Provider，再调用同一转换方法；不修改公共的
`InstalledResource`，也不把转换后的资产类型写入安装资源。安装记录保存完成后由 marketplace-client 的安装后扩展点通知
assets-component，后者使用相同的 Provider 转换结果按资产类型批量绑定当前资产持有人。
业务侧需要处理 `CapabilityResourceInstallEntity` 时，统一使用 marketplace-client 提供的 `CapabilityProviderUtils`
解析为 `ResolvedAssetTypeInstalledResource`，统一处理 Provider 转换与资源类型回退。

本次实施范围：

- marketplace-core：增加资源类型到资产类型的响应式转换 SPI，默认按资源类型作为资产类型返回。
- marketplace-client：保存 Provider ID，增加安装后处理 SPI 和安装资源资产类型解析工具，在 `savePackage` 保存安装记录后调用。
- assets-component：按 Provider 转换结果过滤已安装资源，并自动绑定有资产类型和数据 ID 的资源。
- 集群 Provider：通过 RPC 远程调用实际 Provider 的转换方法，并在 Cluster Provider 本地使用 Map 缓存正、负结果；Provider 刷新时清空。

测试目标：覆盖资产类型转换、Provider 返回空 Mono 时回退资源类型、Provider 缺失时回退资源类型、批量自动绑定、安装记录保存失败时不执行后处理，
以及集群 Provider 远程转换和缓存刷新行为。当前按仓库协作约束不自动新增或修改测试；涉及仓库的 `git diff --check` 已通过，Java
编译和测试未自动执行，请开发者切换 Java 21 后自行验证。
