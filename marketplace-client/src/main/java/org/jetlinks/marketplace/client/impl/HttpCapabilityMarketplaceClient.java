package org.jetlinks.marketplace.client.impl;

import lombok.RequiredArgsConstructor;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.client.configuration.MarketplaceProperties;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 基于 WebClient 的市场 HTTP 客户端（约定 REST 路径，见类注释）.
 *
 * <ul>
 *   <li>{@code POST {base}/api/v1/marketplace/capabilities/_search} — body: {@link CapabilitySearchRequest}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}/versions}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}/versions/{version}/package}</li>
 *   <li>{@code POST {base}/api/v1/marketplace/capabilities/_check-updates} — body: {@code List<InstalledCapability>}}</li>
 * </ul>
 *
 * @author zhouhao
 * @since 2.12
 */
@RequiredArgsConstructor
public class HttpCapabilityMarketplaceClient implements CapabilityMarketplaceClient {

    private final WebClient webClient;

    public HttpCapabilityMarketplaceClient(WebClient.Builder builder, MarketplaceProperties properties) {
        this.webClient = builder
            .baseUrl(properties.getServerUrl())
            .defaultHeaders(headers -> headers.setBearerAuth(properties.getSecureKey()))
            .build();
    }

    @Override
    public Flux<CapabilityInfo> search(CapabilitySearchRequest request) {
        return webClient
            .post()
            .uri("/marketplace/capabilities/_search")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .accept(MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToFlux(CapabilityInfo.class);
    }

    @Override
    public Mono<CapabilityInfo> getDetail(String capabilityId) {
        return webClient
            .get()
            .uri("/marketplace/capabilities/{id}", capabilityId)
            .accept(MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToMono(CapabilityInfo.class);
    }

    @Override
    public Flux<CapabilityVersion> getVersions(String capabilityId) {
        return webClient
            .get()
            .uri("/marketplace/capabilities/{id}/versions", capabilityId)
            .accept(MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToFlux(CapabilityVersion.class);
    }

    @Override
    public Mono<CapabilityPackage> download(String capabilityId, String version) {
        return webClient
            .get()
            .uri("/marketplace/capabilities/{id}/versions/{ver}/package", capabilityId, version)
            .accept(MediaType.APPLICATION_NDJSON)
            .retrieve()
            .bodyToMono(CapabilityPackage.class);
    }

    @Override
    public Flux<CapabilityInfo> checkUpdates(List<InstalledCapability> installed) {
        return webClient
            .post()
            .uri("/marketplace/capabilities/_check-updates")
            .accept(MediaType.APPLICATION_NDJSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(installed)
            .retrieve()
            .bodyToFlux(CapabilityInfo.class);
    }
}
