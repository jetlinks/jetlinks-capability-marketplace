package org.jetlinks.marketplace.client.impl;

import com.google.common.collect.Collections2;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.exception.NotFoundException;
import org.hswebframework.web.exception.ValidationException;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.core.monitor.logger.Logger;
import org.jetlinks.core.monitor.metrics.Metrics;
import org.jetlinks.core.monitor.recorder.AbstractActionRecorder;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
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
import reactor.util.context.Context;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
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
        return install(capabilityId, version, CapabilityInstallRequest.ofConfiguration(configuration));
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                          String version,
                                                          CapabilityInstallRequest request) {
        return install0(capabilityId, version, normalizeRequest(request), List.of());
    }

    @Transactional(rollbackFor = Throwable.class)
    public Mono<Void> savePackage(CapabilityPackage pkg,
                                  Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                  CapabilityInstallRequest request,
                                  List<CapabilityResourceInstallEntity> installedResources) {
        // todo 安装依赖.
        List<CapabilityDependency> dependencies = pkg.getInfo().getDependencies();

        CapabilityProvider provider = CapabilityProviders.getOrThrow(pkg.getInfo().getProvider());

        return provider
            .install(new CapabilityContextImpl(pkg, request, installedResources, upstream))
            .doOnNext(resource -> upstream
                .emitNext(
                    ProgressState.progress("message.capability_installed_resource", "安装成功", resource),
                    Reactors.emitFailureHandler()))
            .collectList()
            .flatMap(resources -> {

                // 删除当前升级目标范围内的旧绑定信息.
                Mono<Void> task = deleteInstalledResources(installedResources);

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
        return upgrade(capabilityId, targetVersion, CapabilityInstallRequest.ofConfiguration(configuration));
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                          String targetVersion,
                                                          CapabilityInstallRequest request) {
        CapabilityInstallRequest actual = normalizeRequest(request);
        return resolveUpgradeTargets(capabilityId, actual)
            .flatMapMany(resources -> install0(capabilityId, targetVersion, actual, resources));
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

    private Mono<Void> deleteInstalledResources(Collection<CapabilityResourceInstallEntity> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            return Mono.empty();
        }
        return Flux
            .fromIterable(resources)
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
                                                           CapabilityInstallRequest request,
                                                           List<CapabilityResourceInstallEntity> installedResources) {
        return CapabilityOperationContext
            .currentOrCreate()
            .flatMapMany(operationContext -> install0(capabilityId, version, request, installedResources, operationContext)
                .contextWrite(CapabilityOperationContext.makeCurrent(operationContext)));
    }

    private Flux<ProgressState<InstalledResource>> install0(String capabilityId,
                                                            String version,
                                                            CapabilityInstallRequest request,
                                                            List<CapabilityResourceInstallEntity> installedResources,
                                                            CapabilityOperationContext operationContext) {
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>>
            progressStream = Sinks
            .unsafe()
            .manyWithUpstream()
            .multicastOnBackpressureBuffer();

        progressStream.subscribeTo(
            reportOperationEvent(
                operationContext,
                CapabilityOperationEvent.of(CapabilityOperationEvent.Type.download, capabilityId, version))
                .then(client.download(capabilityId, version))
                .switchIfEmpty(Mono.error(() -> new NotFoundException.NoStackTrace(
                    "message.capability.not_found",
                    "功能未找到",
                    capabilityId)))
                .flatMap(pkg -> {
                    // saving...
                    progressStream.emitNext(
                        ProgressState.progress("message.capability_start_install", "开始安装..."),
                        Reactors.emitFailureHandler());
                    return reportOperationEvent(
                        operationContext,
                        CapabilityOperationEvent.of(CapabilityOperationEvent.Type.installing, capabilityId, pkg.getVersion())
                    )
                        .then(savePackage(pkg, progressStream, request, installedResources))
                        .then(reportOperationEvent(
                            operationContext,
                            successEvent(capabilityId, pkg.getVersion())));
                })
                .then(Mono.<ProgressState<InstalledResource>>empty())
                .onErrorResume(err -> Mono.just(ProgressState.error(err)))
                .doFinally(ignore -> progressStream.emitComplete(Reactors.emitFailureHandler()))
                .contextWrite(CapabilityOperationContext.makeCurrent(operationContext))
        );


        return progressStream
            .asFlux()
            .doOnSubscribe((s) -> progressStream.emitNext(
                ProgressState.progress("message.capability_download_package", "正在下载功能包"),
                Reactors.emitFailureHandler()))
            .concatMap(state -> reportOperationEvent(operationContext,
                                                     progressEvent(capabilityId, version, state))
                .thenReturn(state))
            .contextWrite(CapabilityOperationContext.makeCurrent(operationContext));
    }

    private CapabilityInstallRequest normalizeRequest(CapabilityInstallRequest request) {
        return request == null ? new CapabilityInstallRequest() : request;
    }

    private Mono<List<CapabilityResourceInstallEntity>> resolveUpgradeTargets(String capabilityId,
                                                                              CapabilityInstallRequest request) {
        CapabilityInstallRequest.UpgradeOptions upgrade = request.getEffectiveUpgrade();
        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.capability(capabilityId))
            .collectList()
            .flatMap(resources -> {
                if (upgrade.hasTargetDataIds()) {
                    Set<String> targetDataIds = new LinkedHashSet<>(upgrade.getTargetDataIds());
                    List<CapabilityResourceInstallEntity> matched = resources
                        .stream()
                        .filter(resource -> targetDataIds.contains(resource.getDataId()))
                        .toList();
                    if (matched.isEmpty()) {
                        return Mono.error(new ValidationException.NoStackTrace("error.capability_upgrade_target_not_found"));
                    }
                    return Mono.just(matched);
                }

                Set<String> dataIds = resources
                    .stream()
                    .map(CapabilityResourceInstallEntity::getDataId)
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

                if (dataIds.isEmpty()) {
                    return Mono.just(List.of());
                }
                if (dataIds.size() == 1) {
                    return Mono.just(resources);
                }
                return Mono.error(new ValidationException.NoStackTrace("error.capability_upgrade_target_ambiguous"));
            });
    }

    private CapabilityOperationEvent progressEvent(String capabilityId,
                                                   String version,
                                                   ProgressState<InstalledResource> state) {
        CapabilityOperationEvent.Type type = state.getExtra() instanceof ActionRecord
            ? CapabilityOperationEvent.Type.action
            : switch (state.getType()) {
                case error -> CapabilityOperationEvent.Type.failed;
                case log -> CapabilityOperationEvent.Type.log;
                case success -> CapabilityOperationEvent.Type.success;
                default -> CapabilityOperationEvent.Type.progress;
            };
        CapabilityOperationEvent event = CapabilityOperationEvent.of(
            type,
            capabilityId,
            version
        );
        event.setMessage(state.getMessage());
        if (state.getType() == ProgressState.Type.error) {
            event.setErrorMessage(state.getMessage());
        }
        if (state.getType() == ProgressState.Type.log) {
            event.setLevel(String.valueOf(state.getExtra()));
        }
        if (state.getExtra() instanceof InstalledResource resource) {
            event.setResource(resource);
        }
        return event;
    }

    private CapabilityOperationEvent successEvent(String capabilityId,
                                                  String version) {
        CapabilityOperationEvent event = CapabilityOperationEvent.of(
            CapabilityOperationEvent.Type.success,
            capabilityId,
            version
        );
        event.setMessage("安装完成");
        return event;
    }

    private Mono<Void> reportOperationEvent(CapabilityOperationContext context,
                                            CapabilityOperationEvent event) {
        if (event == null) {
            return Mono.empty();
        }
        event.setOperationId(context.getId());
        if (event.getInstallKey() == null) {
            event.setInstallKey(CapabilityOperationEvent.DEFAULT_INSTALL_KEY);
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }
        return Mono
            .defer(() -> {
                Mono<Void> report = client.reportOperationEvent(event);
                return report == null ? Mono.empty() : report;
            })
            .onErrorResume(error -> {
                log.warn("report capability operation event failed, operationId:{}, capabilityId:{}, type:{}",
                         event.getOperationId(),
                         event.getCapabilityId(),
                         event.getType(),
                         error);
                return Mono.empty();
            });
    }


    record CapabilityContextImpl(
        CapabilityPackage pkg,
        CapabilityInstallRequest request,
        List<CapabilityResourceInstallEntity> installedResources,
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress)
        implements CapabilityProvider.CapabilityContext, Monitor, Logger {

        @Override
        public Flux<InstalledResource> loadInstallResources() {
            return Flux
                .fromIterable(installedResources == null ? List.<CapabilityResourceInstallEntity>of() : installedResources)
                .map(CapabilityResourceInstallEntity::toResource);
        }


        @Override
        public Map<String, Object> configuration() {
            return request == null ? Map.of() : request.getConfiguration();
        }

        @Override
        public CapabilityInstallRequest request() {
            return request == null ? new CapabilityInstallRequest() : request;
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
        public Recorder recorder() {
            return new Recorder() {
                @Override
                public <E> ActionRecorder<E> action(CharSequence action) {
                    return newRecorder(action, null);
                }
            };
        }

        private <E> ActionRecorder<E> newRecorder(CharSequence action,
                                                  String parentRecordId) {
            ProgressActionRecorder<E> recorder = new ProgressActionRecorder<>(action, parentRecordId, progress);
            recorder.start(Context.empty());
            return recorder;
        }

        @Override
        public void log(Level level, String message, Object... args) {
            progress.emitNext(
                ProgressState.log(level.name(), message, args),
                Reactors.emitFailureHandler()
            );
        }

        private final class ProgressActionRecorder<E> extends AbstractActionRecorder<E> {

            private ProgressActionRecorder(CharSequence action,
                                           String parentRecordId,
                                           Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress) {
                super(action, parentRecordId);
                this.progress = progress;
            }

            private final Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress;

            @Override
            protected void handle(ActionRecord record) {
                String message = record.getAction() == null
                    ? "operation"
                    : String.valueOf(record.getAction());
                progress.emitNext(
                    ProgressState.progress(message, message, record),
                    Reactors.emitFailureHandler()
                );
            }

            @Override
            public <T> ActionRecorder<T> child(CharSequence action) {
                ProgressActionRecorder<T> child = new ProgressActionRecorder<>(action, record.getId(), progress);
                child.start(Context.empty());
                if (StringUtils.hasText(record.getTraceId())) {
                    child.record.setTraceId(record.getTraceId());
                    child.record.setSpanId(record.getSpanId());
                }
                return child;
            }
        }
    }

}
