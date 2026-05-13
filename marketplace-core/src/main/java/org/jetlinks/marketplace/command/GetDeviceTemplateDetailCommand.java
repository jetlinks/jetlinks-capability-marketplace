package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Get marketplace device template detail.
 *
 * @author JetLinks
 * @since 2.12
 */
public class GetDeviceTemplateDetailCommand
    extends AbstractCommand<Mono<Map<String, Object>>, GetDeviceTemplateDetailCommand> {

    public String getTemplateId() {
        return getOrNull("templateId", String.class);
    }

    public GetDeviceTemplateDetailCommand setTemplateId(String templateId) {
        return with("templateId", templateId);
    }

    public String getResourceId() {
        return getOrNull("resourceId", String.class);
    }

    public GetDeviceTemplateDetailCommand setResourceId(String resourceId) {
        return with("resourceId", resourceId);
    }
}
