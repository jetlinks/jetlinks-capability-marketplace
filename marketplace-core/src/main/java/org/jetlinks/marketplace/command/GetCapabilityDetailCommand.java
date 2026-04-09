package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityInfo;
import reactor.core.publisher.Mono;

/**
 * 获取能力详情命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class GetCapabilityDetailCommand extends AbstractCommand<Mono<CapabilityInfo>, GetCapabilityDetailCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public GetCapabilityDetailCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }
}
