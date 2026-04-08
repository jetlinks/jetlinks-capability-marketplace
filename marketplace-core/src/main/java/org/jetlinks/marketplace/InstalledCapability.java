package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.marketplace.enums.CapabilityState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 已安装能力信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class InstalledCapability implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String capabilityId;
    private String name;
    private String type;
    private String version;

    private Map<String, Object> metadata;

    private List<InstalledResource> resources;
}
