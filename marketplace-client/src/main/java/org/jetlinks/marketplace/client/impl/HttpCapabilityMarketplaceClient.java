package org.jetlinks.marketplace.client.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hswebframework.web.crud.web.ResponseMessage;
import org.jetlinks.marketplace.CapabilityAvailability;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.CapabilityTag;
import org.jetlinks.marketplace.CapabilityTagClassifier;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.client.configuration.MarketplaceProperties;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 基于 WebClient 的市场 HTTP 客户端（约定 REST 路径，见类注释）.
 *
 * <ul>
 *   <li>{@code POST {base}/api/v1/marketplace/capabilities/_search} — body: {@link CapabilitySearchRequest}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}/availability}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}/versions}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/capabilities/{id}/versions/{version}/package}</li>
 *   <li>{@code POST {base}/api/v1/marketplace/capabilities/_check-updates} — body: {@code List<InstalledCapability>}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/tag-classifiers} — query: {@code type}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/tag-classifiers/{id}}</li>
 *   <li>{@code GET {base}/api/v1/marketplace/tags} — query: {@code classifierId}</li>
 * </ul>
 *
 * @author zhouhao
 * @since 2.12
 */
@RequiredArgsConstructor
public class HttpCapabilityMarketplaceClient implements CapabilityMarketplaceClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final WebClient webClient;

    public HttpCapabilityMarketplaceClient(WebClient.Builder builder, MarketplaceProperties properties) {
        this.webClient = builder
            .baseUrl(properties.getServerUrl())
            .defaultHeaders(headers -> {
                if (StringUtils.isNotBlank(properties.getSecureKey())) {
                    headers.setBearerAuth(properties.getSecureKey());
                }
            })
            .build();
    }

    @Override
    public Flux<CapabilityInfo> search(CapabilitySearchRequest request) {
        return exchangeToFlux(
            webClient
                .post()
                .uri("/marketplace/capabilities/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityInfo.class
        );
    }

    @Override
    public Mono<CapabilityInfo> getDetail(String capabilityId) {
        return exchangeToMono(
            webClient
                .get()
                .uri("/marketplace/capabilities/{id}", capabilityId)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityInfo.class
        );
    }

    @Override
    public Mono<CapabilityAvailability> checkAvailability(String capabilityId) {
        return exchangeToMono(
            webClient
                .get()
                .uri("/marketplace/capabilities/{id}/availability", capabilityId)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityAvailability.class
        );
    }

    @Override
    public Flux<CapabilityVersion> getVersions(String capabilityId) {
        return exchangeToFlux(
            webClient
                .get()
                .uri("/marketplace/capabilities/{id}/versions", capabilityId)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityVersion.class
        );
    }

    @Override
    public Mono<CapabilityPackage> download(String capabilityId, String version) {
        return exchangeToMono(
            webClient
                .get()
                .uri("/marketplace/capabilities/{id}/versions/{ver}/package", capabilityId, version)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityPackage.class
        );
    }

    @Override
    public Flux<CapabilityInfo> checkUpdates(List<InstalledCapability> installed) {
        return exchangeToFlux(
            webClient
                .post()
                .uri("/marketplace/capabilities/_check-updates")
                .accept(MediaType.APPLICATION_NDJSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(installed),
            CapabilityInfo.class
        );
    }

    @Override
    public Flux<CapabilityTagClassifier> getTagClassifiers(String type) {
        return exchangeToFlux(
            webClient
                .get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/marketplace/tag-classifiers");
                    if (type != null) {
                        uriBuilder.queryParam("type", type);
                    }
                    return uriBuilder.build();
                })
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityTagClassifier.class
        );
    }

    @Override
    public Mono<CapabilityTagClassifier> getTagClassifier(String id) {
        return exchangeToMono(
            webClient
                .get()
                .uri("/marketplace/tag-classifiers/{id}", id)
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityTagClassifier.class
        );
    }

    @Override
    public Flux<CapabilityTag> getTags(String classifierId) {
        return exchangeToFlux(
            webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/marketplace/tags")
                    .queryParam("classifierId", classifierId)
                    .build())
                .accept(MediaType.APPLICATION_NDJSON),
            CapabilityTag.class
        );
    }

    private <T> Mono<T> exchangeToMono(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
        return request.exchangeToMono(response -> {
            if (response.statusCode().isError()) {
                return createResponseException(response).flatMap(Mono::error);
            }
            return response.bodyToMono(type);
        });
    }

    private <T> Flux<T> exchangeToFlux(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
        return request.exchangeToFlux(response -> {
            if (response.statusCode().isError()) {
                return createResponseException(response).flatMapMany(Flux::error);
            }
            return response.bodyToFlux(type);
        });
    }

    private Mono<? extends Throwable> createResponseException(ClientResponse response) {
        HttpStatusCode statusCode = response.statusCode();
        return response
            .bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> new MarketplaceResponseException(statusCode, extractErrorMessage(body, statusCode)));
    }

    private String extractErrorMessage(String body, HttpStatusCode statusCode) {
        if (StringUtils.isBlank(body)) {
            return defaultStatusMessage(statusCode);
        }

        String message = extractJsonMessage(body);
        if (StringUtils.isNotBlank(message)) {
            return message;
        }

        return body.trim();
    }

    private String extractJsonMessage(String body) {
        String message = extractResponseMessage(body);
        if (StringUtils.isNotBlank(message)) {
            return message;
        }

        try {
            JsonNode json = OBJECT_MAPPER.readTree(body);
            if (json == null) {
                return null;
            }
            if (json.isTextual()) {
                return StringUtils.trimToNull(json.asText());
            }
            return firstJsonText(json, "message", "msg", "error_description", "detail", "error");
        } catch (Exception ignore) {
            return null;
        }
    }

    private String extractResponseMessage(String body) {
        try {
            ResponseMessage<?> message = OBJECT_MAPPER.readValue(body, ResponseMessage.class);
            return StringUtils.trimToNull(message.getMessage());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String firstJsonText(JsonNode json, String... fields) {
        for (String field : fields) {
            JsonNode value = json.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.isValueNode() ? value.asText() : value.toString();
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
        }
        return null;
    }

    private String defaultStatusMessage(HttpStatusCode statusCode) {
        if (statusCode instanceof HttpStatus httpStatus) {
            return httpStatus.getReasonPhrase();
        }
        return "HTTP " + statusCode.value();
    }

    private static class MarketplaceResponseException extends ResponseStatusException {

        private MarketplaceResponseException(HttpStatusCode statusCode, String reason) {
            super(statusCode, reason);
        }

        @Override
        public String getMessage() {
            return StringUtils.defaultIfBlank(getReason(), super.getMessage());
        }
    }
}
