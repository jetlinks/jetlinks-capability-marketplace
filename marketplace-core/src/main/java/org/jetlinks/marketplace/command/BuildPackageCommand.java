package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityPackage;
import reactor.core.publisher.Mono;

/**
 * 打包能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class BuildPackageCommand extends AbstractCommand<Mono<CapabilityPackage>, BuildPackageCommand> {

    public String getProviderType() {
        return getOrNull("providerType", String.class);
    }

    public BuildPackageCommand setProviderType(String providerType) {
        return with("providerType", providerType);
    }
}
