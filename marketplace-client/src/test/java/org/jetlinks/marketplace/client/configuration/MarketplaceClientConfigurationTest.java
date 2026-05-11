package org.jetlinks.marketplace.client.configuration;

import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class MarketplaceClientConfigurationTest {

    @Test
    void shouldOnlyConditionPublicProxyController() throws NoSuchMethodException {
        Method publicProxyController = MarketplaceClientConfiguration.class.getDeclaredMethod(
            "marketplaceClientController",
            CapabilityMarketplaceClient.class);

        ConditionalOnMissingBean missingBean =
            publicProxyController.getAnnotation(ConditionalOnMissingBean.class);
        ConditionalOnProperty property =
            publicProxyController.getAnnotation(ConditionalOnProperty.class);

        assertThat(missingBean).isNotNull();
        assertThat(missingBean.name()).containsExactly("marketplacePublicController");
        assertThat(property).isNotNull();
        assertThat(property.prefix()).isEqualTo("jetlinks.marketplace.controller");
        assertThat(property.name()).containsExactly("public-api-enabled");
        assertThat(property.havingValue()).isEqualTo("true");
        assertThat(property.matchIfMissing()).isTrue();

        Method resourceController = MarketplaceClientConfiguration.class.getDeclaredMethod(
            "marketplaceClientResourceController",
            CapabilityResourceManager.class);

        assertThat(resourceController.getAnnotation(ConditionalOnProperty.class)).isNull();
    }

    @Test
    void shouldEnablePublicProxyControllerByDefault() {
        MarketplaceProperties properties = new MarketplaceProperties();

        assertThat(properties.getController().isPublicApiEnabled()).isTrue();
    }
}
