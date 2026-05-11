package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 安装能力命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class InstallCapabilityCommand extends AbstractCommand<Flux<ProgressState<InstalledResource>>, InstallCapabilityCommand> {

    public String getCapabilityId() {
        return getOrNull("capabilityId", String.class);
    }

    public InstallCapabilityCommand setCapabilityId(String capabilityId) {
        return with("capabilityId", capabilityId);
    }

    public String getVersion() {
        return getOrNull("version", String.class);
    }

    public InstallCapabilityCommand setVersion(String version) {
        return with("version", version);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getConfiguration() {
        return (Map<String, Object>) getOrNull("configuration", Map.class);
    }

    public InstallCapabilityCommand setConfiguration(Map<String, Object> configuration) {
        return with("configuration", configuration);
    }
}
