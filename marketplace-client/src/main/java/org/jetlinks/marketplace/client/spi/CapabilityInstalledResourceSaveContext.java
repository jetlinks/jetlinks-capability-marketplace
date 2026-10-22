package org.jetlinks.marketplace.client.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Supplier;

/**
 * Provider 安装完成后的记录处理上下文。旧记录仅包含本次覆盖目标，新记录来自本次 Provider 返回结果。
 *
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#afterInstalled(CapabilityInstalledResourceSaveContext)
 */
@AllArgsConstructor
public final class CapabilityInstalledResourceSaveContext {

    @Getter
    private final CapabilityProvider provider;

    @Getter
    private final CapabilityPackage capabilityPackage;

    @Getter
    private final List<CapabilityResourceInstallEntity> previousResources;

    @Getter
    private final List<CapabilityResourceInstallEntity> resources;

    private final Supplier<Mono<Void>> saver;

    /**
     * 继续后续记录处理扩展，链尾默认删除旧记录并保存新记录。
     * 扩展应至多委托一次；自行接管记录存储时不调用此方法。异常向安装事务传播。
     *
     * @return 后续记录处理的完成信号；空信号代表完成，不触发默认回退
     */
    public Mono<Void> save() {
        return Mono.defer(saver);
    }
}
