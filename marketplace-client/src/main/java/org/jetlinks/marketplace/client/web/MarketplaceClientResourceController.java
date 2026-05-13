package org.jetlinks.marketplace.client.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.authorization.annotation.Resource;
import org.hswebframework.web.authorization.annotation.ResourceAction;
import org.jetlinks.marketplace.CapabilityInstallRequest;
import org.jetlinks.marketplace.InstalledResource;
import org.jetlinks.marketplace.ProgressState;
import org.jetlinks.marketplace.client.CapabilityResourceManager;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/marketplace")
@AllArgsConstructor
@Tag(name = "能力市场资源接口")
@Authorize
@Resource(id = "marketplace", name = "能力市场")
public class MarketplaceClientResourceController {

    private final CapabilityResourceManager resourceManager;

    @PostMapping("/capabilities/{id}/{version}/_install")
    @Operation(summary = "安装能力")
    @ResourceAction(id = "install", name = "安装")
    public Flux<ProgressState<InstalledResource>> install(@PathVariable String id,
                                                          @PathVariable String version,
                                                          @RequestBody(required = false) Mono<CapabilityInstallRequest> request) {
        return request
            .defaultIfEmpty(new CapabilityInstallRequest())
            .flatMapMany(payload -> resourceManager.install(id, version, payload));
    }

    @PostMapping("/capabilities/{id}/{version}/_upgrade")
    @Operation(summary = "升级能力")
    @ResourceAction(id = "upgrade", name = "升级")
    public Flux<ProgressState<InstalledResource>> upgrade(@PathVariable String id,
                                                          @PathVariable String version,
                                                          @RequestBody(required = false) Mono<CapabilityInstallRequest> request) {
        return request
            .defaultIfEmpty(new CapabilityInstallRequest())
            .flatMapMany(payload -> resourceManager.upgrade(id, version, payload));
    }

    @PostMapping("/capabilities/{type}/installed")
    @Operation(summary = "根据内部数据获取已安装的能力")
    @QueryAction
    public Flux<InstalledResource> listInstalled(@PathVariable String type,
                                                 @RequestBody Mono<List<String>> dataId) {
        // todo 资产权限控制
        return dataId
            .flatMapMany(lst -> resourceManager.listInstalledResources(type, lst));
    }

    @PostMapping("/capabilities/{type}/{capId}/installed")
    @Operation(summary = "获取获取已安装的能力")
    @QueryAction
    public Flux<InstalledResource> listInstalled(@PathVariable String type,
                                                 @PathVariable String capId,
                                                 @RequestBody Mono<List<String>> resourceId) {
        // todo 资产权限控制
        return resourceId
            .flatMapMany(lst -> resourceManager.listInstalledResources(type, capId, lst));
    }
}
