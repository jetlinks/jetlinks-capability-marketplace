package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 能力包.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityPackage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private CapabilityInfo info;

    private String version;

    private List<CapabilityResource> resources;

}
