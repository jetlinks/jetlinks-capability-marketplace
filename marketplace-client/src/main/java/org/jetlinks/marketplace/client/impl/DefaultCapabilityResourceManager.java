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

import java.util.*;

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
        return install0(capabilityId, version, normalizeRequest(request), List.of(), Set.of());
    }

    @Transactional(rollbackFor = Throwable.class)
    public Mono<Void> savePackage(CapabilityPackage pkg,
                                  Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                  CapabilityInstallRequest request,
                                  List<CapabilityResourceInstallEntity> installedResources) {
        Set<String> installingStack = new LinkedHashSet<>();
        if (pkg != null && pkg.getInfo() != null && StringUtils.hasText(pkg.getInfo().getId())) {
            installingStack.add(pkg.getInfo().getId());
        }
        return savePackage(pkg, upstream, request, installedResources, installingStack);
    }

    @Transactional(rollbackFor = Throwable.class)
    public Mono<Void> savePackage(CapabilityPackage pkg,
                                  Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                  CapabilityInstallRequest request,
                                  List<CapabilityResourceInstallEntity> installedResources,
                                  Set<String> installingStack) {
        CapabilityProvider provider = CapabilityProviders.getOrThrow(pkg.getInfo().getProvider());

        return installDependencies(pkg, upstream, installingStack)
            .flatMap(dependencyResources -> Mono.defer(() -> provider
                .install(new CapabilityContextImpl(pkg, request, installedResources, dependencyResources, upstream))
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
                })));
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
            .flatMapMany(resources -> install0(capabilityId, targetVersion, actual, resources, Set.of()));
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
        return install0(capabilityId, version, request, installedResources, Set.of());
    }

    private Flux<ProgressState<InstalledResource>> install0(String capabilityId,
                                                            String version,
                                                            CapabilityInstallRequest request,
                                                            List<CapabilityResourceInstallEntity> installedResources,
                                                            Set<String> installingStack) {
        if (installingStack.contains(capabilityId)) {
            return Flux.just(ProgressState.error(
                new ValidationException.NoStackTrace("error.capability_dependency_cycle_detected")));
        }
        Set<String> nextInstallingStack = new LinkedHashSet<>(installingStack);
        nextInstallingStack.add(capabilityId);

        return CapabilityOperationContext
            .currentOrCreate()
            .flatMapMany(operationContext -> install0(capabilityId, version, request, installedResources, operationContext, nextInstallingStack)
                .contextWrite(CapabilityOperationContext.makeCurrent(operationContext)));
    }

    private Flux<ProgressState<InstalledResource>> install0(String capabilityId,
                                                            String version,
                                                            CapabilityInstallRequest request,
                                                            List<CapabilityResourceInstallEntity> installedResources,
                                                            CapabilityOperationContext operationContext) {
        return install0(capabilityId, version, request, installedResources, operationContext, Set.of());
    }

    private Flux<ProgressState<InstalledResource>> install0(String capabilityId,
                                                            String version,
                                                            CapabilityInstallRequest request,
                                                            List<CapabilityResourceInstallEntity> installedResources,
                                                            CapabilityOperationContext operationContext,
                                                            Set<String> installingStack) {
        return doInstall0(capabilityId, version, request, installedResources, operationContext, installingStack)
            .contextWrite(CapabilityOperationContext.makeCurrent(operationContext));
    }

    private Flux<ProgressState<InstalledResource>> doInstall0(String capabilityId,
                                                              String version,
                                                              CapabilityInstallRequest request,
                                                              List<CapabilityResourceInstallEntity> installedResources,
                                                              CapabilityOperationContext operationContext,
                                                              Set<String> installingStack) {
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
                        .then(savePackage(pkg, progressStream, request, installedResources, installingStack))
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

    private Mono<List<CapabilityResourceInstallEntity>> installDependencies(CapabilityPackage pkg,
                                                                            Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                                                            Set<String> installingStack) {
        CapabilityInfo info = pkg.getInfo();
        List<CapabilityDependency> dependencies = info == null ? List.of() : info.getDependencies();
        if (CollectionUtils.isEmpty(dependencies)) {
            return Mono.just(Collections.emptyList());
        }
        return Flux
            .fromIterable(dependencies)
            .concatMap(dependency -> installDependency(dependency, upstream, installingStack))
            .collectList();
    }

    private Flux<CapabilityResourceInstallEntity> installDependency(CapabilityDependency dependency,
                                                                    Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                                                    Set<String> installingStack) {
        if (dependency == null || !StringUtils.hasText(dependency.getCapabilityId())) {
            return Flux.error(new ValidationException.NoStackTrace("error.capability_dependency_invalid"));
        }
        String dependencyId = dependency.getCapabilityId();

        return loadInstalledResourceEntities(CapabilityInstalledResourceFilterContext.capability(dependencyId))
            .collectList()
            .flatMapMany(installedResources -> {
                if (isDependencySatisfied(dependency, installedResources)) {
                    upstream.emitNext(
                        ProgressState.progress(
                            "message.capability_dependency_skip",
                            "依赖能力已安装,跳过"),
                        Reactors.emitFailureHandler());
                    return Flux.fromIterable(installedResources);
                }
                if (installingStack.contains(dependencyId)) {
                    return Flux.error(new ValidationException.NoStackTrace("error.capability_dependency_cycle_detected"));
                }
                return resolveDependencyVersion(dependency)
                    .flatMapMany(version -> installDependencyVersion(
                        dependencyId,
                        version.getVersion(),
                        CollectionUtils.isNotEmpty(installedResources),
                        upstream,
                        installingStack)
                        .thenMany(Flux.defer(() -> loadInstalledResourceEntities(
                            CapabilityInstalledResourceFilterContext.capability(dependencyId)))));
            });
    }

    private boolean isDependencySatisfied(CapabilityDependency dependency,
                                          List<CapabilityResourceInstallEntity> installedResources) {
        if (CollectionUtils.isEmpty(installedResources)) {
            return false;
        }
        if (!StringUtils.hasText(dependency.getVersionRange())) {
            return true;
        }
        return getMaxInstalledVersion(installedResources)
            .filter(version -> matchesDependencyVersionRange(version, dependency.getVersionRange()))
            .isPresent();
    }

    private java.util.Optional<String> getMaxInstalledVersion(List<CapabilityResourceInstallEntity> installedResources) {
        return installedResources
            .stream()
            .map(CapabilityResourceInstallEntity::getVersion)
            .filter(StringUtils::hasText)
            .max(Version::compare);
    }

    private Mono<CapabilityVersion> resolveDependencyVersion(CapabilityDependency dependency) {
        return client
            .getVersions(dependency.getCapabilityId())
            .filter(CapabilityVersion::isAvailable)
            .filter(version -> StringUtils.hasText(version.getVersion()))
            .filter(version -> matchesDependencyVersionRange(version.getVersion(), dependency.getVersionRange()))
            .sort(Comparator.reverseOrder())
            .next()
            .switchIfEmpty(Mono.error(
                new ValidationException.NoStackTrace("error.capability_dependency_version_not_found")));
    }

    private Mono<Void> installDependencyVersion(String capabilityId,
                                                String version,
                                                boolean upgrade,
                                                Sinks.ManyWithUpstream<ProgressState<InstalledResource>> upstream,
                                                Set<String> installingStack) {
        //TODO 2026/5/15 从安装包来、从请求来
        CapabilityInstallRequest request = new CapabilityInstallRequest();
        Flux<ProgressState<InstalledResource>> progress = upgrade
            ? resolveUpgradeTargets(capabilityId, request)
            .flatMapMany(resources -> install0(capabilityId, version, request, resources, installingStack))
            : install0(capabilityId, version, request, List.of(), installingStack);

        upstream.emitNext(
            ProgressState.progress(
                upgrade ? "message.capability_dependency_upgrade" : "message.capability_dependency_install",
                upgrade ? "正在升级依赖能力" : "正在安装依赖能力"),
            Reactors.emitFailureHandler());

        return progress
            .concatMap(state -> {
                upstream.emitNext(state, Reactors.emitFailureHandler());
                if (state.getType() == ProgressState.Type.error) {
                    return Mono.error(new ValidationException.NoStackTrace(state.getMessage()));
                }
                return Mono.empty();
            })
            .then();
    }

    private boolean matchesDependencyVersionRange(String version,
                                                  String versionRange) {
        try {
            return CapabilityVersionRange.matches(version, versionRange);
        } catch (IllegalArgumentException e) {
            throw new ValidationException.NoStackTrace("error.capability_dependency_version_range_invalid");
        }
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
        List<CapabilityResourceInstallEntity> dependencyResources,
        Sinks.ManyWithUpstream<ProgressState<InstalledResource>> progress)
        implements CapabilityProvider.CapabilityContext, Monitor, Logger {

        @Override
        public Flux<InstalledResource> loadInstallResources() {
            return Flux
                .fromIterable(installedResources == null ? CollectionUtils.emptyCollection() : installedResources)
                .map(CapabilityResourceInstallEntity::toResource);
        }

        @Override
        public Flux<InstalledResource> loadDependencyResources() {
            return Flux
                .fromIterable(dependencyResources == null ? CollectionUtils.emptyCollection() : dependencyResources)
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
