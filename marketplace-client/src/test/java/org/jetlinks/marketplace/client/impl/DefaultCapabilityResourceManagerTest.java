package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.marketplace.CapabilityDependency;
import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityOperationContext;
import org.jetlinks.marketplace.CapabilityOperationEvent;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldInstallRequiredDependenciesBeforeMainCapability() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> installedDependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();
        CapabilityResourceInstallEntity installedDependency =
            installEntity("binding-dep-installed", "dep-cap", "tool", "dep-cap-resource", "dep-cap-data");
        installedDependency.setVersion("1.2.0");

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=1.0.0,<2.0.0")))));
        when(client.getVersions("dep-cap"))
            .thenReturn(Flux.just(version("1.0.0"), version("1.2.0"), version("2.0.0")));
        when(client.download("dep-cap", "1.2.0")).thenReturn(Mono.just(packageFor("dep-cap", "1.2.0")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery, installedDependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.empty());
        when(installedDependencyQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> context
            .loadDependencyResources("tool")
            .collectList()
            .flatMapMany(dependencyResources -> {
                installedOrder.add(context.pkg().getInfo().getId());
                String capabilityId = context.pkg().getInfo().getId();
                if ("dep-cap".equals(capabilityId)) {
                    assertEquals(List.of(), dependencyResources);
                }
                if ("main-cap".equals(capabilityId)) {
                    assertEquals(List.of("dep-cap-data"),
                                 dependencyResources.stream().map(InstalledResource::getDataId).toList());
                }
                return Flux.just(resource("tool", capabilityId + "-resource", capabilityId + "-data"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("dep-cap", "main-cap"), installedOrder);

        InOrder clientOrder = inOrder(client);
        clientOrder.verify(client).download("main-cap", "1.0.0");
        clientOrder.verify(client).download("dep-cap", "1.2.0");

        ArgumentCaptor<Collection<CapabilityResourceInstallEntity>> savedBindings = ArgumentCaptor.forClass(Collection.class);
        verify(repository, times(2)).save(savedBindings.capture());
        assertEquals(List.of("dep-cap", "main-cap"), savedBindings
            .getAllValues()
            .stream()
            .map(collection -> collection.iterator().next().getCapabilityId())
            .toList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSkipInstalledDependencyWhenVersionMatchesRange() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        CapabilityResourceInstallEntity installedDependency =
            installEntity("binding-dep", "dep-cap", "tool", "dep-old", "dep-data");
        installedDependency.setVersion("1.5.0");

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=1.0.0,<2.0.0")))));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            return Flux.just(resource("tool", "main-resource", "main-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("main-cap"), installedOrder);
        verify(client, never()).getVersions("dep-cap");
        verify(client, never()).download("dep-cap", "1.5.0");
        verify(repository).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUpgradeInstalledDependencyWhenVersionDoesNotMatchRange() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyCheckQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyUpgradeQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        CapabilityResourceInstallEntity installedDependency =
            installEntity("binding-dep", "dep-cap", "tool", "dep-old", "dep-data");
        installedDependency.setVersion("1.0.0");

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=1.2.0,<2.0.0")))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.just(version("1.1.0"), version("1.3.0")));
        when(client.download("dep-cap", "1.3.0")).thenReturn(Mono.just(packageFor("dep-cap", "1.3.0")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyCheckQuery, dependencyUpgradeQuery);
        when(dependencyCheckQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(dependencyUpgradeQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(repository.deleteById(any(Collection.class))).thenReturn(Mono.just(1));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> context
            .loadInstallResources()
            .collectList()
            .flatMapMany(resources -> {
                installedOrder.add(context.pkg().getInfo().getId());
                if ("dep-cap".equals(context.pkg().getInfo().getId())) {
                    assertEquals(List.of("dep-old"), resources.stream().map(InstalledResource::getResourceId).toList());
                }
                String capabilityId = context.pkg().getInfo().getId();
                return Flux.just(resource("tool", capabilityId + "-resource", capabilityId + "-data"));
            })));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("dep-cap", "main-cap"), installedOrder);
        verify(client).download("dep-cap", "1.3.0");

        ArgumentCaptor<Collection<String>> deleted = ArgumentCaptor.forClass(Collection.class);
        verify(repository).deleteById(deleted.capture());
        assertEquals(List.of("binding-dep"), deleted.getValue().stream().toList());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldStopMainInstallWhenDependencyVersionNotFound() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=3.0.0")))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.just(version("1.0.0"), version("2.0.0")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.empty());

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            return Flux.just(resource("tool", "main-resource", "main-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of(), installedOrder);
        assertTrue(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(repository, never()).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldStopMainInstallAndRemainingDependenciesWhenOneDependencyFails() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> firstDependencyQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> failedDependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(
                dependency("dep-ok", ">=1.0.0"),
                dependency("dep-fail", ">=1.0.0"),
                dependency("dep-later", ">=1.0.0")
            ))));
        when(client.getVersions("dep-ok")).thenReturn(Flux.just(version("1.0.0")));
        when(client.download("dep-ok", "1.0.0")).thenReturn(Mono.just(packageFor("dep-ok", "1.0.0")));
        when(client.getVersions("dep-fail")).thenReturn(Flux.just(version("1.0.0")));
        when(client.download("dep-fail", "1.0.0")).thenReturn(Mono.just(packageFor("dep-fail", "1.0.0")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(firstDependencyQuery, failedDependencyQuery);
        when(firstDependencyQuery.fetch()).thenReturn(Flux.empty());
        when(failedDependencyQuery.fetch()).thenReturn(Flux.empty());
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> {
            String capabilityId = context.pkg().getInfo().getId();
            if ("dep-fail".equals(capabilityId)) {
                return Flux.error(new ValidationException.NoStackTrace("error.dependency_install_failed"));
            }
            installedOrder.add(capabilityId);
            return Flux.just(resource("tool", capabilityId + "-resource", capabilityId + "-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("dep-ok"), installedOrder);
        assertTrue(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(client, never()).getVersions("dep-later");
        verify(client, never()).download("dep-later", "1.0.0");
        verify(repository, times(1)).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldStopMainInstallWhenDependencyVersionRangeInvalid() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=")))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.just(version("1.0.0")));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.empty());

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            return Flux.just(resource("tool", "main-resource", "main-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of(), installedOrder);
        assertTrue(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(client, never()).download("dep-cap", "1.0.0");
        verify(repository, never()).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldStopMainInstallWhenDependencyCycleDetected() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=1.0.0")))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.just(version("1.0.0")));
        when(client.download("dep-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("dep-cap", "1.0.0", List.of(dependency("main-cap", ">=1.0.0")))));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.empty());

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            return Flux.just(resource("tool", "main-resource", "main-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of(), installedOrder);
        assertTrue(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(client, never()).getVersions("main-cap");
        verify(repository, never()).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSkipStackDependencyWhenInstalledVersionMatchesRange() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyCheckQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyUpgradeQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> nestedDependencyQuery = mock(ReactiveQuery.class);
        ReactiveQuery<CapabilityResourceInstallEntity> stackDependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        CapabilityResourceInstallEntity installedDependency =
            installEntity("binding-dep", "dep-cap", "tool", "dep-old", "dep-data");
        installedDependency.setVersion("1.0.0");

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(dependency("dep-cap", ">=2.0.0")))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.just(version("2.0.0")));
        when(client.download("dep-cap", "2.0.0"))
            .thenReturn(Mono.just(packageFor("dep-cap", "2.0.0", List.of(dependency("nested-cap", ">=1.0.0")))));
        when(client.getVersions("nested-cap")).thenReturn(Flux.just(version("1.0.0")));
        when(client.download("nested-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("nested-cap", "1.0.0", List.of(dependency("dep-cap", ">=1.0.0")))));
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery())
            .thenReturn(dependencyCheckQuery, dependencyUpgradeQuery, nestedDependencyQuery, stackDependencyQuery);
        when(dependencyCheckQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(dependencyUpgradeQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(nestedDependencyQuery.fetch()).thenReturn(Flux.empty());
        when(stackDependencyQuery.fetch()).thenReturn(Flux.just(installedDependency));
        when(repository.deleteById(any(Collection.class))).thenReturn(Mono.just(1));
        when(repository.save(any(Collection.class))).thenReturn(Mono.just(mock(SaveResult.class)));

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            String capabilityId = context.pkg().getInfo().getId();
            return Flux.just(resource("tool", capabilityId + "-resource", capabilityId + "-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of("nested-cap", "dep-cap", "main-cap"), installedOrder);
        assertFalse(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(client, times(1)).getVersions("dep-cap");
        verify(repository, times(3)).save(any(Collection.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldStopMainInstallWhenOptionalDependencyFails() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
        ReactiveQuery<CapabilityResourceInstallEntity> dependencyQuery = mock(ReactiveQuery.class);
        List<String> installedOrder = new ArrayList<>();

        CapabilityDependency optionalDependency = dependency("dep-cap", ">=1.0.0");
        optionalDependency.setOptional(true);

        when(client.download("main-cap", "1.0.0"))
            .thenReturn(Mono.just(packageFor("main-cap", "1.0.0", List.of(optionalDependency))));
        when(client.getVersions("dep-cap")).thenReturn(Flux.empty());
        when(client.reportOperationEvent(any())).thenReturn(Mono.empty());
        when(repository.createQuery()).thenReturn(dependencyQuery);
        when(dependencyQuery.fetch()).thenReturn(Flux.empty());

        CapabilityProviders.register(provider(context -> {
            installedOrder.add(context.pkg().getInfo().getId());
            return Flux.just(resource("tool", "main-resource", "main-data"));
        }));

        DefaultCapabilityResourceManager manager = new DefaultCapabilityResourceManager(client, repository);

        List<ProgressState<InstalledResource>> states = manager
            .install("main-cap", "1.0.0", Map.of())
            .collectList()
            .block(Duration.ofSeconds(5));

        assertEquals(List.of(), installedOrder);
        assertTrue(states.stream().anyMatch(state -> state.getType() == ProgressState.Type.error));
        verify(repository, never()).save(any(Collection.class));
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
        return packageFor(capabilityId, "1.0.0");
    }

    private CapabilityPackage packageFor(String capabilityId, String version) {
        return packageFor(capabilityId, version, null);
    }

    private CapabilityPackage packageFor(String capabilityId,
                                         String version,
                                         List<CapabilityDependency> dependencies) {
        CapabilityInfo info = new CapabilityInfo();
        info.setId(capabilityId);
        info.setProvider(PROVIDER_ID);
        info.setDependencies(dependencies);

        CapabilityPackage pkg = new CapabilityPackage();
        pkg.setInfo(info);
        pkg.setVersion(version);
        return pkg;
    }

    private CapabilityDependency dependency(String capabilityId, String versionRange) {
        CapabilityDependency dependency = new CapabilityDependency();
        dependency.setCapabilityId(capabilityId);
        dependency.setVersionRange(versionRange);
        return dependency;
    }

    private CapabilityVersion version(String version) {
        CapabilityVersion capabilityVersion = new CapabilityVersion();
        capabilityVersion.setVersion(version);
        return capabilityVersion;
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
