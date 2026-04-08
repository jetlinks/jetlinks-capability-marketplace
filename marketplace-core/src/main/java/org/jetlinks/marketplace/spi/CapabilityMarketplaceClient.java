package org.jetlinks.marketplace.spi;

import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 远程能力市场客户端.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityMarketplaceClient {

    Flux<CapabilityInfo> search(CapabilitySearchRequest request);

    Mono<CapabilityInfo> getDetail(String capabilityId);

    Flux<CapabilityVersion> getVersions(String capabilityId);

    Mono<CapabilityPackage> download(String capabilityId, String version);

    Flux<CapabilityInfo> checkUpdates(List<InstalledCapability> installed);
}
