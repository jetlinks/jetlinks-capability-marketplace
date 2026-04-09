package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityVersion;
import reactor.core.publisher.Flux;

/**
 * 获取能力版本命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class GetCapabilityVersionsCommand extends AbstractCommand<Flux<CapabilityVersion>, GetCapabilityVersionsCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public GetCapabilityVersionsCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }
}
