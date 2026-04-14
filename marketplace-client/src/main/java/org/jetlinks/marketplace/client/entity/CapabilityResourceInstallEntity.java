package org.jetlinks.marketplace.client.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.ezorm.rdb.mapping.annotation.ColumnType;
import org.hswebframework.ezorm.rdb.mapping.annotation.DefaultValue;
import org.hswebframework.ezorm.rdb.mapping.annotation.JsonCodec;
import org.hswebframework.web.api.crud.entity.ExtendableEntity;
import org.hswebframework.web.api.crud.entity.RecordCreationEntity;
import org.hswebframework.web.api.crud.entity.RecordModifierEntity;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.crud.generator.Generators;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledResource;

import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.Table;
import java.sql.JDBCType;
import java.util.List;

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
@Schema(description = "能力资源安装信息")
public class CapabilityResourceInstallEntity extends ExtendableEntity<String> implements RecordCreationEntity, RecordModifierEntity {

    /**
     * 资源类型
     */
    @Column(length = 64, nullable = false)
    @Schema(description = "资源类型")
    private String type;

    /**
     * 能力ID
     */
    @Column(length = 64, nullable = false)
    @Schema(description = "能力ID")
    private String capabilityId;

    /**
     * 资源ID
     */
    @Column(length = 64, nullable = false)
    @Schema(description = "资源ID")
    private String resourceId;

    /**
     * 数据ID
     */
    @Column(length = 64, nullable = false)
    @Schema(description = "数据ID")
    private String dataId;

    /**
     * 版本号
     */
    @Column(length = 32)
    @Schema(description = "版本号")
    private String version;

    @Column
    @Schema(description = "依赖资源列表")
    @ColumnType(javaType = String.class, jdbcType = JDBCType.LONGVARCHAR)
    @JsonCodec
    private List<String> dependencies;

    /**
     * 创建人ID
     */
    @Column(length = 64, updatable = false)
    @Schema(description = "创建人ID", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String creatorId;

    /**
     * 创建时间
     */
    @Column(updatable = false)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long createTime;

    /**
     * 修改人ID
     */
    @Column(length = 64)
    @Schema(description = "修改人ID", accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String modifierId;

    /**
     * 修改时间
     */
    @Column
    @Schema(description = "修改时间", accessMode = Schema.AccessMode.READ_ONLY)
    @DefaultValue(generator = Generators.CURRENT_TIME)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long modifyTime;

    public CapabilityResourceInstallEntity copyFrom(InstalledResource resource) {
        FastBeanCopier.copy(resource, this);
        return this;
    }

    public <T extends InstalledResource> T copyTo(T resource) {
        FastBeanCopier.copy(this, resource);
        return resource;
    }

    public static CapabilityResourceInstallEntity from(InstalledResource resource, CapabilityPackage capabilityPackage) {
        CapabilityResourceInstallEntity entity = new CapabilityResourceInstallEntity()
            .copyFrom(resource);
        entity.setCapabilityId(capabilityPackage.getInfo().getId());
        if (entity.getVersion() == null) {
            entity.setVersion(capabilityPackage.getVersion());
        }

        return entity;
    }

    public InstalledResource toResource() {
        return copyTo(new InstalledResource());
    }

}
