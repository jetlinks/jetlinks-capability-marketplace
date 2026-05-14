package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 能力操作事件.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
@NoArgsConstructor
public class CapabilityOperationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_INSTALL_KEY = "default";

    private String operationId;

    private Type type;

    private String capabilityId;

    private String version;

    private String installKey = DEFAULT_INSTALL_KEY;

    private String message;

    private String errorMessage;

    private String level;

    private String traceId;

    private Long timestamp;

    private InstalledResource resource;

    private List<InstalledResource> resources;

    public static CapabilityOperationEvent of(Type type,
                                              String capabilityId,
                                              String version) {
        CapabilityOperationEvent event = new CapabilityOperationEvent();
        event.setType(type);
        event.setCapabilityId(capabilityId);
        event.setVersion(version);
        event.setInstallKey(DEFAULT_INSTALL_KEY);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        download,
        installing,
        action,
        progress,
        log,
        success,
        failed;
    }

}
