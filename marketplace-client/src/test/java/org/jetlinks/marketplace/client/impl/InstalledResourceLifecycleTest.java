package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.ezorm.rdb.mapping.defaults.SaveResult;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceFilterContext;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourcePostProcessor;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceSaveContext;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证记录管理接管、默认替换以及保存后处理的生命周期边界。 */
@SuppressWarnings({"unchecked", "rawtypes"})
class InstalledResourceLifecycleTest {

    private static final String PROVIDER = "installed-resource-lifecycle-test";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
    private final CapabilityProvider provider = mock(CapabilityProvider.class);
    private final CapabilityPackage pkg = new CapabilityPackage();
    private final CapabilityResourceInstallEntity previous = new CapabilityResourceInstallEntity();
    private final List<String> events = new ArrayList<>();
    private final CapabilityInstalledResourcePostProcessor postProcessor = (current, resources) -> Mono.fromRunnable(() -> {
        assertSame(provider, current);
        assertEquals(1, resources.size());
        events.add("post");
    });

    @BeforeEach
    void setUp() {
        when(provider.getId()).thenReturn(PROVIDER);
        CapabilityProviders.register(provider);
        CapabilityInfo info = new CapabilityInfo();
        info.setId("cap-1");
        info.setProvider(PROVIDER);
        pkg.setInfo(info);
        pkg.setVersion("2");
        previous.setId("previous-binding");
        previous.setDataId("previous-data");
        InstalledResource resource = new InstalledResource();
        resource.setType("tool");
        resource.setResourceId("resource-1");
        resource.setDataId("new-data");
        when(provider.install(any())).thenReturn(Flux.just(resource));
        when(repository.deleteById(any(Collection.class))).thenAnswer(call -> Mono.fromSupplier(() -> {
            assertEquals(List.of("previous-binding"), List.copyOf(call.getArgument(0, Collection.class)));
            events.add("delete");
            return 1;
        }));
        when(repository.save(any(Collection.class))).thenAnswer(call -> Mono.fromSupplier(() -> {
            events.add("save");
            return mock(SaveResult.class);
        }));
    }

    @AfterEach
    void tearDown() {
        CapabilityProviders.unregister(PROVIDER);
    }

    @Test
    void shouldDelegateReplacementOnceThenRunPostProcessor() {
        var first = new SaveInterceptor(context -> Mono.defer(() -> {
            events.add("first-before");
            return context.save().doOnSuccess(ignore -> events.add("first-after"));
        }));
        var second = new SaveInterceptor(context -> Mono.defer(() -> {
            events.add("second-before");
            return context.save().doOnSuccess(ignore -> events.add("second-after"));
        }));

        save(manager(first, second)).block(TIMEOUT);

        assertEquals(List.of("first-before", "second-before", "delete", "save", "second-after", "first-after", "post"), events);
        verify(repository, times(1)).deleteById(any(Collection.class));
        verify(repository, times(1)).save(any(Collection.class));
    }

    @Test
    void shouldLetCustomStorageReplaceDefaultPersistenceAndKeepPostProcessing() {
        List<CapabilityResourceInstallEntity> stored = new ArrayList<>(List.of(previous));
        var custom = new SaveInterceptor(context -> Mono.fromRunnable(() -> {
            assertSame(pkg, context.getCapabilityPackage());
            assertSame(provider, context.getProvider());
            assertEquals(List.of(previous), context.getPreviousResources());
            assertEquals("cap-1", context.getResources().getFirst().getCapabilityId());
            assertEquals(PROVIDER, context.getResources().getFirst().getProviderId());
            stored.removeAll(context.getPreviousResources());
            stored.addAll(context.getResources());
            events.add("custom-save");
        }));

        save(manager(custom)).block(TIMEOUT);

        assertEquals(List.of("new-data"), stored.stream().map(CapabilityResourceInstallEntity::getDataId).toList());
        assertEquals(List.of("custom-save", "post"), events);
        verifyNoInteractions(repository);
    }

    @Test
    void shouldPropagateCustomStorageFailureWithoutDefaultFallbackOrPostProcessing() {
        RuntimeException error = new IllegalStateException("custom store failed");
        var custom = new SaveInterceptor(context -> Mono.error(error));

        assertSame(error, assertThrows(RuntimeException.class, () -> save(manager(custom)).block(TIMEOUT)));
        assertTrue(events.isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void shouldSkipPostProcessingWhenDefaultSaveFails() {
        RuntimeException error = new IllegalStateException("default save failed");
        when(repository.save(any(Collection.class))).thenReturn(Mono.error(error));

        assertSame(error, assertThrows(RuntimeException.class, () -> save(manager()).block(TIMEOUT)));
        assertEquals(List.of("delete"), events);
    }

    @Test
    void shouldHandleEmptyProviderResultAndDeleteOnlyPreviousRecords() {
        when(provider.install(any())).thenReturn(Flux.empty());
        var observer = new SaveInterceptor(context -> {
            assertTrue(context.getResources().isEmpty());
            return context.save();
        });

        save(manager(observer)).block(TIMEOUT);

        assertEquals(List.of("delete"), events);
        verify(repository, never()).save(any(Collection.class));
    }

    @Test
    void shouldCreateRecordManagementContextForEveryInstallSubscription() {
        List<CapabilityInstalledResourceSaveContext> contexts = new ArrayList<>();
        var custom = new SaveInterceptor(context -> Mono.fromRunnable(() -> contexts.add(context)));
        Mono<Void> install = save(manager(custom));

        install.block(TIMEOUT);
        install.block(TIMEOUT);

        assertEquals(2, contexts.size());
        assertNotSame(contexts.getFirst(), contexts.getLast());
        assertEquals(List.of("post", "post"), events);
        verifyNoInteractions(repository);
    }

    private DefaultCapabilityResourceManager manager(CapabilityInstalledResourceInterceptor... interceptors) {
        return new DefaultCapabilityResourceManager(mock(CapabilityMarketplaceClient.class), repository,
            List.of(interceptors), List.of(postProcessor));
    }

    private Mono<Void> save(DefaultCapabilityResourceManager manager) {
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress = Sinks.unsafe().manyWithUpstream()
            .multicastOnBackpressureBuffer();
        return manager.savePackage(pkg, progress, new CapabilityInstallRequest(), List.of(previous));
    }

    private record SaveInterceptor(Function<CapabilityInstalledResourceSaveContext, Mono<Void>> save)
        implements CapabilityInstalledResourceInterceptor {
        @Override
        public Mono<Void> afterInstalled(CapabilityInstalledResourceSaveContext context) {
            return save.apply(context);
        }

        @Override
        public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                             Flux<CapabilityResourceInstallEntity> resources) {
            return resources;
        }
    }
}
