package org.jetlinks.marketplace.client.entity;

import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.InstalledResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityResourceInstallEntityTest {

    @Test
    void shouldCopyBindingFieldsFromInstalledResource() {
        CapabilityPackage pkg = capabilityPackage("cap-1", "1.0.0");
        InstalledResource resource = installedResource("tool", "resource-1", "data-1");

        CapabilityResourceInstallEntity entity = CapabilityResourceInstallEntity.from(resource, pkg);

        assertThat(entity.getType()).isEqualTo("tool");
        assertThat(entity.getCapabilityId()).isEqualTo("cap-1");
        assertThat(entity.getProviderId()).isNull();
        assertThat(entity.getResourceId()).isEqualTo("resource-1");
        assertThat(entity.getDataId()).isEqualTo("data-1");
        assertThat(entity.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void shouldPreferResourceVersionOverPackageVersion() {
        InstalledResource resource = installedResource("tool", "resource-1", "data-1");
        resource.setVersion("2.0.0");

        CapabilityResourceInstallEntity entity = CapabilityResourceInstallEntity.from(resource, capabilityPackage("cap-1", "1.0.0"));

        assertThat(entity.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void shouldExposeAuditFieldsWhenConvertingToResourceDetail() {
        CapabilityResourceInstallEntity entity = new CapabilityResourceInstallEntity();
        entity.setType("tool");
        entity.setCapabilityId("cap-1");
        entity.setProviderId("provider-1");
        entity.setResourceId("resource-1");
        entity.setDataId("data-1");
        entity.setVersion("1.0.0");
        entity.setCreatorId("creator-1");
        entity.setCreateTime(10L);
        entity.setModifierId("modifier-1");
        entity.setModifyTime(20L);

        InstalledResource resource = entity.toResource();

        assertThat(resource).isInstanceOf(InstalledResourceDetail.class);
        InstalledResourceDetail detail = (InstalledResourceDetail) resource;
        assertThat(detail.getProviderId()).isEqualTo("provider-1");
        assertThat(detail.getCreatorId()).isEqualTo("creator-1");
        assertThat(detail.getCreateTime()).isEqualTo(10L);
        assertThat(detail.getModifierId()).isEqualTo("modifier-1");
        assertThat(detail.getModifyTime()).isEqualTo(20L);
    }

    private static CapabilityPackage capabilityPackage(String capabilityId, String version) {
        CapabilityInfo info = new CapabilityInfo();
        info.setId(capabilityId);

        CapabilityPackage pkg = new CapabilityPackage();
        pkg.setInfo(info);
        pkg.setVersion(version);
        return pkg;
    }

    private static InstalledResource installedResource(String type, String resourceId, String dataId) {
        InstalledResource resource = new InstalledResource();
        resource.setType(type);
        resource.setResourceId(resourceId);
        resource.setDataId(dataId);
        return resource;
    }
}
