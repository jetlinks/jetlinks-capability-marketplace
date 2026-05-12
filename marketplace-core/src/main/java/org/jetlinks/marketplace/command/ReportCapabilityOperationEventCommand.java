package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityOperationEvent;
import reactor.core.publisher.Mono;

/**
 * 上报能力操作事件命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class ReportCapabilityOperationEventCommand
    extends AbstractCommand<Mono<Void>, ReportCapabilityOperationEventCommand> {

    public CapabilityOperationEvent getEvent() {
        return getOrNull("event", CapabilityOperationEvent.class);
    }

    public ReportCapabilityOperationEventCommand setEvent(CapabilityOperationEvent event) {
        return with("event", event);
    }
}
