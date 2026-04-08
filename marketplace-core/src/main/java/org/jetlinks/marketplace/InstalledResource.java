package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstalledResource {

    private String type;

    private String capabilityId;

    private String resourceId;

    private String dataId;

    private String version;
}
