package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 能力包中的单个资源项.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityResource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String type;

    private Map<String, Object> metadata;
}
