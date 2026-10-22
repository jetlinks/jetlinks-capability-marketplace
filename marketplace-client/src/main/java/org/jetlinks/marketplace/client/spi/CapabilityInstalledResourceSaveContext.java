package org.jetlinks.marketplace.client.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.spi.CapabilityProvider;

import java.util.List;

/**
 * Provider 安装完成后由管理器按订阅创建的记录处理数据，不持有执行委托。
 * 旧记录仅包含本次覆盖目标，新记录来自本次 Provider 返回结果；记录管理完成后再执行保存后处理器。
 *
 * @author zhouhao
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#afterInstalled(CapabilityInstalledResourceSaveContext, CapabilityInstalledResourceSaveChain)
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
}
