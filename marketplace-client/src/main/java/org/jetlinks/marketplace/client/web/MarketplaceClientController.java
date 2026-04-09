package org.jetlinks.marketplace.client.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.ResourceAction;
import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/marketplace")
@AllArgsConstructor
@Tag(name = "能力市场接口")
@Authorize
@Resource(id = "marketplace", name = "能力市场")
public class MarketplaceClientController {

    private final CapabilityMarketplaceClient client;

    private final CapabilityResourceManager resourceManager;

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

    @GetMapping("/capabilities/{id}/versions")
    @Operation(summary = "获取能力版本列表")
    public Flux<CapabilityVersion> getVersions(@PathVariable String id) {
        return client.getVersions(id);
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

    @PostMapping("/capabilities/{id}/{version}/_install")
    @Operation(summary = "安装能力")
    @ResourceAction(id = "install", name = "安装")
    public Flux<ProgressState<InstalledResource>> install(@PathVariable String id,
                                                          @PathVariable String version,
                                                          @RequestBody Mono<Map<String, Object>> configuration) {
        return configuration
            .flatMapMany(map -> resourceManager.install(id, version, map));
    }

    @PostMapping("/capabilities/{id}/{version}/_upgrade")
    @Operation(summary = "升级能力")
    @ResourceAction(id = "upgrade", name = "升级")
    public Flux<ProgressState<InstalledResource>> upgrade(@PathVariable String id,
                                                          @PathVariable String version,
                                                          @RequestBody Mono<Map<String, Object>> configuration) {
        return configuration
            .flatMapMany(map -> resourceManager.upgrade(id, version, map));
    }


    @PostMapping("/capabilities/{type}/installed")
    @Operation(summary = "获取已安装的能力")
    @QueryAction
    public Flux<InstalledResource> listInstalled(@PathVariable String type,
                                                 @RequestBody Mono<List<String>> dataId) {
        // todo 资产权限控制
        return dataId
            .flatMapMany(lst -> resourceManager.listInstalledResources(type, lst));
    }


}
