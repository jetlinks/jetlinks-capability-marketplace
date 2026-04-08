package org.jetlinks.marketplace.client.impl;

import com.google.common.collect.Collections2;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
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

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class DefaultCapabilityResourceManager implements CapabilityResourceManager {

    private final CapabilityMarketplaceClient client;
    private final ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository;


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledCapability>> install(String capabilityId,
                                                            String version,
                                                            Map<String, Object> configuration) {

        Sinks.ManyWithUpstream<ProgressState<InstalledCapability>>
            progressStream = Sinks
            .unsafe()
            .manyWithUpstream()
            .multicastOnBackpressureBuffer();

        // downloading
        progressStream.emitNext(
            ProgressState.progress("message.capability_download_package", 0.2F),
            Reactors.emitFailureHandler());

        progressStream.subscribeTo(
            client
                .download(capabilityId, version)
                .flatMap(pkg -> {
                    // saving...
                    progressStream.emitNext(
                        ProgressState.progress("message.capability_download_package", 0.5F),
                        Reactors.emitFailureHandler());
                    return savePackage(pkg, progressStream);
                })
                .map(ProgressState::success)
                .onErrorResume(err -> Mono.just(ProgressState.error(err)))
        );


        return progressStream.asFlux();
    }


    @Transactional(rollbackFor = Throwable.class)
    public Mono<InstalledCapability> savePackage(CapabilityPackage pkg,
                                                 Sinks.ManyWithUpstream<ProgressState<InstalledCapability>> upstream) {
        // todo 安装依赖.
        List<CapabilityDependency> dependencies = pkg.getInfo().getDependencies();

        CapabilityProvider provider = CapabilityProviders.getOrThrow(pkg.getInfo().getProvider());

        return provider
            .install(new CapabilityContextImpl(this, pkg, upstream))
            .flatMap(cap -> {

                // downloading
                upstream.emitNext(
                    ProgressState.progress("message.capability_saving_resource", 0.9F),
                    Reactors.emitFailureHandler());

                List<InstalledResource> resources = cap.getResources();

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
                            Collections2.transform(resources, CapabilityResourceInstallEntity::from)
                        ).then()
                    );
                }

                return task.thenReturn(cap);
            });
    }

    record CapabilityContextImpl(
        DefaultCapabilityResourceManager parent,
        CapabilityPackage pkg,
        Sinks.ManyWithUpstream<ProgressState<InstalledCapability>> progress)
        implements CapabilityProvider.CapabilityContext, Monitor, Logger {

        @Override
        public Flux<InstalledResource> loadInstallResources() {
            return parent
                .resourceRepository
                .createQuery()
                .where(CapabilityResourceInstallEntity::getCapabilityId, pkg.getInfo().getId())
                .fetch()
                .map(CapabilityResourceInstallEntity::toResource);
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


    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledCapability>> upgrade(String capabilityId,
                                                            String targetVersion,
                                                            Map<String, Object> configuration) {
        return install(capabilityId, targetVersion, configuration);
    }
}
