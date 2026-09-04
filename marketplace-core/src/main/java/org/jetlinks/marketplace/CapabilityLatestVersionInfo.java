package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.util.Map;

/**
 * 携带最新版本的能力信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityLatestVersionInfo extends CapabilityInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 最新版本信息。 */
    private CapabilityVersion version;

    /** 资源附件信息。 */
    private Map<String, Object> attachment;
}
