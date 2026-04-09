package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
public class InstalledResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String type;

    private String capabilityId;

    private String resourceId;

    private String dataId;

    private String version;
}
