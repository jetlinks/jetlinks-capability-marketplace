package org.jetlinks.marketplace.command;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
