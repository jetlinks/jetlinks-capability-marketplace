package org.jetlinks.marketplace.client.spi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;

/**
 * 管理器按订阅创建的安装记录加载数据，不持有执行委托或跨订阅状态。
 * 查询参数在本次订阅内独立，可追加约束或通过 {@link #withQuery(QueryParamEntity)} 替换。
 *
 * @author zhouhao
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#loadInstalled(CapabilityInstalledResourceLoadContext, CapabilityInstalledResourceLoadChain)
 */
@AllArgsConstructor
public final class CapabilityInstalledResourceLoadContext {

    @Getter
    private final CapabilityInstalledResourceFilterContext filterContext;

    @Getter
    private final QueryParamEntity query;

    /**
     * 使用新的查询参数创建上下文，保留原加载范围，不修改当前上下文或复制参数。
     *
     * @param query 本次安装记录查询参数
     * @return 供后续责任链使用的新上下文
     */
    public CapabilityInstalledResourceLoadContext withQuery(QueryParamEntity query) {
        return new CapabilityInstalledResourceLoadContext(filterContext, query);
    }
}
