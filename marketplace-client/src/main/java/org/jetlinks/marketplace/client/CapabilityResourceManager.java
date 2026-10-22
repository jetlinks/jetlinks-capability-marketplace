package org.jetlinks.marketplace.client;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

/**
 * 能力安装与升级编排入口.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityResourceManager {

    Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                   String version,
                                                   Map<String, Object> configuration);

    default Flux<ProgressState<InstalledResource>> install(String capabilityId,
                                                           String version,
                                                           CapabilityInstallRequest request) {
        return install(capabilityId, version, request == null ? Map.of() : request.getConfiguration());
    }

    Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                   String targetVersion,
                                                   Map<String, Object> configuration);

    default Flux<ProgressState<InstalledResource>> upgrade(String capabilityId,
                                                           String targetVersion,
                                                           CapabilityInstallRequest request) {
        return upgrade(capabilityId, targetVersion, request == null ? Map.of() : request.getConfiguration());
    }

    Mono<Boolean> isInstalled(String capabilityId);

    /**
     * 按查询参数加载当前上下文可访问的安装资源，统一执行查询前约束和结果过滤。
     *
     * @param query 查询条件、排序和分页参数
     * @return 可访问的安装资源
     * @since 2.20
     */
    Flux<InstalledResource> queryInstalledResources(QueryParamEntity query);

    Flux<InstalledResource> listInstalledResources(String type, Collection<String> dataId);

    Flux<InstalledResource> listInstalledResources(String type,String capId, Collection<String> resourceId);


}
