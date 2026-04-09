package org.jetlinks.marketplace.client;

import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import reactor.core.publisher.Flux;

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

    Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                     String targetVersion,
                                                     Map<String, Object> configuration);

    Flux<InstalledResource> listInstalledResources(String type, Collection<String> dataId);


}
