package org.jetlinks.marketplace.client.impl;

import org.jetlinks.marketplace.CapabilityAvailability;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.CapabilityTag;
import org.jetlinks.marketplace.CapabilityTagClassifier;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.client.configuration.MarketplaceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpCapabilityMarketplaceClientTest {

    @Test
    void shouldUseResponseMessageAsExceptionMessage() {
        HttpCapabilityMarketplaceClient client = createClient(
            ClientResponse.create(HttpStatus.UNAUTHORIZED)
                          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                          .body("""
                                    {"message":"invalid secure key","status":401,"code":"error"}
                                    """)
                          .build()
        );

        assertThatThrownBy(() -> client.getDetail("test").block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(exception.getReason()).isEqualTo("invalid secure key");
                assertThat(exception.getMessage()).isEqualTo("invalid secure key");
            });
    }

    @Test
    void shouldFallbackToPlainTextBodyAsExceptionMessage() {
        HttpCapabilityMarketplaceClient client = createClient(
            ClientResponse.create(HttpStatus.BAD_REQUEST)
                          .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                          .body("capability id is required")
                          .build()
        );

        assertThatThrownBy(() -> client.getDetail("test").block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("capability id is required");
                assertThat(exception.getMessage()).isEqualTo("capability id is required");
            });
    }

    @Test
    void shouldRequestAvailabilityEndpointAndParseBody() {
        AtomicReference<ClientRequest> requestRef = new AtomicReference<>();
        HttpCapabilityMarketplaceClient client = createClient(request -> {
            requestRef.set(request);
            return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                              .body("""
                                    {
                                      "capabilityId":"cap-1",
                                      "available":false,
                                      "reasonCode":"error.marketplace.capability.purchase_required",
                                      "reason":"need purchase",
                                      "purchaseUrl":"/marketplace/capabilities/cap-1/orders"
                                    }
                                    """)
                              .build()
            );
        });

        CapabilityAvailability availability = client.checkAvailability("cap-1").block();

        assertThat(requestRef.get()).isNotNull();
        assertThat(requestRef.get().url().getPath()).isEqualTo("/marketplace/capabilities/cap-1/availability");
        assertThat(availability).isNotNull();
        assertThat(availability.getCapabilityId()).isEqualTo("cap-1");
        assertThat(availability.isAvailable()).isFalse();
        assertThat(availability.getReasonCode()).isEqualTo(CapabilityAvailability.REASON_PURCHASE_REQUIRED);
        assertThat(availability.getPurchaseUrl()).isEqualTo("/marketplace/capabilities/cap-1/orders");
    }

    @Test
    void shouldUseBuilderPropertiesAndParseMarketplaceResponses() {
        List<ClientRequest> requests = new ArrayList<>();
        ArrayDeque<ClientResponse> responses = new ArrayDeque<>(List.of(
            ndjsonResponse("""
                           {"id":"cap-1","name":"Capability 1"}
                           {"id":"cap-2","name":"Capability 2"}
                           """),
            ndjsonResponse("""
                           {"version":"1.0.0","available":true}
                           """),
            jsonResponse("""
                         {"version":"1.0.0","resources":[{"id":"res-1","name":"Resource 1"}]}
                         """),
            ndjsonResponse("""
                           {"id":"cap-3","name":"Capability 3"}
                           """),
            ndjsonResponse("""
                           {"id":"classifier-1","name":"Plugin"}
                           """),
            ndjsonResponse("""
                           {"id":"classifier-2","name":"All"}
                           """),
            jsonResponse("""
                         {"id":"classifier-3","name":"Detail"}
                         """),
            ndjsonResponse("""
                           {"id":"tag-1","name":"Tag 1","categoryId":"classifier-3"}
                           """)
        ));

        MarketplaceProperties properties = new MarketplaceProperties();
        properties.setServerUrl("https://marketplace.test");
        properties.setSecureKey("secure-token");

        HttpCapabilityMarketplaceClient client = new HttpCapabilityMarketplaceClient(
            WebClient
                .builder()
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(responses.removeFirst());
                }),
            properties
        );

        CapabilitySearchRequest searchRequest = new CapabilitySearchRequest();
        searchRequest.setKeyword("demo");
        List<CapabilityInfo> searched = client.search(searchRequest).collectList().block();
        List<CapabilityVersion> versions = client.getVersions("cap-1").collectList().block();
        CapabilityPackage capabilityPackage = client.download("cap-1", "1.0.0").block();

        InstalledCapability installed = new InstalledCapability();
        installed.setCapabilityId("cap-1");
        List<CapabilityInfo> updates = client.checkUpdates(List.of(installed)).collectList().block();
        List<CapabilityTagClassifier> typedClassifiers = client.getTagClassifiers("plugin").collectList().block();
        List<CapabilityTagClassifier> allClassifiers = client.getTagClassifiers(null).collectList().block();
        CapabilityTagClassifier classifierDetail = client.getTagClassifier("classifier-3").block();
        List<CapabilityTag> tags = client.getTags("classifier-3").collectList().block();

        assertThat(searched).hasSize(2);
        assertThat(searched.get(0).getId()).isEqualTo("cap-1");
        assertThat(searched.get(1).getId()).isEqualTo("cap-2");

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo("1.0.0");
        assertThat(versions.get(0).isAvailable()).isTrue();

        assertThat(capabilityPackage).isNotNull();
        assertThat(capabilityPackage.getVersion()).isEqualTo("1.0.0");
        assertThat(capabilityPackage.getResources()).hasSize(1);
        assertThat(capabilityPackage.getResources().get(0).getId()).isEqualTo("res-1");

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).getId()).isEqualTo("cap-3");

        assertThat(typedClassifiers).hasSize(1);
        assertThat(typedClassifiers.get(0).getId()).isEqualTo("classifier-1");

        assertThat(allClassifiers).hasSize(1);
        assertThat(allClassifiers.get(0).getId()).isEqualTo("classifier-2");

        assertThat(classifierDetail).isNotNull();
        assertThat(classifierDetail.getId()).isEqualTo("classifier-3");

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).getId()).isEqualTo("tag-1");
        assertThat(tags.get(0).getCategoryId()).isEqualTo("classifier-3");

        assertThat(requests).hasSize(8);
        assertThat(requests.get(0).method().name()).isEqualTo("POST");
        assertThat(requests.get(0).url().toString()).isEqualTo("https://marketplace.test/marketplace/capabilities/_search");
        assertThat(requests.get(0).headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer secure-token");
        assertThat(requests.get(0).headers().getFirst(HttpHeaders.CONTENT_TYPE)).contains(MediaType.APPLICATION_JSON_VALUE);

        assertThat(requests.get(1).url().toString()).isEqualTo("https://marketplace.test/marketplace/capabilities/cap-1/versions");
        assertThat(requests.get(2).url().toString()).isEqualTo("https://marketplace.test/marketplace/capabilities/cap-1/versions/1.0.0/package");
        assertThat(requests.get(3).url().toString()).isEqualTo("https://marketplace.test/marketplace/capabilities/_check-updates");
        assertThat(requests.get(4).url().toString()).isEqualTo("https://marketplace.test/marketplace/tag-classifiers?type=plugin");
        assertThat(requests.get(5).url().toString()).isEqualTo("https://marketplace.test/marketplace/tag-classifiers");
        assertThat(requests.get(6).url().toString()).isEqualTo("https://marketplace.test/marketplace/tag-classifiers/classifier-3");
        assertThat(requests.get(7).url().toString()).isEqualTo("https://marketplace.test/marketplace/tags?classifierId=classifier-3");
    }

    @Test
    void shouldUseJsonFieldAndTextBodyAsExceptionMessage() {
        HttpCapabilityMarketplaceClient fieldClient = createClient(
            ClientResponse.create(HttpStatus.BAD_REQUEST)
                          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                          .body("""
                                {"msg":"capability id is invalid"}
                                """)
                          .build()
        );

        assertThatThrownBy(() -> fieldClient.getVersions("cap-1").collectList().block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("capability id is invalid");
            });

        HttpCapabilityMarketplaceClient textClient = createClient(
            ClientResponse.create(HttpStatus.BAD_GATEWAY)
                          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                          .body("""
                                "upstream timeout"
                                """)
                          .build()
        );

        assertThatThrownBy(() -> textClient.download("cap-1", "1.0.0").block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                assertThat(exception.getReason()).isEqualTo("upstream timeout");
            });
    }

    @Test
    void shouldFallbackToDefaultStatusMessageWhenBodyIsEmpty() {
        HttpCapabilityMarketplaceClient client = createClient(
            ClientResponse.create(HttpStatusCode.valueOf(499))
                          .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                          .build()
        );

        assertThatThrownBy(() -> client.getTags("classifier-1").collectList().block())
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                ResponseStatusException exception = (ResponseStatusException) error;
                assertThat(exception.getStatusCode().value()).isEqualTo(499);
                assertThat(exception.getReason()).isEqualTo("HTTP 499");
                assertThat(exception.getMessage()).isEqualTo("HTTP 499");
            });
    }

    private HttpCapabilityMarketplaceClient createClient(ClientResponse response) {
        return createClient(ignore -> Mono.just(response));
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                             .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                             .body(body)
                             .build();
    }

    private ClientResponse ndjsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                             .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_NDJSON_VALUE)
                             .body(body)
                             .build();
    }

    private HttpCapabilityMarketplaceClient createClient(java.util.function.Function<ClientRequest, Mono<ClientResponse>> exchangeFunction) {
        WebClient webClient = WebClient.builder()
                                       .exchangeFunction(request -> exchangeFunction.apply(request))
                                       .build();
        return new HttpCapabilityMarketplaceClient(webClient);
    }
}
