package org.jetlinks.marketplace.client.configuration;

import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.client.command.CapabilityMarketplaceCommandSupport;
import org.jetlinks.marketplace.command.DownloadCapabilityPackageCommand;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.jetlinks.pro.command.CommandSupportManagerProvider;
import org.jetlinks.pro.command.CommandSupportManagerProviders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceClientCommandSupportRegistrationTest {

    @AfterEach
    void tearDown() {
        CommandSupportManagerProvider.supports.unregister(CapabilityMarketplaceCommandSupport.SERVICE_ID);
    }

    @Test
    void shouldRegisterMarketplaceCommandSupportProvider() {
        CapabilityMarketplaceClient client = mock(CapabilityMarketplaceClient.class);
        CapabilityPackage capabilityPackage = new CapabilityPackage();
        capabilityPackage.setVersion("1.0.0");
        when(client.download(any(), any())).thenReturn(Mono.just(capabilityPackage));

        MarketplaceClientConfiguration.MarketplaceClientCommandSupportConfiguration configuration =
            new MarketplaceClientConfiguration.MarketplaceClientCommandSupportConfiguration();
        CapabilityMarketplaceCommandSupport commandSupport = configuration.capabilityMarketplaceCommandSupport(client);
        CommandSupportManagerProviders.register(
            configuration.capabilityMarketplaceCommandSupportManagerProvider(commandSupport)
        );

        assertThat(
            CommandSupportManagerProviders
                .getCommandSupport(CapabilityMarketplaceCommandSupport.SERVICE_ID)
                .flatMapMany(support -> support.getCommandMetadata())
                .collectList()
                .block()
        ).isNotEmpty();

        assertThat(
            CommandSupportManagerProviders
                .getCommandSupport(CapabilityMarketplaceCommandSupport.SERVICE_ID)
                .flatMap(support -> support.execute(
                    new DownloadCapabilityPackageCommand()
                        .setCapabilityId("cap-1")
                        .setVersion("1.0.0")
                ))
                .block()
        ).isSameAs(capabilityPackage);
    }

    @Test
    void shouldExposeCommandSupportBeanMethod() throws NoSuchMethodException {
        assertThat(
            MarketplaceClientConfiguration.MarketplaceClientCommandSupportConfiguration.class.getDeclaredMethod(
                "capabilityMarketplaceCommandSupport",
                CapabilityMarketplaceClient.class
            )
        ).isNotNull();

        assertThat(
            MarketplaceClientConfiguration.MarketplaceClientCommandSupportConfiguration.class.getDeclaredMethod(
                "capabilityMarketplaceCommandSupportManagerProvider",
                CapabilityMarketplaceCommandSupport.class
            )
        ).isNotNull();
    }
}
