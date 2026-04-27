package org.jetlinks.marketplace.spi;

import org.jetlinks.marketplace.CapabilityInfo;
import org.jetlinks.marketplace.CapabilityAvailability;
import org.jetlinks.marketplace.CapabilityPackage;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import org.jetlinks.marketplace.CapabilityTag;
import org.jetlinks.marketplace.CapabilityTagClassifier;
import org.jetlinks.marketplace.CapabilityVersion;
import org.jetlinks.marketplace.InstalledCapability;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 能力市场服务.
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface CapabilityMarketplaceClient {

    Flux<CapabilityInfo> search(CapabilitySearchRequest request);

    Mono<CapabilityInfo> getDetail(String capabilityId);

    default Mono<CapabilityAvailability> checkAvailability(String capabilityId) {
        return getDetail(capabilityId)
            .map(detail -> {
                CapabilityAvailability availability = new CapabilityAvailability();
                availability.setCapabilityId(capabilityId);
                availability.setAvailable(detail.isAvailable());
                availability.setUseCondition(detail.getUseCondition());
                if (!detail.isAvailable() && detail.getUseCondition() != null) {
                    switch (detail.getUseCondition()) {
                        case registered -> availability.setReasonCode(CapabilityAvailability.REASON_LOGIN_REQUIRED);
                        case needPurchase -> {
                            availability.setReasonCode(CapabilityAvailability.REASON_PURCHASE_REQUIRED);
                            availability.setPurchaseUrl("/marketplace/capabilities/" + capabilityId + "/orders");
                        }
                        default -> {
                        }
                    }
                }
                return availability;
            });
    }

    Flux<CapabilityVersion> getVersions(String capabilityId);

    Mono<CapabilityPackage> download(String capabilityId, String version);

    Flux<CapabilityInfo> checkUpdates(List<InstalledCapability> installed);

    /**
     * 获取标签分类.
     *
     * @param type 类型
     * @return 标签分类
     */
    Flux<CapabilityTagClassifier> getTagClassifiers(String type);

    /**
     * 获取标签分类详情.
     *
     * @param id 分类ID
     * @return 标签分类
     */
    Mono<CapabilityTagClassifier> getTagClassifier(String type);

    /**
     * 获取标签.
     *
     * @param classifierId 分类ID
     * @return 标签
     */
    Flux<CapabilityTag> getTags(String classifierId);
}
