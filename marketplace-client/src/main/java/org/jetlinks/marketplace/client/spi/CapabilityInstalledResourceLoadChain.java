package org.jetlinks.marketplace.client.spi;

import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;

/**
 * 安装记录加载的后续责任链，由管理器为当前节点创建，链尾执行默认安装表查询。
 * 仅负责继续加载，结果过滤由管理器在整条加载链之外统一执行。
 *
 * @author zhouhao
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#loadInstalled(CapabilityInstalledResourceLoadContext, CapabilityInstalledResourceLoadChain)
 */
@FunctionalInterface
public interface CapabilityInstalledResourceLoadChain {

    /**
     * 订阅时继续后续节点，不自行订阅、缓存或重试；每次处理至多委托一次。
     *
     * @param context 非空加载上下文；替换查询参数时仍须保留原业务范围
     * @return 后续加载结果；空流不触发默认回退，异常直接传播
     */
    Flux<CapabilityResourceInstallEntity> proceed(CapabilityInstalledResourceLoadContext context);
}
