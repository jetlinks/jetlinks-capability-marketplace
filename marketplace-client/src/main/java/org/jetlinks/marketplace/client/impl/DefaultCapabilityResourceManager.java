package org.jetlinks.marketplace.client.impl;

import com.google.common.collect.Collections2;
import lombok.AllArgsConstructor;
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
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.slf4j.event.Level;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class DefaultCapabilityResourceManager implements CapabilityResourceManager {

    private final CapabilityMarketplaceClient client;
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository;


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
                    ProgressState.progress("message.capability_installed_resource", "安装资源成功", resource),
                    Reactors.emitFailureHandler()))
            .collectList()
            .flatMap(resources -> {
                // downloading
                upstream.emitNext(
                    ProgressState.progress("message.capability_saving_resource", "正在保存资源信息"),
                    Reactors.emitFailureHandler());

                // 删除旧的绑定信息
                Mono<Void> task = resourceRepository
                    .createDelete()
                    .where(CapabilityResourceInstallEntity::getCapabilityId, pkg.getInfo().getId())
                    .execute()
                    .then();

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
    public Flux<InstalledResource> listInstalledResources(String type, Collection<String> dataId) {

        return resourceRepository
            .createQuery()
            .is(CapabilityResourceInstallEntity::getType, type)
            .when(CollectionUtils.isNotEmpty(dataId), dsl -> dsl.in(CapabilityResourceInstallEntity::getDataId, dataId))
            .fetch()
            .map(CapabilityResourceInstallEntity::toResource);
    }

    @Override
    public Flux<InstalledResource> listInstalledResources(String type, String capId, Collection<String> resourceId) {
        return resourceRepository
            .createQuery()
            .is(CapabilityResourceInstallEntity::getType, type)
            .is(CapabilityResourceInstallEntity::getCapabilityId, capId)
            .when(CollectionUtils.isNotEmpty(resourceId), dsl -> dsl.in(CapabilityResourceInstallEntity::getResourceId, resourceId))
            .fetch()
            .map(CapabilityResourceInstallEntity::toResource);
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
                .switchIfEmpty(Mono.error(() -> new NotFoundException.NoStackTrace("message.capability.not_found", "功能[{}]未找到", capabilityId)))
                .flatMap(pkg -> {
                    // saving...
                    progressStream.emitNext(
                        ProgressState.progress("message.capability_download_package", "正在下载功能包"),
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
                  .resourceRepository
                  .createQuery()
                  .where(CapabilityResourceInstallEntity::getCapabilityId, pkg
                                                                           .getInfo()
                                                                           .getId())
                  .fetch()
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
