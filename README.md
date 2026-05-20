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

如果当前上下文已经存在可见的依赖安装记录，并且其最高安装版本满足 `versionRange`，则跳过依赖安装。
如果依赖已安装但版本不满足，则复用升级流程；如果存在多个可见安装根且未指定升级目标，沿用现有升级目标歧义错误。
依赖安装复用同一个 `CapabilityOperationContext`，并会检测循环依赖以避免递归安装。
主能力 Provider 执行时，可通过 `CapabilityContext.loadDependencyResources()` 获取本次依赖安装后可见的依赖资源，
或通过 `CapabilityContext.loadDependencyResources(type)` 按资源类型过滤依赖资源，用于读取依赖资源的 `dataId`。
