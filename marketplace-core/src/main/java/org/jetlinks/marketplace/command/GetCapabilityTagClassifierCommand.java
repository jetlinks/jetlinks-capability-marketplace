package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityTagClassifier;
import reactor.core.publisher.Mono;

/**
 * 获取能力标签分类详情命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class GetCapabilityTagClassifierCommand extends AbstractCommand<Mono<CapabilityTagClassifier>, GetCapabilityTagClassifierCommand> {

    public String getType() {
        return getOrNull("type", String.class);
    }

    public GetCapabilityTagClassifierCommand setType(String type) {
        return with("type", type);
    }
}
