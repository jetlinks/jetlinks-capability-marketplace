package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.GenericInputCommand;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Search device templates exposed by the marketplace.
 *
 * @author JetLinks
 * @since 2.12
 */
public class SearchDeviceTemplatesCommand
    extends AbstractCommand<Mono<Map<String, Object>>, SearchDeviceTemplatesCommand>
    implements GenericInputCommand<Map<String, Object>> {

    @SuppressWarnings("unchecked")
    public Map<String, Object> asRequest() {
        return as(Map.class);
    }

    public SearchDeviceTemplatesCommand withRequest(Map<String, Object> request) {
        return with((Object) request);
    }
}
