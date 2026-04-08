package org.jetlinks.marketplace.client.configuration;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.marketplace.spi.CapabilityProvider;
import org.jetlinks.marketplace.spi.CapabilityProviders;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * 将 Spring 容器中所有 {@link CapabilityProvider} 注册到 {@link CapabilityProviders}.
 *
 * @author zhouhao
 * @since 2.12
 */
@Slf4j
public class CapabilityProviderRegistrar implements ApplicationContextAware, SmartInitializingSingleton {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<CapabilityProvider> providers = context
            .getBeanProvider(CapabilityProvider.class)
            .stream()
            .toList();
        for (CapabilityProvider p : providers) {
            CapabilityProviders.register(p);
        }
    }
}
