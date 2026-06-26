package org.jetlinks.marketplace.client.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.Resource;
import org.jetlinks.marketplace.CapabilityAvailability;
import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/marketplace")
@AllArgsConstructor
@Tag(name = "能力市场接口")
@Authorize
@Resource(id = "marketplace", name = "能力市场")
public class MarketplaceClientController {

    private final CapabilityMarketplaceClient client;

    @PostMapping("/capabilities/_search")
    @Operation(summary = "搜索能力")
    public Flux<CapabilityInfo> search(@RequestBody CapabilitySearchRequest request) {
        return client.search(request);
    }

    @GetMapping("/capabilities/{id}")
    @Operation(summary = "获取能力详情")
    public Mono<CapabilityInfo> getDetail(@PathVariable String id) {
        return client.getDetail(id);
    }

    @GetMapping("/capabilities/{id}/availability")
    @Operation(summary = "获取能力可用性")
    public Mono<CapabilityAvailability> checkAvailability(@PathVariable String id) {
        return client.checkAvailability(id);
    }

    @GetMapping("/capabilities/{id}/versions")
    @Operation(summary = "获取能力版本列表")
    public Flux<CapabilityVersion> getVersions(@PathVariable String id) {
        return client.getVersions(id);
    }

    @GetMapping("/capabilities/{id}/versions/{version}/package")
    @Operation(summary = "下载能力包")
    public Mono<CapabilityPackage> download(@PathVariable String id, @PathVariable String version) {
        return client.download(id, version);
    }

    @PostMapping("/capabilities/_check-updates")
    @Operation(summary = "检查更新")
    public Flux<CapabilityInfo> checkUpdates(@RequestBody List<InstalledCapability> installed) {
        return client.checkUpdates(installed);
    }

    @GetMapping("/tag-classifiers")
    @Operation(summary = "获取标签分类")
    public Flux<CapabilityTagClassifier> getTagClassifiers(@RequestParam(required = false) String type) {
        return client.getTagClassifiers(type);
    }

    @GetMapping("/tag-classifiers/{id}")
    @Operation(summary = "获取标签分类详情")
    public Mono<CapabilityTagClassifier> getTagClassifier(@PathVariable String id) {
        return client.getTagClassifier(id);
    }

    @GetMapping("/tags")
    @Operation(summary = "获取标签")
    public Flux<CapabilityTag> getTags(@RequestParam String classifierId) {
        return client.getTags(classifierId);
    }
}
