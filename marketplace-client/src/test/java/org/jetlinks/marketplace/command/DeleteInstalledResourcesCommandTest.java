package org.jetlinks.marketplace.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeleteInstalledResourcesCommandTest {

    @Test
    void shouldCarryTypeAndDataIds() {
        DeleteInstalledResourcesCommand command = new DeleteInstalledResourcesCommand()
            .setType("toolkit")
            .setDataIds(List.of("group-1", "group-2"));

        assertEquals("toolkit", command.getType());
        assertEquals(List.of("group-1", "group-2"), command.getDataIds());
    }
}
