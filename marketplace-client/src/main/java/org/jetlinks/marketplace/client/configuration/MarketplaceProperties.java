package org.jetlinks.marketplace.client.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 能力市场 SDK 配置.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "jetlinks.marketplace")
public class MarketplaceProperties {

    private boolean enabled = true;
    /**
     * 市场服务根 URL
     */
    private String serverUrl = "https://cloud.jetlinks.cn";

    /**
     * 访问密钥
     */
    private String secureKey = "";


}
