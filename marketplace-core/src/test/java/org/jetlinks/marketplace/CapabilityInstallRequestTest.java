package org.jetlinks.marketplace;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilityInstallRequestTest {

    @Test
    public void shouldExposeEmptyDefaults() {
        CapabilityInstallRequest request = new CapabilityInstallRequest();

        assertEquals(Map.of(), request.getConfiguration());
        assertFalse(request.hasUpgradeOptions());
        assertEquals(List.of(), request.getEffectiveUpgrade().getTargetDataIds());
        assertTrue(request.getEffectiveUpgrade().isRemoveAbsentResources());
    }

    @Test
    public void shouldKeepConfigurationAndUpgradeOptions() {
        CapabilityInstallRequest request = CapabilityInstallRequest.ofConfiguration(Map.of(
            "id", "group-1",
            "code", "device_ops"
        ));
        CapabilityInstallRequest.UpgradeOptions upgrade = new CapabilityInstallRequest.UpgradeOptions();
        upgrade.setTargetDataIds(List.of("skill-1"));
        upgrade.setRemoveAbsentResources(false);
        request.setUpgrade(upgrade);

        assertEquals("group-1", request.getConfiguration().get("id"));
        assertEquals("device_ops", request.getConfiguration().get("code"));
        assertTrue(request.hasUpgradeOptions());
        assertEquals(List.of("skill-1"), request.getEffectiveUpgrade().getTargetDataIds());
        assertFalse(request.getEffectiveUpgrade().isRemoveAbsentResources());
    }
}
