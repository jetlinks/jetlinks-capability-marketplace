package org.jetlinks.marketplace.client.spi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 已安装能力资源过滤上下文.
 *
 * @author zhouhao
 * @since 2.12
 */
public record CapabilityInstalledResourceFilterContext(String capabilityId,
                                                       String type,
                                                       Collection<String> dataIds,
                                                       Collection<String> resourceIds) {

    public CapabilityInstalledResourceFilterContext {
        dataIds = dataIds == null ? List.of() : new ArrayList<>(dataIds);
        resourceIds = resourceIds == null ? List.of() : new ArrayList<>(resourceIds);
    }

    public static CapabilityInstalledResourceFilterContext capability(String capabilityId) {
        return new CapabilityInstalledResourceFilterContext(capabilityId, null, null, null);
    }

    public static CapabilityInstalledResourceFilterContext data(String type, Collection<String> dataIds) {
        return new CapabilityInstalledResourceFilterContext(null, type, dataIds, null);
    }

    public static CapabilityInstalledResourceFilterContext resource(String type,
                                                                    String capabilityId,
                                                                    Collection<String> resourceIds) {
        return new CapabilityInstalledResourceFilterContext(capabilityId, type, null, resourceIds);
    }
}
