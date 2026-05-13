package org.jetlinks.marketplace.client;

import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

/**
 * 能力安装与升级编排入口.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityResourceManager {

    Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                   String version,
                                                   Map<String, Object> configuration);

    default Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                           String version,
                                                           CapabilityInstallRequest request) {
        return install(capabilityId, version, request == null ? Map.of() : request.getConfiguration());
    }

    Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                   String targetVersion,
                                                   Map<String, Object> configuration);

    default Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                           String targetVersion,
                                                           CapabilityInstallRequest request) {
        return upgrade(capabilityId, targetVersion, request == null ? Map.of() : request.getConfiguration());
    }

    Mono<Boolean> isInstalled(String capabilityId);

    Flux<InstalledResource> listInstalledResources(String type, Collection<String> dataId);

    Flux<InstalledResource> listInstalledResources(String type,String capId, Collection<String> resourceId);


}
