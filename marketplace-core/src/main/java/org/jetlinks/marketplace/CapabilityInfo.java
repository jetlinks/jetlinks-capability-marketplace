package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.marketplace.enums.LicenseType;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 能力信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /** 能力提供商，对应 {@link org.jetlinks.marketplace.spi.CapabilityProvider#getId()} */
    private String provider;

    private String currentVersion;
    private String description;
    private String author;
    private String icon;
    private List<CapabilityTagInfo> tags;

    private String classifier;
    private List<CapabilityDependency> dependencies;

    private String minPlatformVersion;

    private Map<String, Object> metadata;
}
