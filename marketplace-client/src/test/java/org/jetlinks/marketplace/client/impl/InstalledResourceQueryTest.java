package org.jetlinks.marketplace.client.impl;

import org.hswebframework.ezorm.core.param.QueryParam;
import org.hswebframework.ezorm.rdb.mapping.ReactiveQuery;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceFilterContext;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 通用查询扩展契约，不依赖资产或租户组件。 */
@SuppressWarnings("unchecked")
class InstalledResourceQueryTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> repository = mock(ReactiveRepository.class);
    private final ReactiveQuery<CapabilityResourceInstallEntity> installedQuery = mock(ReactiveQuery.class);

    @Test
    void shouldKeepProjectionAndPagingWithoutInterceptors() {
        QueryParamEntity query = QueryParamEntity.of("capabilityId", "cap-1");
        query.setIncludes(Set.of("resourceId"));
        query.setExcludes(Set.of("provider_id", "data_id"));
        query.setPageIndex(2);
        query.setPageSize(3);
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.empty());

        assertTrue(manager().queryInstalledResources(query).collectList().block(TIMEOUT).isEmpty());

        ArgumentCaptor<QueryParam> param = ArgumentCaptor.forClass(QueryParam.class);
        verify(installedQuery).setParam(param.capture());
        assertNotSame(query, param.getValue());
        assertEquals(query.getIncludes(), param.getValue().getIncludes());
        assertEquals(query.getExcludes(), param.getValue().getExcludes());
        assertTrue(param.getValue().isPaging());
        assertEquals(2, param.getValue().getPageIndex());
        assertEquals(3, param.getValue().getPageSize());
        verify(repository, never()).nativeQuery();
    }

    @Test
    void shouldFetchDeclaredFieldsForFilterAndRestoreRequestedProjection() {
        QueryParamEntity query = QueryParamEntity.of().noPaging();
        query.setIncludes(Set.of("resourceId"));
        query.setExcludes(Set.of("version", "id"));
        CapabilityResourceInstallEntity resource = resource("binding-1");
        resource.setVersion("1.0");
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.just(resource));

        var interceptor = new CapabilityInstalledResourceInterceptor() {
            @Override
            public Set<String> getRequiredFields() {
                return Set.of("version");
            }

            @Override
            public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                                 Flux<CapabilityResourceInstallEntity> resources) {
                return resources.filter(entity -> "1.0".equals(entity.getVersion()));
            }
        };
        List<InstalledResource> resources = manager(interceptor).queryInstalledResources(query)
            .collectList().block(TIMEOUT);

        assertEquals(1, resources.size());
        assertEquals("binding-1", resources.getFirst().getResourceId());
        assertNull(resources.getFirst().getVersion());
        assertEquals("1.0", resource.getVersion(), "还原投影不能修改拦截器持有的实体");
        ArgumentCaptor<QueryParam> param = ArgumentCaptor.forClass(QueryParam.class);
        verify(installedQuery).setParam(param.capture());
        assertEquals(Set.of("resourceId", "version", "id"), param.getValue().getIncludes());
        assertTrue(param.getValue().getExcludes().isEmpty());
        assertEquals(Set.of("resourceId"), query.getIncludes());
        assertEquals(Set.of("version", "id"), query.getExcludes());
        verify(repository, never()).nativeQuery();
    }

    @Test
    void shouldStopBeforeQueryWhenPreparationIsEmpty() {
        CapabilityInstalledResourceInterceptor denied = mock(CapabilityInstalledResourceInterceptor.class);
        CapabilityInstalledResourceInterceptor remaining = mock(CapabilityInstalledResourceInterceptor.class);
        when(denied.prepareQuery(any(), any(), any())).thenReturn(Mono.empty());

        assertTrue(manager(denied, remaining).queryInstalledResources(QueryParamEntity.of())
            .collectList().block(TIMEOUT).isEmpty());

        verify(repository, never()).createQuery();
        verify(repository, never()).nativeQuery();
        verify(remaining, never()).prepareQuery(any(), any(), any());
        verify(denied, never()).filter(any(), any());
    }

    @Test
    void shouldPropagatePreparationErrorWithoutFetching() {
        CapabilityInstalledResourceInterceptor failed = mock(CapabilityInstalledResourceInterceptor.class);
        RuntimeException error = new IllegalStateException("query extension failed");
        when(failed.prepareQuery(any(), any(), any())).thenReturn(Mono.error(error));

        RuntimeException actual = assertThrows(RuntimeException.class, () -> manager(failed)
            .queryInstalledResources(QueryParamEntity.of()).collectList().block(TIMEOUT));
        assertSame(error, actual);

        verify(repository, never()).createQuery();
        verify(repository, never()).nativeQuery();
        verify(failed, never()).filter(any(), any());
    }

    @Test
    void shouldKeepOrderWithExistingFilteringLambda() {
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.just(resource("first"), resource("second"), resource("third")));
        CapabilityInstalledResourceInterceptor interceptor = (context, resources) -> resources
            .filter(entity -> !"second".equals(entity.getId()))
            .collectList()
            .flatMapMany(list -> {
                Collections.reverse(list);
                return Flux.fromIterable(list);
            });

        List<InstalledResource> resources = manager(interceptor).queryInstalledResources(QueryParamEntity.of().noPaging())
            .collectList().block(TIMEOUT);

        assertEquals(List.of("first", "third"), resources.stream().map(InstalledResource::getResourceId).toList());
        verify(repository, never()).nativeQuery();
    }

    @Test
    void shouldPrepareIndependentQueryForEachSubscriptionInInterceptorOrder() {
        List<String> extensions = new ArrayList<>();
        var first = new CapabilityInstalledResourceInterceptor() {
            @Override
            public Mono<QueryParamEntity> prepareQuery(CapabilityInstalledResourceFilterContext context,
                                                       QueryParamEntity query,
                                                       Flux<CapabilityResourceInstallEntity> resourceTypes) {
                return Mono.deferContextual(ctx -> {
                    String scope = ctx.get("scope");
                    extensions.add(scope);
                    query.toNestQuery().is("version", scope);
                    return Mono.just(query);
                });
            }

            @Override
            public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                                 Flux<CapabilityResourceInstallEntity> resources) {
                return resources;
            }
        };
        var second = new CapabilityInstalledResourceInterceptor() {
            @Override
            public Mono<QueryParamEntity> prepareQuery(CapabilityInstalledResourceFilterContext context,
                                                       QueryParamEntity query,
                                                       Flux<CapabilityResourceInstallEntity> resourceTypes) {
                assertEquals(2, query.getTerms().size());
                extensions.add("second");
                return Mono.just(query);
            }

            @Override
            public Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                                 Flux<CapabilityResourceInstallEntity> resources) {
                return resources;
            }
        };
        when(repository.createQuery()).thenReturn(installedQuery);
        when(installedQuery.fetch()).thenReturn(Flux.just(resource("binding-1")));
        QueryParamEntity query = QueryParamEntity.of("capabilityId", "cap-1");
        Flux<InstalledResource> resources = manager(first, second).queryInstalledResources(query);
        resources.contextWrite(ctx -> ctx.put("scope", "a")).blockLast(TIMEOUT);
        resources.contextWrite(ctx -> ctx.put("scope", "b")).blockLast(TIMEOUT);

        ArgumentCaptor<QueryParam> params = ArgumentCaptor.forClass(QueryParam.class);
        verify(installedQuery, times(2)).setParam(params.capture());
        assertNotSame(params.getAllValues().getFirst(), params.getAllValues().getLast());
        assertEquals("a", params.getAllValues().getFirst().getTerms().getLast().getValue());
        assertEquals("b", params.getAllValues().getLast().getTerms().getLast().getValue());
        assertEquals(List.of("a", "second", "b", "second"), extensions);
        assertEquals(1, query.getTerms().size());
        verify(repository, never()).nativeQuery();
    }

    private DefaultCapabilityResourceManager manager(CapabilityInstalledResourceInterceptor... interceptors) {
        return new DefaultCapabilityResourceManager(mock(CapabilityMarketplaceClient.class), repository, List.of(interceptors));
    }

    private CapabilityResourceInstallEntity resource(String id) {
        CapabilityResourceInstallEntity resource = new CapabilityResourceInstallEntity();
        resource.setId(id);
        resource.setResourceId(id);
        return resource;
    }
}
