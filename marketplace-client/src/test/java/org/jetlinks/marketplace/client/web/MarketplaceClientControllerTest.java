package org.jetlinks.marketplace.client.web;

import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceClientControllerTest {

    @Test
    void shouldDelegateMarketplaceOperations() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        MarketplaceClientController controller = new MarketplaceClientController(client);

        CapabilitySearchRequest searchRequest = new CapabilitySearchRequest();
        searchRequest.setKeyword("demo");

        CapabilityInfo info = new CapabilityInfo();
        info.setId("cap-1");

        CapabilityAvailability availability = new CapabilityAvailability();
        availability.setCapabilityId("cap-1");
        availability.setAvailable(true);

        CapabilityVersion version = new CapabilityVersion();
        version.setVersion("1.0.0");

        CapabilityPackage capabilityPackage = new CapabilityPackage();
        capabilityPackage.setVersion("1.0.0");

        CapabilityTagClassifier classifier = new CapabilityTagClassifier();
        classifier.setId("classifier-1");

        CapabilityTag tag = new CapabilityTag();
        tag.setId("tag-1");

        InstalledCapability installedCapability = new InstalledCapability();
        installedCapability.setCapabilityId("cap-1");

        when(client.search(searchRequest)).thenReturn(Flux.just(info));
        when(client.getDetail("cap-1")).thenReturn(Mono.just(info));
        when(client.checkAvailability("cap-1")).thenReturn(Mono.just(availability));
        when(client.getVersions("cap-1")).thenReturn(Flux.just(version));
        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(capabilityPackage));
        when(client.checkUpdates(List.of(installedCapability))).thenReturn(Flux.just(info));
        when(client.getTagClassifiers("plugin")).thenReturn(Flux.just(classifier));
        when(client.getTagClassifier("classifier-1")).thenReturn(Mono.just(classifier));
        when(client.getTags("classifier-1")).thenReturn(Flux.just(tag));

        assertEquals(List.of(info), controller.search(searchRequest).collectList().block());
        assertEquals(info, controller.getDetail("cap-1").block());
        assertEquals(availability, controller.checkAvailability("cap-1").block());
        assertEquals(List.of(version), controller.getVersions("cap-1").collectList().block());
        assertEquals(capabilityPackage, controller.download("cap-1", "1.0.0").block());
        assertEquals(List.of(info), controller.checkUpdates(List.of(installedCapability)).collectList().block());
        assertEquals(List.of(classifier), controller.getTagClassifiers("plugin").collectList().block());
        assertEquals(classifier, controller.getTagClassifier("classifier-1").block());
        assertEquals(List.of(tag), controller.getTags("classifier-1").collectList().block());

        verify(client).search(searchRequest);
        verify(client).getDetail("cap-1");
        verify(client).checkAvailability("cap-1");
        verify(client).getVersions("cap-1");
        verify(client).download("cap-1", "1.0.0");
        verify(client).checkUpdates(List.of(installedCapability));
        verify(client).getTagClassifiers("plugin");
        verify(client).getTagClassifier("classifier-1");
        verify(client).getTags("classifier-1");
    }
}
