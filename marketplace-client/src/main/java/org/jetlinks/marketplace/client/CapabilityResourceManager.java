package org.jetlinks.marketplace.client;

import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.ProgressState;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 能力安装与升级编排入口.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityResourceManager {

    Flux<ProgressState<InstalledCapability>> install(String capabilityId,
                                                     String version,
                                                     Map<String, Object> configuration);

    Flux<ProgressState<InstalledCapability>> upgrade(String capabilityId,
                                                     String targetVersion,
                                                     Map<String, Object> configuration);

}
