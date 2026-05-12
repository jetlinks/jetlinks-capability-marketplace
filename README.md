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
- `progress` / `log`：进度与日志流水。
- `success` / `failed`：安装最终状态。

`CapabilityOperationContext` 只保存 `operationId`，不承载项目、用户、来源或运行时等业务字段；
这些信息由调用侧已有的租户、认证或链路上下文解析。
