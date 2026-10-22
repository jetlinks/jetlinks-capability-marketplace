# jetlinks-capability-marketplace

JetLinks 能力市场 SDK，提供能力查询、下载、安装、升级和已安装资源管理。

- [marketplace-core](marketplace-core/)：能力包、资源和版本等公共契约，以及 Client、Provider SPI。
- [marketplace-client](marketplace-client/)：安装编排、安装记录管理、HTTP/Command 客户端和 Spring 配置。

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
`MarketplaceResourceDetail`。公共契约 `CapabilityLatestVersionInfo` 在 `CapabilityInfo` 基础上携带最新
`CapabilityVersion` 和资源级 `attachment`；版本信息补充 `publishTime`、`others` 和
`dependencyDetails`，其中依赖详情仍使用
`CapabilityLatestVersionInfo` 表达启用的依赖能力，并在其 `versionRange` 范围内选择最高版本。

查询沿用 `CapabilitySearchRequest` 的分页和筛选语义；`paging` 默认为 `true`，设置为 `false` 时忽略
`pageIndex` 和 `pageSize` 返回全部匹配结果。HTTP 与 Command 两条现有边界返回相同结构。
运行时入口为 `POST /marketplace/capabilities/version/_search`。
SaaS Manager 内部复用资源详情查询和直接依赖装配逻辑，在 `MarketplacePublicService` 统一转换为公共契约；
Controller、Command Handler 和 Client 只负责参数与结果转发，不暴露 SaaS 内部实体和 DTO。

查询只展开当前版本的直接依赖，依赖项自身的 `dependencyDetails` 不递归填充；发布时间原样返回版本
`publishTime` 与 `others.contentPublishedAt`，由调用方按 `publishTime` 优先、`contentPublishedAt` 兜底的规则
判断同版本内容是否更新。不改变现有能力安装时的 `versionRange` 解析和依赖安装行为。

没有最新版本时 `version` 为 `null`；没有依赖时 `dependencyDetails` 为空集合。
SaaS 公开查询只返回启用资源，并沿用既有认证、可用性判断和数据脱敏逻辑。

## 已安装资源管理

### 绑定主键

`capability_resource_install` 的主键使用 `capabilityId + type + resourceId + dataId` 生成稳定摘要，
用于表达一条逻辑安装绑定，而不是一次安装流水。相同能力资源重新安装、升级或补写版本时应复用同一主键，
避免同一逻辑绑定因重复保存落成多条记录；版本号仍单独存储，不参与绑定主键生成。

### 资源详情

`CapabilityResourceInstallEntity#toResource()` 返回 `InstalledResourceDetail`，
在基础 `InstalledResource` 字段之外额外暴露 `providerId` 以及审计字段
`creatorId`、`createTime`、`modifierId`、`modifyTime`。前端做安装状态判断时，
直接读取安装记录的 `modifyTime`，不从版本详情字段推断更新时间。

### 资产类型转换与绑定

能力资源的 `type` 表示能力 Provider 暴露的资源类型。SDK 通过 `CapabilityProvider`
的 `resolveAssetType(String)` 提供类型转换；Provider 缺失或返回空 Mono 时，marketplace-client 的安装资源解析工具会回退使用
`CapabilityResourceInstallEntity.type`。

安装记录额外保存 Provider ID，不修改公共的 `InstalledResource`，也不把转换后的资产类型写入安装资源。
资产、租户组件统一解析时先使用已注册资产类型及别名的规范 ID；未注册类型直接调用 `CapabilityProviders.resolveAssetType`
按实际 Provider 转换。查询前没有具体 Provider 时保留后置解析，结果过滤与安装后绑定使用相同优先级。
安装记录保存完成后由 marketplace-client 的安装后扩展点通知 assets-component，按最终资产类型批量绑定当前资产持有人。
SDK 不依赖资产类型注册表，Provider 转换及资源类型回退仍由原有解析工具处理。

集群 Provider 通过 RPC 调用实际 Provider 的类型转换方法，在本地缓存转换结果（包括空结果），Provider 刷新时清空缓存。
跨服务安装时，`CapabilityContext.loadInstallResources()` 和 `loadDependencyResources()` 使用 `marketplace-core`
中的 `InstalledResource` 作为公共资源契约；安装编排和集群命令回调均将本地实体转换为该契约后再传递。

### 安装记录扩展

[CapabilityInstalledResourceInterceptor](marketplace-client/src/main/java/org/jetlinks/marketplace/client/spi/CapabilityInstalledResourceInterceptor.java)
提供加载、记录管理和结果过滤三个扩展点，由 Spring 注册的拦截器参与处理：

| 扩展点 | 上下文与默认行为 |
| --- | --- |
| `loadInstalled(context, chain)` | 上下文包含业务加载范围和查询参数；链尾按参数查询安装记录。 |
| `afterInstalled(context, chain)` | 上下文包含 Provider、安装包、本次覆盖的旧记录及新记录；链尾替换旧记录并保存新记录。 |
| `filter(context, resources)` | 按原始业务加载范围过滤记录；默认查询和自定义来源都经过所有结果过滤器。 |

加载节点调用 `chain.proceed(context)` 继续后续节点，使用 `chain.proceed(context.withQuery(query))` 传递替换后的查询参数；
追加条件应与原业务范围取交集。节点也可直接返回安装记录，接管本次加载。列表加载不分页，该扩展同时用于状态判断、升级和依赖加载。

记录管理节点调用 `chain.proceed(context)` 继续后续节点，或自行完成旧记录替换和新记录保存。
整条记录管理链成功完成后才调用 `CapabilityInstalledResourcePostProcessor`，资产绑定在此阶段执行；异常中断安装事务和后处理。

上下文按订阅独立创建。Spring 装配按 `@Order` 排序，手动构造管理器时按传入列表顺序执行。
每个节点每次处理至多调用一次 `chain.proceed`，不自行订阅或重试。直接返回空流或空完成信号表示节点已接管处理，
不会触发默认查询或保存。自定义加载仍需经过链外的结果过滤，自定义记录管理仍需在成功后执行链外的保存后处理。

扩展范围为管理器的加载和安装后记录管理，独立查询命令、卸载删除使用各自的存储入口。
自定义存储需保证这些入口兼容，并负责外部存储的事务一致性。

### 数据权限与版本要求

SDK 提供扩展机制，数据权限由资产、租户组件实现。明确指定已注册资产类型或别名时，权限拦截器使用规范资产 ID，
向安装记录的 `dataId` 注入关联权限条件；Provider 不覆盖已注册资产类型。未指定类型时按各记录的类型执行结果过滤，
类型未注册时由结果过滤按实际 Provider 解析。

已有 `filter` 实现和 lambda 可继续使用，新增钩子默认委托后续节点。
使用加载和记录管理责任链的资产、租户组件需配套升级到包含 Load/Save Chain 类型的 SDK；先发布 SDK，再升级组件。
