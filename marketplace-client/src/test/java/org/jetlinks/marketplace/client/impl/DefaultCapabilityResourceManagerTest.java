package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.marketplace.CapabilityInstallRequest;
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
import static org.mockito.Mockito.never;
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
    void shouldIgnoreInstalledResourcesWhenInstallingAndKeepOldBindingsUntouched() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);

        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityInstalledResourceInterceptor tenantVisibleOnly = (context, resources) -> resources
            .filter(entity -> "tenant-visible".equals(entity.getDataId()));

        CapabilityProviders.register(provider(context -> context
            .loadInstallResources()
            .collectList()
            .flatMapMany(resources -> {
                assertEquals(List.of(), resources);
                return Flux.just(resource("tool", "new-visible", "tenant-new"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(
            client,
            repository,
            List.of(tenantVisibleOnly));

        manager
            .install("cap-1", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        ArgumentCaptor<Collection<CapabilityResourceInstallEntity>> savedBindings = ArgumentCaptor.forClass(Collection.class);
        verify(repository).save(savedBindings.capture());
        assertEquals(List.of("tenant-new"), savedBindings.getValue()
            .stream()
            .map(CapabilityResourceInstallEntity::getDataId)
            .toList());

        verify(repository, never()).deleteById(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReuseVisibleInstalledResourcesWhenUpgrading() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> loadQuery = mock(ReactiveQuery.class);

        CapabilityResourceInstallEntity visible = installEntity("binding-visible", "cap-1", "tool", "old-visible", "tenant-visible");
        CapabilityResourceInstallEntity invisible = installEntity("binding-invisible", "cap-1", "tool", "old-invisible", "tenant-invisible");

        when(client.download("cap-1", "1.0.1")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(loadQuery);
        when(loadQuery.fetch()).thenReturn(Flux.just(visible, invisible));
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
                return Flux.just(resource("tool", "new-visible", resources.get(0).getDataId()));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(
            client,
            repository,
            List.of(tenantVisibleOnly));

        manager
            .upgrade("cap-1", "1.0.1", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        ArgumentCaptor<Collection<CapabilityResourceInstallEntity>> savedBindings = ArgumentCaptor.forClass(Collection.class);
        verify(repository).save(savedBindings.capture());
        assertEquals(List.of("tenant-visible"), savedBindings.getValue()
            .stream()
            .map(CapabilityResourceInstallEntity::getDataId)
            .toList());

        ArgumentCaptor<Collection<String>> deleteIds = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteById(deleteIds.capture());
        assertEquals(List.of("binding-visible"), deleteIds.getValue().stream().toList());

    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldFilterUpgradeTargetsByTargetDataIds() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> loadQuery = mock(ReactiveQuery.class);

        CapabilityResourceInstallEntity left = installEntity("binding-left", "cap-1", "tool", "old-left", "tenant-left");
        CapabilityResourceInstallEntity right = installEntity("binding-right", "cap-1", "tool", "old-right", "tenant-right");

        when(client.download("cap-1", "1.0.1")).thenReturn(Mono.just(packageFor("cap-1")));
        when(repository.createQuery()).thenReturn(loadQuery);
        when(loadQuery.fetch()).thenReturn(Flux.just(left, right));
        when(repository.deleteById(any(Collection.class))).thenReturn(Mono.just(1));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> context
            .loadInstallResources()
            .collectList()
            .flatMapMany(resources -> {
                assertEquals(List.of("tenant-right"), resources.stream().map(InstalledResource::getDataId).toList());
                return Flux.just(resource("tool", "new-right", "tenant-right"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        CapabilityInstallRequest request = new CapabilityInstallRequest();
        CapabilityInstallRequest.UpgradeOptions upgrade = new CapabilityInstallRequest.UpgradeOptions();
        upgrade.setTargetDataIds(List.of("tenant-right"));
        request.setUpgrade(upgrade);

        manager
            .upgrade("cap-1", "1.0.1", request)
            .collectList()
            .block(Duration.ofSeconds(5));

        ArgumentCaptor<Collection<String>> deleteIds = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteById(deleteIds.capture());
        assertEquals(List.of("binding-right"), deleteIds.getValue().stream().toList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldTreatMultipleRowsWithSameDataIdAsSingleUpgradeTarget() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> loadQuery = mock(ReactiveQuery.class);

        CapabilityResourceInstallEntity first = installEntity("binding-1", "cap-1", "toolkit", "old-1", "group-1");
        CapabilityResourceInstallEntity second = installEntity("binding-2", "cap-1", "toolkit", "old-2", "group-1");

        when(client.download("cap-1", "1.0.1")).thenReturn(Mono.just(packageFor("cap-1")));
        when(repository.createQuery()).thenReturn(loadQuery);
        when(loadQuery.fetch()).thenReturn(Flux.just(first, second));
        when(repository.deleteById(any(Collection.class))).thenReturn(Mono.just(1));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> context
            .loadInstallResources()
            .collectList()
            .flatMapMany(resources -> {
                assertEquals(List.of("group-1", "group-1"), resources.stream().map(InstalledResource::getDataId).toList());
                return Flux.just(resource("toolkit", "new-1", "group-1"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .upgrade("cap-1", "1.0.1", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        ArgumentCaptor<Collection<String>> deleteIds = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteById(deleteIds.capture());
        assertEquals(List.of("binding-1", "binding-2"), deleteIds.getValue().stream().toList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldRejectUpgradeWhenMultipleVisibleInstallRootsExistWithoutExplicitTarget() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> loadQuery = mock(ReactiveQuery.class);

        when(repository.createQuery()).thenReturn(loadQuery);
        when(loadQuery.fetch()).thenReturn(Flux.just(
            installEntity("binding-left", "cap-1", "tool", "old-left", "tenant-left"),
            installEntity("binding-right", "cap-1", "tool", "old-right", "tenant-right")
        ));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        ValidationException error = org.junit.jupiter.api.Assertions.assertThrows(
            ValidationException.class,
            () -> manager.upgrade("cap-1", "1.0.1", Map.of()).collectList().block(Duration.ofSeconds(5))
        );

        assertEquals("error.capability_upgrade_target_ambiguous", error.getMessage());
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
    void shouldReportStructuredRecorderEvents() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> query = mock(ReactiveQuery.class);

        when(client.download("cap-1", "1.0.0")).thenReturn(Mono.just(packageFor("cap-1")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(query);
        when(query.fetch()).thenReturn(Flux.empty());
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> {
            ActionRecorder<Object> root = context
                .monitor()
                .recorder()
                .action("marketplace.install");
            root
                .child("ui.open-detail")
                .tag("page", "skill")
                .attribute("id", "skill-1")
                .complete();
            root.complete();
            return Flux.just(resource("tool", "new-visible", "tenant-visible"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<org.jetlinks.marketplace.ProgressState<InstalledResource>> states = manager
            .install("cap-1", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        List<ActionRecord> records = states
            .stream()
            .map(org.jetlinks.marketplace.ProgressState::getExtra)
            .filter(ActionRecord.class::isInstance)
            .map(ActionRecord.class::cast)
            .toList();

        assertEquals(2, records.size());

        ActionRecord root = records
            .stream()
            .filter(record -> "marketplace.install".contentEquals(record.getAction()))
            .findFirst()
            .orElseThrow();
        ActionRecord child = records
            .stream()
            .filter(record -> "ui.open-detail".contentEquals(record.getAction()))
            .findFirst()
            .orElseThrow();

        assertEquals(root.getId(), child.getParentId());
        assertEquals("skill", child.getTags().get("page"));
        assertEquals("skill-1", child.getAttributes().get("id"));

        ArgumentCaptor<CapabilityOperationEvent> eventCaptor = ArgumentCaptor.forClass(CapabilityOperationEvent.class);
        verify(client, atLeast(1)).reportOperationEvent(eventCaptor.capture());

        List<CapabilityOperationEvent> recorderEvents = eventCaptor
            .getAllValues()
            .stream()
            .filter(event -> "ui.open-detail".equals(event.getMessage())
                || "marketplace.install".equals(event.getMessage()))
            .toList();

        assertEquals(2, recorderEvents.size());
        assertTrue(recorderEvents
                       .stream()
                       .map(CapabilityOperationEvent::getType)
                       .allMatch(CapabilityOperationEvent.Type.action::equals));
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
