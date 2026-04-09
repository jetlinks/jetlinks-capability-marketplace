package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityTag;
import reactor.core.publisher.Flux;

/**
 * 获取能力标签命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class GetCapabilityTagsCommand extends AbstractCommand<Flux<CapabilityTag>, GetCapabilityTagsCommand> {

    public String getClassifierId() {
        return getOrNull("classifierId", String.class);
    }

    public GetCapabilityTagsCommand setClassifierId(String classifierId) {
        return with("classifierId", classifierId);
    }
}
