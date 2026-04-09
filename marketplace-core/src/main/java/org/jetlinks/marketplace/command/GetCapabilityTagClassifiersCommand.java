package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityTagClassifier;
import reactor.core.publisher.Flux;

/**
 * 获取能力标签分类命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class GetCapabilityTagClassifiersCommand extends AbstractCommand<Flux<CapabilityTagClassifier>, GetCapabilityTagClassifiersCommand> {

    public String getType() {
        return getOrNull("type", String.class);
    }

    public GetCapabilityTagClassifiersCommand setType(String type) {
        return with("type", type);
    }
}
