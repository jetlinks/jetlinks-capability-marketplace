package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.GenericInputCommand;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 检查能力更新命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class CheckCapabilityUpdatesCommand
    extends AbstractCommand<Flux<CapabilityInfo>, CheckCapabilityUpdatesCommand>
    implements GenericInputCommand<List<InstalledCapability>> {

    @SuppressWarnings("unchecked")
    public List<InstalledCapability> getInstalled() {
        return getOrNull("inputs", List.class);
    }

}
