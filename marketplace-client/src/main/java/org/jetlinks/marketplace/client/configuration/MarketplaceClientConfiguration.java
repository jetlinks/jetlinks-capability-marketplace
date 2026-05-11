package org.jetlinks.marketplace.client.configuration;

import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.crud.annotation.EnableEasyormRepository;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.client.entity.CapabilityResourceInstallEntity;
import org.jetlinks.marketplace.client.impl.DefaultCapabilityResourceManager;
import org.jetlinks.marketplace.client.impl.HttpCapabilityMarketplaceClient;
import org.jetlinks.marketplace.client.spi.CapabilityInstalledResourceInterceptor;
import org.jetlinks.marketplace.client.web.MarketplaceClientController;
import org.jetlinks.marketplace.client.web.MarketplaceClientResourceController;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

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
                                                                      ReactiveRepository<CapabilityResourceInstallEntity, String> resourceRepository,
                                                                      List<CapabilityInstalledResourceInterceptor> installedResourceInterceptors) {
        return new DefaultCapabilityResourceManager(client, resourceRepository, installedResourceInterceptors);
    }


    @Bean
    @ConditionalOnMissingBean(name = "marketplacePublicController")
    @ConditionalOnProperty(
        prefix = "jetlinks.marketplace.controller",
        name = "public-api-enabled",
        havingValue = "true",
        matchIfMissing = true)
    public MarketplaceClientController marketplaceClientController(CapabilityMarketplaceClient client) {
        return new MarketplaceClientController(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public MarketplaceClientResourceController marketplaceClientResourceController(CapabilityResourceManager resourceManager) {
        return new MarketplaceClientResourceController(resourceManager);
    }


}
