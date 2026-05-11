package org.jetlinks.marketplace.client.web;

import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceClientResourceControllerTest {

    @Test
    void shouldDelegateResourceOperations() {
        CapabilityResourceManager resourceManager = mock(CapabilityResourceManager.class);
        MarketplaceClientResourceController controller = new MarketplaceClientResourceController(resourceManager);

        InstalledResource installedResource = new InstalledResource();
        installedResource.setCapabilityId("cap-1");

        ProgressState<InstalledResource> progress = new ProgressState<>();
        progress.setData(installedResource);
        progress.setType(ProgressState.Type.progress);

        when(resourceManager.install(eq("cap-1"), eq("1.0.0"), anyMap())).thenReturn(Flux.just(progress));
        when(resourceManager.upgrade(eq("cap-1"), eq("1.0.1"), anyMap())).thenReturn(Flux.just(progress));
        when(resourceManager.listInstalledResources(eq("device"), anyCollection())).thenReturn(Flux.just(installedResource));
        when(resourceManager.listInstalledResources(eq("device"), eq("cap-1"), anyCollection())).thenReturn(Flux.just(installedResource));

        assertEquals(List.of(progress), controller.install("cap-1", "1.0.0", Mono.just(Map.of("key", "value"))).collectList().block());
        assertEquals(List.of(progress), controller.upgrade("cap-1", "1.0.1", Mono.just(Map.of("key", "value"))).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", Mono.just(Collections.singletonList("data-1"))).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", "cap-1", Mono.just(Collections.singletonList("data-1"))).collectList().block());

        verify(resourceManager).install(eq("cap-1"), eq("1.0.0"), anyMap());
        verify(resourceManager).upgrade(eq("cap-1"), eq("1.0.1"), anyMap());
        verify(resourceManager).listInstalledResources(eq("device"), anyCollection());
        verify(resourceManager).listInstalledResources(eq("device"), eq("cap-1"), anyCollection());
    }
}
