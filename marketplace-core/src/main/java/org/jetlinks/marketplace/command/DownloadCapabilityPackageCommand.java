package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.CapabilityPackage;
import reactor.core.publisher.Mono;

/**
 * 下载能力包命令.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public class DownloadCapabilityPackageCommand extends AbstractCommand<Mono<CapabilityPackage>, DownloadCapabilityPackageCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public DownloadCapabilityPackageCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }

    public String getVersion() {
        return getOrNull("version", String.class);
    }

    public DownloadCapabilityPackageCommand setVersion(String version) {
        return with("version", version);
    }
}
