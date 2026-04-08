package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Mono;

/**
 * 升级能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class UpgradeCapabilityCommand extends AbstractCommand<Mono<InstalledCapability>, UpgradeCapabilityCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public UpgradeCapabilityCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }

    public String getTargetVersion() {
        return getOrNull("targetVersion", String.class);
    }

    public UpgradeCapabilityCommand setTargetVersion(String targetVersion) {
        return with("targetVersion", targetVersion);
    }
}
