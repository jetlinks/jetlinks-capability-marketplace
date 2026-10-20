package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

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

    /** 市场版本发布时间。 */
    private Long publishTime;
    /** 市场版本扩展信息。 */
    private Map<String, Object> others;
    /** 当前版本的直接依赖详情。 */
    private List<CapabilityLatestVersionInfo> dependencyDetails;
    /**
     * 当前版本能力包中的资源，仅在查询请求显式要求时返回。
     * <p>
     * 该字段用于读取包内展示元数据；安装仍应通过 {@link CapabilityPackage} 获取原始能力包。
     */
    private List<CapabilityResource> resources;

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
