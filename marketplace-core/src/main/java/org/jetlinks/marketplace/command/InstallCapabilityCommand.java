package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Mono;

/**
 * 安装能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class InstallCapabilityCommand extends AbstractCommand<Mono<InstalledCapability>, InstallCapabilityCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public InstallCapabilityCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }

    public String getVersion() {
        return getOrNull("version", String.class);
    }

    public InstallCapabilityCommand setVersion(String version) {
        return with("version", version);
    }
}
