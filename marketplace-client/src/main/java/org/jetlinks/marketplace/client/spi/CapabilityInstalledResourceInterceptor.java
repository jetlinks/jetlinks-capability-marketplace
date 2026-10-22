package org.jetlinks.marketplace.client.spi;

import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 已安装能力资源加载、记录管理及过滤扩展点.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityInstalledResourceInterceptor {

    /**
     * 加载安装记录，覆盖管理器的列表、状态判断、升级及依赖加载；原有结果过滤仍会统一执行。
     *
     * @param context 加载范围、查询参数及后续加载委托。追加约束应与原始条件取交集
     * @return 安装记录；可调用 context.load() 继续，或自行返回记录。空结果不回退默认查询，错误直接传播
     * @since 2.20
     * @see #filter(CapabilityInstalledResourceFilterContext, Flux)
     */
    default Flux<CapabilityResourceInstallEntity> loadInstalled(CapabilityInstalledResourceLoadContext context) {
        return context.load();
    }

    /**
     * Provider 安装成功后的记录管理，默认委托后续扩展，链尾替换旧记录并保存新记录。
     * 记录处理成功后才调用保存后处理器；扩展接管存储时须在完成信号前完成自身记录管理。
     *
     * @param context 当前 Provider、安装包、本次覆盖的旧记录、新记录及默认保存委托
     * @return 记录处理完成信号；空信号表示已处理，不触发默认保存；异常中断安装事务及后置处理
     * @since 2.20
     * @see CapabilityInstalledResourcePostProcessor
     */
    default Mono<Void> afterInstalled(CapabilityInstalledResourceSaveContext context) {
        return context.save();
    }

    Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                 Flux<CapabilityResourceInstallEntity> resources);
}
