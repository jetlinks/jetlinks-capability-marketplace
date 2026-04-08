package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 能力版本信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String version;
    private String releaseNotes;
    private String minPlatformVersion;
    private long releaseTime;
    private long size;
    private String checksum;
}
