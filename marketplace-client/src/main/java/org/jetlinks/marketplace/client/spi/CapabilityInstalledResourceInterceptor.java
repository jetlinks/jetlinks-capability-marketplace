package org.jetlinks.marketplace.client.spi;

import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * 已安装能力资源过滤扩展点.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityInstalledResourceInterceptor {

    /**
     * 在读取安装记录前调整查询，原有结果过滤仍会执行。
     *
     * @param context 加载范围
     * @param query 当前订阅独立的查询参数，附加约束应与原始条件取交集
     * @param resourceTypes 按原始查询条件在数据库去重的 Provider/资源类型组合；
     *                      实体仅含 providerId 和 type，不包含安装记录 ID 和 dataId。
     *                      该流延迟加载且在本次订阅内共享，扩展无需类型信息时可不订阅；类型转换由扩展处理
     * @return 受限查询；空 Mono 表示没有匹配范围，不执行安装记录查询；错误直接传播
     * @since 2.20
     * @see #filter(CapabilityInstalledResourceFilterContext, Flux)
     */
    default Mono<QueryParamEntity> prepareQuery(CapabilityInstalledResourceFilterContext context,
                                               QueryParamEntity query,
                                               Flux<CapabilityResourceInstallEntity> resourceTypes) {
        return Mono.just(query);
    }

    /**
     * 声明结果过滤必需的实体属性。管理器构建时读取，需返回稳定的字段集合。
     * SDK 会在查询中补取这些字段，过滤后恢复调用方的字段投影。
     *
     * @return 实体属性名集合，不使用数据库列名；默认没有额外要求
     * @since 2.20
     * @see #filter(CapabilityInstalledResourceFilterContext, Flux)
     */
    default Set<String> getRequiredFields() {
        return Set.of();
    }

    /**
     * 过滤查询结果。SDK 每批最多传入 200 条，并按安装记录 ID 恢复输入顺序。
     * 返回的实体须保留输入 ID，且不应引入输入批次以外的记录。
     */
    Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                 Flux<CapabilityResourceInstallEntity> resources);
}
