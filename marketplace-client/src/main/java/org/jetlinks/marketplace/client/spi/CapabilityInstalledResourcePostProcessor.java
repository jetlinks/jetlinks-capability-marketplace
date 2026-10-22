package org.jetlinks.marketplace.client.spi;

import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * 已安装能力资源保存后的扩展点.
 *
 * <p>由 marketplace-client 在默认或扩展的安装记录管理成功完成后顺序调用，用于处理依赖具体业务模块的后置副作用。
 * 实现不应修改安装记录；返回异常会中断当前安装事务并向上游传播。</p>
 *
 * @author zhouhao
 * @see org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor
 * @since 2.12
 */
public interface CapabilityInstalledResourcePostProcessor {

    /**
     * 处理一次能力 Provider 返回的已安装资源.
     *
     * @param provider 当前能力 Provider，不为空
     * @param resources 已保存的安装资源记录集合，数据来自当前安装包，可能为空
     * @return 后置处理完成信号；返回空 Mono 表示无需处理
     * @see CapabilityProvider#resolveAssetType(String)
     */
    Mono<Void> process(CapabilityProvider provider,
                       Collection<CapabilityResourceInstallEntity> resources);
}
