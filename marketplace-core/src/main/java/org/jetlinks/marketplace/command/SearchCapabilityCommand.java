package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.GenericInputCommand;
import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import reactor.core.publisher.Flux;

/**
 * 搜索能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class SearchCapabilityCommand extends AbstractCommand<Flux<CapabilityInfo>, SearchCapabilityCommand>
    implements GenericInputCommand<CapabilitySearchRequest> {

    public CapabilitySearchRequest asRequest() {
        return as(CapabilitySearchRequest.class);
    }

    public SearchCapabilityCommand with(CapabilitySearchRequest request) {
        return with((Object) request);
    }
}
