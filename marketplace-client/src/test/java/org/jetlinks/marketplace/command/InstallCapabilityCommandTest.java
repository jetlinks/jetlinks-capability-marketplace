package org.jetlinks.marketplace.command;

import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallCapabilityCommandTest {

    @Test
    void shouldCarryInstallConfiguration() {
        InstallCapabilityCommand command = new InstallCapabilityCommand()
            .setCapabilityId("cap-1")
            .setVersion("1.0.0")
            .setConfiguration(Map.of("key", "value"));

        assertEquals("cap-1", command.getCapabilityId());
        assertEquals("1.0.0", command.getVersion());
        assertEquals("value", command.getConfiguration().get("key"));
    }

    @Test
    void shouldCarryInstallRequestAndKeepConfigurationCompatible() {
        CapabilityInstallRequest request = new CapabilityInstallRequest();
        request.setConfiguration(Map.of("key", "value"));
        CapabilityInstallRequest.UpgradeOptions upgrade = new CapabilityInstallRequest.UpgradeOptions();
        upgrade.setTargetDataIds(List.of("skill-1"));
        request.setUpgrade(upgrade);
        CapabilityInstallRequest.Operator operator = new CapabilityInstallRequest.Operator();
        operator.setId("user-1");
        operator.setName("用户一");
        operator.setUsername("user");
        request.setOperator(operator);

        InstallCapabilityCommand command = new InstallCapabilityCommand()
            .setCapabilityId("cap-2")
            .setVersion("1.0.1")
            .setRequest(request);

        assertEquals("value", command.getConfiguration().get("key"));
        assertEquals("user-1", command.getRequest().getEffectiveOperator().getId());
        assertTrue(command.getRequest().hasUpgradeOptions());
        assertEquals(List.of("skill-1"), command.getRequest().getEffectiveUpgrade().getTargetDataIds());

        command.setConfiguration(Map.of("key", "value-2"));

        assertEquals("value-2", command.getConfiguration().get("key"));
        assertEquals("value-2", command.getRequest().getConfiguration().get("key"));
        assertFalse(command.getRequest().getEffectiveUpgrade().getTargetDataIds().isEmpty());
    }
}
