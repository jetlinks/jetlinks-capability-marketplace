package org.jetlinks.marketplace.client.spi;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.spi.CapabilityProviderUtils.ResolvedAssetTypeInstalledResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 已安装能力资源过滤扩展点.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityInstalledResourceInterceptor {

    /**
     * 在读取安装记录前约束查询，原有结果过滤仍会执行。
     *
     * @param context 加载范围
     * @param query 当前查询参数，权限条件必须与原始条件取交集
     * @param resourceTypes 按原始业务条件在数据库去重后解析的 Provider/资源类型；
     *                      resource 仅含 providerId 和 type，不包含安装记录和数据 ID。
     *                      该流按本次订阅共享，无需查询前置权限时可不订阅
     * @return 受限查询；空 Mono 表示没有匹配范围，不执行安装记录查询；错误直接传播
     * @since 2.20
     * @see #filter(CapabilityInstalledResourceFilterContext, Flux)
     */
    default Mono<QueryParamEntity> prepareQuery(CapabilityInstalledResourceFilterContext context,
                                               QueryParamEntity query,
                                               Flux<ResolvedAssetTypeInstalledResource> resourceTypes) {
        return Mono.just(query);
    }

    Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                 Flux<CapabilityResourceInstallEntity> resources);
}
