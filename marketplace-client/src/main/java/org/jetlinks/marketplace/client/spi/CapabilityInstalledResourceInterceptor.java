package org.jetlinks.marketplace.client.spi;

import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;

/**
 * 已安装能力资源过滤扩展点.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityInstalledResourceInterceptor {

    Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                 Flux<CapabilityResourceInstallEntity> resources);
}
