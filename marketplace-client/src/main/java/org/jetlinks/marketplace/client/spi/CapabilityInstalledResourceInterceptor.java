package org.jetlinks.marketplace.client.spi;

import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 已安装能力资源加载、记录管理及过滤扩展点.
 *
 * @author zhouhao
 * @since 2.12
 */
public interface CapabilityInstalledResourceInterceptor {

    /**
     * 加载安装记录，覆盖管理器的列表、状态判断、升级及依赖加载；原有结果过滤仍会统一执行。
     *
     * 在订阅时按装配顺序调用，不切换调度器；Spring 装配可通过 {@code @Order} 调整顺序。
     *
     * @param context 本次订阅的加载范围及查询参数。追加约束应与原始条件取交集
     * @param chain 后续加载链；每次处理至多委托一次，接管加载时不调用，不自行订阅或重试
     * @return 安装记录；调用 chain.proceed(context) 继续，或自行返回记录。空结果不回退默认查询，错误直接传播
     * @since 2.20
     * @see #filter(CapabilityInstalledResourceFilterContext, Flux)
     */
    default Flux<CapabilityResourceInstallEntity> loadInstalled(CapabilityInstalledResourceLoadContext context,
                                                               CapabilityInstalledResourceLoadChain chain) {
        return chain.proceed(context);
    }

    /**
     * Provider 安装成功后的记录管理，默认委托后续扩展，链尾替换旧记录并保存新记录。
     * 记录处理成功后才调用保存后处理器；扩展接管存储时须在完成信号前完成自身记录管理。
     *
     * 在订阅时按装配顺序调用，不切换调度器、不自动重试；接管外部存储的扩展负责自身事务一致性。
     *
     * @param context 当前 Provider、安装包、本次覆盖的旧记录及新记录
     * @param chain 后续记录管理链；每次处理至多委托一次，自行接管存储时不调用，不自行订阅
     * @return 记录处理完成信号；空信号表示已处理，不触发默认保存；异常中断安装事务及后置处理
     * @since 2.20
     * @see CapabilityInstalledResourcePostProcessor
     */
    default Mono<Void> afterInstalled(CapabilityInstalledResourceSaveContext context,
                                      CapabilityInstalledResourceSaveChain chain) {
        return chain.proceed(context);
    }

    /**
     * 按装配顺序统一过滤加载结果，加载节点接管或短路也不会跳过任何已注册的结果过滤器。
     *
     * @param context 原始业务加载范围
     * @param resources 默认或自定义来源经前序结果过滤器处理后的记录流，不应自行订阅
     * @return 过滤后的记录；空流不回退，异常向调用方传播
     */
    Flux<CapabilityResourceInstallEntity> filter(CapabilityInstalledResourceFilterContext context,
                                                 Flux<CapabilityResourceInstallEntity> resources);
}
