package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Flux;

/**
 * 查询已安装能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class QueryInstalledCapabilityCommand extends AbstractCommand<Flux<InstalledCapability>, QueryInstalledCapabilityCommand> {

    public String getCapabilityType() {
        return getOrNull("capabilityType", String.class);
    }

    public QueryInstalledCapabilityCommand setCapabilityType(String capabilityType) {
        return with("capabilityType", capabilityType);
    }
}
