package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CapabilityInstallRequest implements Serializable {

    private Map<String, Object> configuration;

    private UpgradeOptions upgrade;

    public Map<String, Object> getConfiguration() {
        return configuration == null ? Map.of() : configuration;
    }

    public UpgradeOptions getUpgrade() {
        return upgrade;
    }

    public UpgradeOptions getEffectiveUpgrade() {
        return upgrade == null ? new UpgradeOptions() : upgrade;
    }

    public boolean hasUpgradeOptions() {
        return upgrade != null;
    }

    public static CapabilityInstallRequest ofConfiguration(Map<String, Object> configuration) {
        CapabilityInstallRequest request = new CapabilityInstallRequest();
        request.setConfiguration(configuration);
        return request;
    }

    @Getter
    @Setter
    public static class UpgradeOptions implements Serializable {

        private List<String> targetDataIds;

        private Boolean removeAbsentResources;

        public List<String> getTargetDataIds() {
            return targetDataIds == null ? List.of() : List.copyOf(targetDataIds);
        }

        public boolean hasTargetDataIds() {
            return targetDataIds != null && !targetDataIds.isEmpty();
        }

        public boolean isRemoveAbsentResources() {
            return !Boolean.FALSE.equals(removeAbsentResources);
        }
    }
}
