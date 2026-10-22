package org.jetlinks.marketplace.client.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 安装记录加载上下文。查询参数在本次订阅内独立，可由扩展追加约束。
 *
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#loadInstalled(CapabilityInstalledResourceLoadContext)
 */
@AllArgsConstructor
public final class CapabilityInstalledResourceLoadContext {

    @Getter
    private final CapabilityInstalledResourceFilterContext filterContext;

    @Getter
    private final QueryParamEntity query;

    private final Function<QueryParamEntity, Flux<CapabilityResourceInstallEntity>> loader;

    /** 继续后续加载扩展，链尾按当前参数查询默认安装记录表。 */
    public Flux<CapabilityResourceInstallEntity> load() {
        return load(query);
    }

    /**
     * 使用指定参数继续后续加载扩展，支持查询扩展返回新的参数对象。
     * 扩展自行返回记录时可不委托；空结果不会触发默认回退。
     *
     * @param query 本次安装记录查询参数
     * @return 后续扩展或默认查询的结果
     */
    public Flux<CapabilityResourceInstallEntity> load(QueryParamEntity query) {
        return Flux.defer(() -> loader.apply(query));
    }
}
