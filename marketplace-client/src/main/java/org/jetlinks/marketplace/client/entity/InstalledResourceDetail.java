package org.jetlinks.marketplace.client.entity;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.marketplace.InstalledResource;

/**
 * 已安装资源详情，包含审计字段。
 */
@Getter
@Setter
public class InstalledResourceDetail extends InstalledResource {
    private String creatorId;

    private Long createTime;

    private String modifierId;

    private Long modifyTime;
}
