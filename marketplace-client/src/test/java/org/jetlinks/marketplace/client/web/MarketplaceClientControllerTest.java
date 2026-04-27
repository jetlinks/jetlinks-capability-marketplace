package org.jetlinks.marketplace.client.web;

import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceClientControllerTest {

    @Test
    void shouldDelegateMarketplaceOperations() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        CapabilityResourceManager resourceManager = mock(CapabilityResourceManager.class);
        MarketplaceClientController controller = new MarketplaceClientController(client, resourceManager);

        CapabilitySearchRequest searchRequest = new CapabilitySearchRequest();
        searchRequest.setKeyword("demo");

        CapabilityInfo info = new CapabilityInfo();
        info.setId("cap-1");

        CapabilityAvailability availability = new CapabilityAvailability();
        availability.setCapabilityId("cap-1");
        availability.setAvailable(true);

        CapabilityVersion version = new CapabilityVersion();
        version.setVersion("1.0.0");

        CapabilityTagClassifier classifier = new CapabilityTagClassifier();
        classifier.setId("classifier-1");

        CapabilityTag tag = new CapabilityTag();
        tag.setId("tag-1");

        InstalledCapability installedCapability = new InstalledCapability();
        installedCapability.setCapabilityId("cap-1");

        InstalledResource installedResource = new InstalledResource();
        installedResource.setCapabilityId("cap-1");

        ProgressState<InstalledResource> progress = new ProgressState<>();
        progress.setData(installedResource);
        progress.setType(ProgressState.Type.progress);

        when(client.search(searchRequest)).thenReturn(Flux.just(info));
        when(client.getDetail("cap-1")).thenReturn(Mono.just(info));
        when(client.checkAvailability("cap-1")).thenReturn(Mono.just(availability));
        when(client.getVersions("cap-1")).thenReturn(Flux.just(version));
        when(client.checkUpdates(List.of(installedCapability))).thenReturn(Flux.just(info));
        when(client.getTagClassifiers("plugin")).thenReturn(Flux.just(classifier));
        when(client.getTagClassifier("classifier-1")).thenReturn(Mono.just(classifier));
        when(client.getTags("classifier-1")).thenReturn(Flux.just(tag));
        when(resourceManager.install(eq("cap-1"), eq("1.0.0"), anyMap())).thenReturn(Flux.just(progress));
        when(resourceManager.upgrade(eq("cap-1"), eq("1.0.1"), anyMap())).thenReturn(Flux.just(progress));
        when(resourceManager.listInstalledResources(eq("device"), anyCollection())).thenReturn(Flux.just(installedResource));
        when(resourceManager.listInstalledResources(eq("device"), eq("cap-1"), anyCollection())).thenReturn(Flux.just(installedResource));

        assertEquals(List.of(info), controller.search(searchRequest).collectList().block());
        assertEquals(info, controller.getDetail("cap-1").block());
        assertEquals(availability, controller.checkAvailability("cap-1").block());
        assertEquals(List.of(version), controller.getVersions("cap-1").collectList().block());
        assertEquals(List.of(info), controller.checkUpdates(List.of(installedCapability)).collectList().block());
        assertEquals(List.of(classifier), controller.getTagClassifiers("plugin").collectList().block());
        assertEquals(classifier, controller.getTagClassifier("classifier-1").block());
        assertEquals(List.of(tag), controller.getTags("classifier-1").collectList().block());
        assertEquals(List.of(progress), controller.install("cap-1", "1.0.0", Mono.just(Map.of("key", "value"))).collectList().block());
        assertEquals(List.of(progress), controller.upgrade("cap-1", "1.0.1", Mono.just(Map.of("key", "value"))).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", Mono.just(Collections.singletonList("data-1"))).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", "cap-1", Mono.just(Collections.singletonList("data-1"))).collectList().block());

        verify(client).search(searchRequest);
        verify(client).getDetail("cap-1");
        verify(client).checkAvailability("cap-1");
        verify(client).getVersions("cap-1");
        verify(client).checkUpdates(List.of(installedCapability));
        verify(client).getTagClassifiers("plugin");
        verify(client).getTagClassifier("classifier-1");
        verify(client).getTags("classifier-1");
        verify(resourceManager).install(eq("cap-1"), eq("1.0.0"), anyMap());
        verify(resourceManager).upgrade(eq("cap-1"), eq("1.0.1"), anyMap());
        verify(resourceManager).listInstalledResources(eq("device"), anyCollection());
        verify(resourceManager).listInstalledResources(eq("device"), eq("cap-1"), anyCollection());
    }
}
