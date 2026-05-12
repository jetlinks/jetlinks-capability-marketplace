package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityOperationContext;
import org.jetlinks.marketplace.CapabilityOperationEvent;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultCapabilityResourceManagerTest {

    private static final String PROVIDER_ID = "test-installed-resource-filter";

    @AfterEach
    void tearDown() {
        CapabilityProviders.unregister(PROVIDER_ID);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldFilterInstallResourcesAndDeleteOnlyVisibleRecords() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> loadQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> deleteQuery = mock(ReactiveQuery.class);

        CapabilityResourceInstallEntity visible = installEntity("binding-visible", "cap-1", "tool", "old-visible", "tenant-visible");
        CapabilityResourceInstallEntity invisible = installEntity("binding-invisible", "cap-1", "tool", "old-invisible", "tenant-invisible");

        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(loadQuery, deleteQuery);
        when(loadQuery.fetch()).thenReturn(Flux.just(visible, invisible));
        when(deleteQuery.fetch()).thenReturn(Flux.just(visible, invisible));
        when(repository.deleteById(any(Collection.class))).thenReturn(Mono.just(1));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityInstalledResourceInterceptor tenantVisibleOnly = (context, resources) -> resources
            .filter(entity -> "tenant-visible".equals(entity.getDataId()));

        CapabilityProviders.register(provider(context -> context
            .loadInstallResources()
            .collectList()
            .flatMapMany(resources -> {
                assertEquals(List.of("old-visible"), resources
                    .stream()
                    .map(InstalledResource::getResourceId)
                    .toList());
                return Flux.just(resource("tool", "new-visible", "tenant-visible"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(
            client,
            repository,
            List.of(tenantVisibleOnly));

        assertEquals(2, manager
            .install("cap-1", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5))
            .size());

        ArgumentCaptor<Collection<String>> deleteIds = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteById(deleteIds.capture());
        assertEquals(List.of("binding-visible"), deleteIds.getValue().stream().toList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReportInstallEventsWithSameOperationId() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> query = mock(ReactiveQuery.class);
        AtomicReference<String> providerOperationId = new AtomicReference<>();

        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(query);
        when(query.fetch()).thenReturn(Flux.empty());
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> CapabilityOperationContext
            .current()
            .flatMapMany(operationContext -> {
                providerOperationId.set(operationContext.getId());
                return Flux.just(resource("tool", "new-visible", "tenant-visible"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .install("cap-1", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        ArgumentCaptor<CapabilityOperationEvent> eventCaptor = ArgumentCaptor.forClass(CapabilityOperationEvent.class);
        verify(client, atLeast(1)).reportOperationEvent(eventCaptor.capture());

        List<CapabilityOperationEvent> events = eventCaptor.getAllValues();
        List<String> operationIds = events
            .stream()
            .map(CapabilityOperationEvent::getOperationId)
            .distinct()
            .collect(Collectors.toList());

        assertEquals(1, operationIds.size());
        assertEquals(operationIds.get(0), providerOperationId.get());
        EnumSet<CapabilityOperationEvent.Type> types = events
            .stream()
            .map(CapabilityOperationEvent::getType)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(CapabilityOperationEvent.Type.class)));

        assertTrue(types.contains(CapabilityOperationEvent.Type.download));
        assertTrue(types.contains(CapabilityOperationEvent.Type.installing));
        assertTrue(types.contains(CapabilityOperationEvent.Type.progress));
        assertTrue(types.contains(CapabilityOperationEvent.Type.success));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldIgnoreReportFailureWhenInstalling() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> query = mock(ReactiveQuery.class);

        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.error(new RuntimeException("report failed")));
        when(repository.createQuery()).thenReturn(query);
        when(query.fetch()).thenReturn(Flux.empty());
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> Flux.just(resource("tool", "new-visible", "tenant-visible"))));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .install("cap-1", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        verify(repository).save(any(Collection.class));
    }

    @Test
    void shouldCheckInstalledStateAfterInterceptors() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> query = mock(ReactiveQuery.class);

        when(repository.createQuery()).thenReturn(query);
        when(query.fetch()).thenReturn(Flux.just(
            installEntity("binding-invisible", "cap-1", "tool", "old-invisible", "tenant-invisible")));

        CapabilityInstalledResourceInterceptor tenantVisibleOnly = (context, resources) -> resources
            .filter(entity -> "tenant-visible".equals(entity.getDataId()));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(
            client,
            repository,
            List.of(tenantVisibleOnly));

        assertFalse(manager.isInstalled("cap-1").block(Duration.ofSeconds(5)));
    }

    @Test
    void shouldFilterListedInstalledResources() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> query = mock(ReactiveQuery.class);

        CapabilityResourceInstallEntity visible = installEntity("binding-visible", "cap-1", "tool", "resource-1", "tenant-visible");
        CapabilityResourceInstallEntity invisible = installEntity("binding-invisible", "cap-1", "tool", "resource-2", "tenant-invisible");

        when(repository.createQuery()).thenReturn(query);
        when(query.fetch()).thenReturn(Flux.just(visible, invisible));

        CapabilityInstalledResourceInterceptor tenantVisibleOnly = (context, resources) -> resources
            .filter(entity -> "tenant-visible".equals(entity.getDataId()));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(
            client,
            repository,
            List.of(tenantVisibleOnly));

        List<InstalledResource> resources = manager
            .listInstalledResources("tool", List.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("resource-1"), resources
            .stream()
            .map(InstalledResource::getResourceId)
            .toList());
    }

    private CapabilityProvider provider(ProviderInstaller installer) {
        return new CapabilityProvider() {
            @Override
            public String getId() {
                return PROVIDER_ID;
            }

            @Override
            public String getName() {
                return "test";
            }

            @Override
            public Flux<InstalledResource> install(CapabilityContext context) {
                return installer.install(context);
            }
        };
    }

    private CapabilityPackage packageFor(String capabilityId) {
        CapabilityInfo info = new CapabilityInfo();
        info.setId(capabilityId);
        info.setProvider(PROVIDER_ID);

        CapabilityPackage pkg = new CapabilityPackage();
        pkg.setInfo(info);
        pkg.setVersion("1.0.0");
        return pkg;
    }

    private CapabilityResourceInstallEntity installEntity(String id,
                                                          String capabilityId,
                                                          String type,
                                                          String resourceId,
                                                          String dataId) {
        CapabilityResourceInstallEntity entity = new CapabilityResourceInstallEntity();
        entity.setId(id);
        entity.setCapabilityId(capabilityId);
        entity.setType(type);
        entity.setResourceId(resourceId);
        entity.setDataId(dataId);
        return entity;
    }

    private InstalledResource resource(String type, String resourceId, String dataId) {
        InstalledResource resource = new InstalledResource();
        resource.setType(type);
        resource.setResourceId(resourceId);
        resource.setDataId(dataId);
        return resource;
    }

    interface ProviderInstaller {
        Flux<InstalledResource> install(CapabilityProvider.CapabilityContext context);
    }
}
