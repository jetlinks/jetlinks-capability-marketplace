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
public class CapabilityVersion implements Serializable, Comparable<CapabilityVersion> {

    @Serial
    private static final long serialVersionUID = 1L;

    private String version;

    private String summary;

    private String releaseNotes;
    private String minPlatformVersion;
    private long releaseTime;
    private long size;
    private String checksum;
    private boolean available = true;

    public Version version() {
        return Version.parseNullable(version);
    }

    public Version minPlatformVersion() {
        return Version.parseNullable(minPlatformVersion);
    }

    @Override
    public int compareTo(CapabilityVersion other) {
        return Version.compare(version, other == null ? null : other.version);
    }
}
