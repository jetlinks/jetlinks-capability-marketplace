package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.core.param.QueryParam;
import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceFilterContext;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceLoadContext;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 安装记录查询前扩展契约，不依赖资产或租户组件。 */
@SuppressWarnings("unchecked")
class InstalledResourceQueryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
    private final ReactiveQuery<CapabilityResourceInstallEntity> installedQuery = mock(ReactiveQuery.class);

    @Test
    void shouldPrepareLoadingRangeBeforeFetch() {
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.empty());
        var interceptor = new QueryInterceptor((context, query) -> {
            assertEquals("tool", context.type());
            assertEquals("cap-1", context.capabilityId());
            query.toNestQuery().is("version", "1");
            return Mono.just(query);
        });

        manager(interceptor).listInstalledResources("tool", "cap-1", List.of("resource-1")).blockLast(TIMEOUT);

        ArgumentCaptor<QueryParam> param = ArgumentCaptor.forClass(QueryParam.class);
        verify(installedQuery).setParam(param.capture());
        var range = param.getValue().getTerms().getFirst().getTerms();
        assertEquals(List.of("capabilityId", "type", "resourceId"), range.stream().map(term -> term.getColumn()).toList());
        assertEquals(List.of("resource-1"), range.get(2).getValue());
        assertEquals("version", param.getValue().getTerms().getLast().getColumn());
        assertEquals("1", param.getValue().getTerms().getLast().getValue());
        assertFalse(param.getValue().isPaging());
        verify(repository, never()).nativeQuery();
    }

    @Test
    void shouldKeepEmptyCustomLoadWithoutDefaultFallback() {
        CapabilityInstalledResourceInterceptor denied = mock(CapabilityInstalledResourceInterceptor.class);
        CapabilityInstalledResourceInterceptor remaining = mock(CapabilityInstalledResourceInterceptor.class);
        when(denied.loadInstalled(any())).thenReturn(Flux.empty());
        when(denied.filter(any(), any())).thenAnswer(call -> call.getArgument(1));
        when(remaining.filter(any(), any())).thenAnswer(call -> call.getArgument(1));

        assertTrue(manager(denied, remaining).listInstalledResources("tool", List.of())
            .collectList().block(TIMEOUT).isEmpty());

        verifyNoInteractions(repository);
        verify(remaining, never()).loadInstalled(any());
    }

    @Test
    void shouldPropagatePreparationErrorWithoutFetching() {
        RuntimeException error = new IllegalStateException("query extension failed");
        var failed = new QueryInterceptor((context, query) -> Mono.error(error));

        assertSame(error, assertThrows(RuntimeException.class, () -> manager(failed)
            .listInstalledResources("tool", List.of()).collectList().block(TIMEOUT)));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldPrepareIndependentQueryForEachSubscriptionInInterceptorOrder() {
        List<String> extensions = new ArrayList<>();
        var first = new QueryInterceptor((context, query) -> Mono.deferContextual(ctx -> {
            String scope = ctx.get("scope");
            extensions.add(scope);
            query.toNestQuery().is("version", scope);
            return Mono.just(query);
        }));
        var second = new QueryInterceptor((context, query) -> {
            assertEquals(2, query.getTerms().size());
            extensions.add("second");
            return Mono.just(query);
        });
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.empty());
        Flux<InstalledResource> resources = manager(first, second).listInstalledResources("tool", List.of());
        resources.contextWrite(ctx -> ctx.put("scope", "a")).blockLast(TIMEOUT);
        resources.contextWrite(ctx -> ctx.put("scope", "b")).blockLast(TIMEOUT);

        ArgumentCaptor<QueryParam> params = ArgumentCaptor.forClass(QueryParam.class);
        verify(installedQuery, times(2)).setParam(params.capture());
        assertNotSame(params.getAllValues().getFirst(), params.getAllValues().getLast());
        assertEquals("a", params.getAllValues().getFirst().getTerms().getLast().getValue());
        assertEquals("b", params.getAllValues().getLast().getTerms().getLast().getValue());
        assertEquals(List.of("a", "second", "b", "second"), extensions);
    }

    @Test
    void shouldFilterCustomLoadedResourcesWithoutDefaultQuery() {
        CapabilityResourceInstallEntity visible = new CapabilityResourceInstallEntity();
        visible.setResourceId("visible");
        CapabilityResourceInstallEntity hidden = new CapabilityResourceInstallEntity();
        hidden.setResourceId("hidden");
        var custom = new CapabilityInstalledResourceInterceptor() {
            @Override
            public Flux<CapabilityResourceInstallEntity> loadInstalled(CapabilityInstalledResourceLoadContext context) {
                assertEquals("tool", context.getFilterContext().type());
                return Flux.just(visible, hidden);
            }

            @Override
            public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                                 Flux<CapabilityResourceInstallEntity> resources) {
                return resources;
            }
        };
        CapabilityInstalledResourceInterceptor filter = (context, resources) -> resources
            .filter(resource -> "visible".equals(resource.getResourceId()));

        assertEquals(List.of("visible"), manager(custom, filter).listInstalledResources("tool", List.of())
            .map(InstalledResource::getResourceId).collectList().block(TIMEOUT));
        verifyNoInteractions(repository);
    }

    private DefaultCapabilityResourceManager manager(CapabilityInstalledResourceInterceptor... interceptors) {
        return new DefaultCapabilityResourceManager(mock(CapabilityMarketplaceClient.class), repository, List.of(interceptors));
    }

    private record QueryInterceptor(
        BiFunction<CapabilityInstalledResourceFilterContext, QueryParamEntity, Mono<QueryParamEntity>> prepare
    ) implements CapabilityInstalledResourceInterceptor {
        @Override
        public Flux<CapabilityResourceInstallEntity> loadInstalled(CapabilityInstalledResourceLoadContext context) {
            return prepare.apply(context.getFilterContext(), context.getQuery()).flatMapMany(context::load);
        }

        @Override
        public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                             Flux<CapabilityResourceInstallEntity> resources) {
            return resources;
        }
    }
}
