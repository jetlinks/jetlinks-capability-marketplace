package org.jetlinks.marketplace.client.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.api.crud.entity.GenericEntity;
import org.jetlinks.marketplace.InstalledResource;

import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * 能力资源实体.
 * 用于存储已安装的能力资源信息，包括资源类型、关联的能力ID、资源ID、数据ID和版本等信息.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
@Table(name = "capability_resource_install", indexes = {
    @Index(name = "idx_crs_cap_id", columnList = "capability_id"),
    @Index(name = "idx_crs_d_id", columnList = "type,data_id")
})
@Schema(description = "能力资源安装")
public class CapabilityResourceInstallEntity extends GenericEntity<String> {

    /**
     * 资源类型
     */
    @Column(length = 64, nullable = false)
    private String type;

    /**
     * 能力ID
     */
    @Column(length = 64, nullable = false)
    private String capabilityId;

    /**
     * 资源ID
     */
    @Column(length = 64, nullable = false)
    private String resourceId;

    /**
     * 数据ID
     */
    @Column(length = 64, nullable = false)
    private String dataId;

    /**
     * 版本号
     */
    @Column(length = 32)
    private String version;


    public static CapabilityResourceInstallEntity from(InstalledResource resource) {
        return new CapabilityResourceInstallEntity()
            .copyFrom(resource);
    }

    public InstalledResource toResource() {
        return copyTo(new InstalledResource());
    }

}
