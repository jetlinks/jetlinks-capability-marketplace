package org.jetlinks.marketplace.spi;


import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CapabilityProvider} 注册表
 *
 * @author zhouhao
 * @since 2.12
 */
public final class CapabilityProviders {

    private static final Map<String, CapabilityProvider> BY_TYPE_ID = new ConcurrentHashMap<>();

    private CapabilityProviders() {
    }

    public static void register(CapabilityProvider provider) {
        BY_TYPE_ID.put(provider.getId(), provider);
    }

    public static void unregister(String capabilityTypeId) {
        BY_TYPE_ID.remove(capabilityTypeId);
    }

    public static Optional<CapabilityProvider> get(String id) {
        return Optional.ofNullable(BY_TYPE_ID.get(id));
    }

    public static CapabilityProvider getOrThrow(String id) {
        return get(id)
            .orElseThrow(() -> new UnsupportedOperationException("unsupported provider " + id));
    }

    public static Collection<CapabilityProvider> all() {
        return BY_TYPE_ID.values();
    }

    public static void clear() {
        BY_TYPE_ID.clear();
    }
}
