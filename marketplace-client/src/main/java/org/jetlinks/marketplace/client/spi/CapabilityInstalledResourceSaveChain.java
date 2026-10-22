package org.jetlinks.marketplace.client.spi;

import reactor.core.publisher.Mono;

/**
 * Provider 安装完成后的记录管理责任链，由管理器为当前节点创建。
 * 链尾替换旧记录并保存新记录，保存后处理器由管理器在整条链成功完成后执行。
 *
 * @author zhouhao
 * @since 2.20
 * @see CapabilityInstalledResourceInterceptor#afterInstalled(CapabilityInstalledResourceSaveContext, CapabilityInstalledResourceSaveChain)
 */
@FunctionalInterface
public interface CapabilityInstalledResourceSaveChain {

    /**
     * 订阅时继续后续记录管理节点，不自行订阅、缓存或重试；每次处理至多委托一次。
     *
     * @param context 非空记录管理上下文，描述当前 Provider 安装结果及本次覆盖的旧记录
     * @return 完成信号；空信号表示已处理，不触发默认回退，异常中断后续处理
     */
    Mono<Void> proceed(CapabilityInstalledResourceSaveContext context);
}
