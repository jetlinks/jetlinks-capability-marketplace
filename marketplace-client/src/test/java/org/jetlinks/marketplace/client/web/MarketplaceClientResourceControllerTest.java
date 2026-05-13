package org.jetlinks.marketplace.client.web;

import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceClientResourceControllerTest {

    @Test
    void shouldDelegateResourceOperationsWithTypedRequestPayloads() {
        CapabilityResourceManager resourceManager = mock(CapabilityResourceManager.class);
        MarketplaceClientResourceController controller = new MarketplaceClientResourceController(resourceManager);

        InstalledResource installedResource = new InstalledResource();
        installedResource.setCapabilityId("cap-1");

        ProgressState<InstalledResource> progress = new ProgressState<>();
        progress.setData(installedResource);
        progress.setType(ProgressState.Type.progress);

        when(resourceManager.install(eq("cap-1"), eq("1.0.0"), any(CapabilityInstallRequest.class))).thenReturn(Flux.just(progress));
        when(resourceManager.upgrade(eq("cap-1"), eq("1.0.1"), any(CapabilityInstallRequest.class))).thenReturn(Flux.just(progress));
        when(resourceManager.listInstalledResources(eq("device"), anyCollection())).thenReturn(Flux.just(installedResource));
        when(resourceManager.listInstalledResources(eq("device"), eq("cap-1"), anyCollection())).thenReturn(Flux.just(installedResource));

        CapabilityInstallRequest installRequest = CapabilityInstallRequest.ofConfiguration(java.util.Map.of("key", "value"));
        CapabilityInstallRequest upgradeRequest = CapabilityInstallRequest.ofConfiguration(java.util.Map.of("key", "value"));
        CapabilityInstallRequest.UpgradeOptions upgradeOptions = new CapabilityInstallRequest.UpgradeOptions();
        upgradeOptions.setTargetDataIds(List.of("data-1"));
        upgradeOptions.setRemoveAbsentResources(false);
        upgradeRequest.setUpgrade(upgradeOptions);

        assertEquals(List.of(progress), controller.install("cap-1", "1.0.0", Mono.just(installRequest)).collectList().block());
        assertEquals(List.of(progress), controller.install("cap-1", "1.0.0", Mono.empty()).collectList().block());
        assertEquals(List.of(progress), controller.upgrade("cap-1", "1.0.1", Mono.just(upgradeRequest)).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", Mono.just(Collections.singletonList("data-1"))).collectList().block());
        assertEquals(List.of(installedResource), controller.listInstalled("device", "cap-1", Mono.just(Collections.singletonList("data-1"))).collectList().block());

        ArgumentCaptor<CapabilityInstallRequest> installCaptor = ArgumentCaptor.forClass(CapabilityInstallRequest.class);
        ArgumentCaptor<CapabilityInstallRequest> upgradeCaptor = ArgumentCaptor.forClass(CapabilityInstallRequest.class);

        verify(resourceManager, times(2)).install(eq("cap-1"), eq("1.0.0"), installCaptor.capture());
        verify(resourceManager).upgrade(eq("cap-1"), eq("1.0.1"), upgradeCaptor.capture());
        verify(resourceManager).listInstalledResources(eq("device"), anyCollection());
        verify(resourceManager).listInstalledResources(eq("device"), eq("cap-1"), anyCollection());

        assertEquals("value", installCaptor.getAllValues().get(0).getConfiguration().get("key"));
        assertEquals(List.of(), installCaptor.getAllValues().get(0).getEffectiveUpgrade().getTargetDataIds());
        assertEquals(java.util.Map.of(), installCaptor.getAllValues().get(1).getConfiguration());
        assertEquals(List.of(), installCaptor.getAllValues().get(1).getEffectiveUpgrade().getTargetDataIds());

        assertEquals("value", upgradeCaptor.getValue().getConfiguration().get("key"));
        assertEquals(List.of("data-1"), upgradeCaptor.getValue().getEffectiveUpgrade().getTargetDataIds());
        assertEquals(false, upgradeCaptor.getValue().getEffectiveUpgrade().isRemoveAbsentResources());
    }
}
