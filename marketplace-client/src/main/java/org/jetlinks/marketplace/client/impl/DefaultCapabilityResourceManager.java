package org.jetlinks.marketplace.client.impl;

import com.google.common.collect.Collections2;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.exception.NotFoundException;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.tracer.Tracer;
import org.jetlinks.core.utils.Reactors;
import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceFilterContext;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.slf4j.event.Level;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultCapabilityResourceManager implements CapabilityResourceManager {

    private final CapabilityMarketplaceClient client;
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository;
    private final List<CapabilityInstalledResourceInterceptor> installedResourceInterceptors;

    public DefaultCapabilityResourceManager(CapabilityMarketplaceClient client,
                                            ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository) {
        this(client, resourceRepository, List.of());
    }

    public DefaultCapabilityResourceManager(CapabilityMarketplaceClient client,
                                            ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository,
                                            List<CapabilityInstalledResourceInterceptor> installedResourceInterceptors) {
        this.client = client;
        this.resourceRepository = resourceRepository;
        this.installedResourceInterceptors = installedResourceInterceptors == null
            ? List.of()
            : List.copyOf(installedResourceInterceptors);
    }


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                          String version,
                                                          Map<String, Object> configuration) {
        return install0(capabilityId, version, configuration, false);
    }


    @Transactional(rollbackFor = Throwable.class)
    public Mono<Void> savePackage(CapabilityPackage pkg,
                                  Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                  Map<String, Object> configuration,
                                  boolean force) {
        // todo 安装依赖.
        List<CapabilityDependency> dependencies = pkg.getInfo().getDependencies();

        CapabilityProvider provider = CapabilityProviders.getOrThrow(pkg.getInfo().getProvider());

        return provider
            .install(new CapabilityContextImpl(this, pkg, configuration, upstream, force))
            .doOnNext(resource -> upstream
                .emitNext(
                    ProgressState.progress("message.capability_installed_resource", "安装成功", resource),
                    Reactors.emitFailureHandler()))
            .collectList()
            .flatMap(resources -> {

                // 删除当前上下文可见的旧绑定信息.
                Mono<Void> task = deleteInstalledResources(pkg.getInfo().getId());

                // 创建绑定信息
                if (CollectionUtils.isNotEmpty(resources)) {
                    task = task.then(
                        resourceRepository.save(
                            Collections2.transform(resources, res -> CapabilityResourceInstallEntity.from(res, pkg))
                        ).then()
                    );
                }

                return task.then();
            });
    }


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                          String targetVersion,
                                                          Map<String, Object> configuration) {
        return install0(capabilityId, targetVersion, configuration, true);
    }

    @Override
    public Mono<Boolean> isInstalled(String capabilityId) {
        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.capability(capabilityId))
            .hasElements();
    }

    @Override
    public Flux<InstalledResource> listInstalledResources(String type, Collection<String> dataId) {

        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.data(type, dataId))
            .map(CapabilityResourceInstallEntity::toResource);
    }

    @Override
    public Flux<InstalledResource> listInstalledResources(String type, String capId, Collection<String> resourceId) {
        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.resource(type, capId, resourceId))
            .map(CapabilityResourceInstallEntity::toResource);
    }

    private Flux<CapabilityResourceInstallEntity> loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext context) {
        var query = resourceRepository.createQuery();

        if (StringUtils.hasText(context.capabilityId())) {
            query.is(CapabilityResourceInstallEntity::getCapabilityId, context.capabilityId());
        }
        if (StringUtils.hasText(context.type())) {
            query.is(CapabilityResourceInstallEntity::getType, context.type());
        }
        if (CollectionUtils.isNotEmpty(context.dataIds())) {
            query.in(CapabilityResourceInstallEntity::getDataId, context.dataIds());
        }
        if (CollectionUtils.isNotEmpty(context.resourceIds())) {
            query.in(CapabilityResourceInstallEntity::getResourceId, context.resourceIds());
        }

        return filterInstalledResources(context, query.fetch());
    }

    private Flux<CapabilityResourceInstallEntity> filterInstalledResources(CapabilityInstalledResourceFilterContext context,
                                                                           Flux<CapabilityResourceInstallEntity> resources) {
        Flux<CapabilityResourceInstallEntity> filtered = resources;
        for (CapabilityInstalledResourceInterceptor interceptor : installedResourceInterceptors) {
            filtered = interceptor.filter(context, filtered);
        }
        return filtered;
    }

    private Mono<Void> deleteInstalledResources(String capabilityId) {
        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.capability(capabilityId))
            .map(CapabilityResourceInstallEntity::getId)
            .filter(Objects::nonNull)
            .collectList()
            .flatMap(ids -> CollectionUtils.isEmpty(ids)
                ? Mono.empty()
                : resourceRepository.deleteById(ids).then());
    }

    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledResource>> install0(String capabilityId,
                                                           String version,
                                                           Map<String, Object> configuration,
                                                           boolean force) {
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>>
            progressStream = Sinks
            .unsafe()
            .manyWithUpstream()
            .multicastOnBackpressureBuffer();

        progressStream.subscribeTo(
            client
                .download(capabilityId, version)
                .switchIfEmpty(Mono.error(() -> new NotFoundException.NoStackTrace(
                    "message.capability.not_found",
                    "功能未找到",
                    capabilityId)))
                .flatMap(pkg -> {
                    // saving...
                    progressStream.emitNext(
                        ProgressState.progress("message.capability_start_install", "开始安装..."),
                        Reactors.emitFailureHandler());
                    return savePackage(pkg, progressStream, configuration, force);
                })
                .then(Mono.<ProgressState<InstalledResource>>empty())
                .onErrorResume(err -> Mono.just(ProgressState.error(err)))
                .doFinally(ignore -> progressStream.emitComplete(Reactors.emitFailureHandler()))
        );


        return progressStream
            .asFlux()
            .doOnSubscribe((s) -> progressStream.emitNext(
                ProgressState.progress("message.capability_download_package", "正在下载功能包"),
                Reactors.emitFailureHandler()));
    }


    record CapabilityContextImpl(
        DefaultCapabilityResourceManager parent,
        CapabilityPackage pkg,
        Map<String, Object> configuration,
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress,
        boolean force)
        implements CapabilityProvider.CapabilityContext, Monitor, Logger {

        @Override
        public Flux<InstalledResource> loadInstallResources() {
            return force ? Flux.empty()
                : parent
                  .loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.capability(pkg
                                                                                                         .getInfo()
                                                                                                         .getId()))
                  .map(CapabilityResourceInstallEntity::toResource);
        }


        @Override
        public Map<String, Object> configuration() {
            return configuration == null ? Map.of() : configuration;
        }

        @Override
        public Monitor monitor() {
            return this;
        }

        @Override
        public Logger logger() {
            return this;
        }

        @Override
        public Tracer tracer() {
            return Tracer.noop();
        }

        @Override
        public Metrics metrics() {
            return Metrics.noop();
        }

        @Override
        public void log(Level level, String message, Object... args) {
            progress.emitNext(
                ProgressState.log(level.name(), message, args),
                Reactors.emitFailureHandler()
            );
        }
    }

}
