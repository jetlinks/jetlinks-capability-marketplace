package org.jetlinks.marketplace.spi;

import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Mono;

/**
 * 能力生命周期监听.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityLifecycleListener {

    default Mono<Void> beforeInstall(CapabilityPackage pkg) {
        return Mono.empty();
    }

    default Mono<Void> afterInstall(InstalledCapability installed) {
        return Mono.empty();
    }

    default Mono<Void> beforeUninstall(String capabilityId) {
        return Mono.empty();
    }

    default Mono<Void> afterUninstall(String capabilityId) {
        return Mono.empty();
    }

    default Mono<Void> beforeUpgrade(CapabilityPackage pkg, InstalledCapability current) {
        return Mono.empty();
    }

    default Mono<Void> afterUpgrade(InstalledCapability upgraded) {
        return Mono.empty();
    }
}
