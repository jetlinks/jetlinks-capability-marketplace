package org.jetlinks.marketplace.client.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

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

    private HttpCapabilityMarketplaceClient createClient(ClientResponse response) {
        WebClient webClient = WebClient.builder()
                                       .exchangeFunction(request -> Mono.just(response))
                                       .build();
        return new HttpCapabilityMarketplaceClient(webClient);
    }
}
