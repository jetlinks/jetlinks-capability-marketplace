package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.GenericInputCommand;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Flux;

/**
 * 查询已安装能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class QueryInstalledCapabilityCommand
    extends AbstractCommand<Flux<InstalledCapability>, QueryInstalledCapabilityCommand> implements
    GenericInputCommand<CapabilitySearchRequest> {

    public CapabilitySearchRequest asRequest(){
        return as(CapabilitySearchRequest.class);
    }

    public QueryInstalledCapabilityCommand with(CapabilitySearchRequest request){
        return with((Object) request);
    }
}
