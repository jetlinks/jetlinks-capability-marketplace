package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 能力包水印信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class WatermarkInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String tenantId;
    private String userId;
    private String instanceId;
    private String licenseId;
    private long timestamp;
    private String traceId;
}
