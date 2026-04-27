package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityAvailability;
import reactor.core.publisher.Mono;

/**
 * 获取能力可用性信息命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class GetCapabilityAvailabilityCommand extends AbstractCommand<Mono<CapabilityAvailability>, GetCapabilityAvailabilityCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public GetCapabilityAvailabilityCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }
}
