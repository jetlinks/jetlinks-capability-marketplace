package org.jetlinks.marketplace.client.configuration;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.crud.annotation.EnableEasyormRepository;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.impl.DefaultCapabilityResourceManager;
import org.jetlinks.marketplace.client.impl.HttpCapabilityMarketplaceClient;
import org.jetlinks.marketplace.client.web.MarketplaceClientController;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 能力市场 SDK 自动配置.
 *
 * @author zhouhao
 * @since 2.12
 */
@AutoConfiguration
@EnableConfigurationProperties(MarketplaceProperties.class)
@EnableEasyormRepository("org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity")
@ConditionalOnProperty(prefix = "jetlinks.marketplace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketplaceClientConfiguration {

    @Bean
    public CapabilityProviderRegistrar capabilityProviderRegistrar() {
        return new CapabilityProviderRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityMarketplaceClient capabilityMarketplaceClient(
        WebClient.Builder builder,
        MarketplaceProperties properties) {
        return new HttpCapabilityMarketplaceClient(builder, properties);
    }


    @Bean
    @ConditionalOnMissingBean(CapabilityResourceManager.class)
    public DefaultCapabilityResourceManager capabilityResourceManager(CapabilityMarketplaceClient client,
                                                                      ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository) {
        return new DefaultCapabilityResourceManager(client, resourceRepository);
    }


    @Bean
    public MarketplaceClientController marketplaceClientController(CapabilityMarketplaceClient client,
                                                                   CapabilityResourceManager resourceManager) {
        return new MarketplaceClientController(client, resourceManager);
    }


}
