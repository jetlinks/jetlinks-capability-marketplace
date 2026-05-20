package org.jetlinks.marketplace.spi;

import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledCapability;
import org.jetlinks.marketplace.InstalledResource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

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
    Flux<InstalledResource> install(CapabilityContext context);


    interface CapabilityContext {

        /**
         * 加载已安装的资源
         *
         * @return InstalledResource
         */
        Flux<InstalledResource> loadInstallResources();

        /**
         * 加载当前安装包依赖能力的已安装资源.
         *
         * @return InstalledResource
         */
        default Flux<InstalledResource> loadDependencyResources() {
            return Flux.empty();
        }

        /**
         * 按资源类型加载当前安装包依赖能力的已安装资源.
         *
         * @param type 资源类型
         * @return InstalledResource
         */
        default Flux<InstalledResource> loadDependencyResources(String type) {
            return loadDependencyResources()
                .filter(resource -> type.equals(resource.getType()));
        }

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

        Map<String,Object> configuration();

        default CapabilityInstallRequest request() {
            return CapabilityInstallRequest.ofConfiguration(configuration());
        }
    }
}
