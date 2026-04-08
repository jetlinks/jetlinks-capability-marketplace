package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Mono;

/**
 * 卸载能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class UninstallCapabilityCommand extends AbstractCommand<Mono<Void>, UninstallCapabilityCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public UninstallCapabilityCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }
}
