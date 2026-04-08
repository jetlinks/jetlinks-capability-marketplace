package org.jetlinks.marketplace.spi;

import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.InstalledResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 能力提供者 SPI
 *
 * @author zhouhao
 * @see CapabilityProviders
 * @since 2.12
 */
public interface CapabilityProvider {

    /**
     * ID
     */
    String getId();

    /**
     * 名称
     */
    String getName();

    /**
     * 安装能力
     *
     * @param context 安装上下文
     * @return 安装结果
     */
    Mono<InstalledCapability> install(CapabilityContext context);


    interface CapabilityContext {

        /**
         * 加载已安装的资源
         *
         * @return InstalledResource
         */
        Flux<InstalledResource> loadInstallResources();

        /**
         * 获取安装包
         *
         * @return 安装包
         */
        CapabilityPackage pkg();

        /**
         * 监控对象,用于打印日志等操作
         */
        Monitor monitor();

    }
}
