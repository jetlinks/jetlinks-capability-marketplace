package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 能力依赖关系.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityDependency implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String capabilityId;

    /** 兼容版本范围，如 {@code ">=1.0.0,<2.0.0"} */
    private String versionRange;

    private boolean optional;
}
