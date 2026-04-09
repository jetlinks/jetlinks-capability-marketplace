package org.jetlinks.marketplace.client.command;

import lombok.AllArgsConstructor;
import org.jetlinks.core.annotation.command.CommandHandler;
import org.jetlinks.marketplace.*;
import org.jetlinks.marketplace.command.*;
import org.jetlinks.marketplace.spi.CapabilityMarketplaceClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AllArgsConstructor
public class CapabilityMarketplaceCommandSupport {

    private final CapabilityMarketplaceClient client;

    @CommandHandler
    public Flux<CapabilityInfo> search(QueryInstalledCapabilityCommand request) {
        return client.search(request.asRequest());
    }

    @CommandHandler
    public Mono<CapabilityInfo> getDetail(GetCapabilityDetailCommand request) {
        return client.getDetail(request.getCapabilityId());
    }

    @CommandHandler
    public Flux<CapabilityVersion> getVersions(GetCapabilityVersionsCommand request) {
        return client.getVersions(request.getCapabilityId());
    }

    @CommandHandler
    public Mono<CapabilityPackage> download(DownloadCapabilityPackageCommand request) {
        return client.download(request.getCapabilityId(), request.getVersion());
    }

    @CommandHandler
    public Flux<CapabilityInfo> checkUpdates(CheckCapabilityUpdatesCommand request) {
        return client.checkUpdates(request.getInstalled());
    }

    @CommandHandler
    public Flux<CapabilityTagClassifier> getTagClassifiers(GetCapabilityTagClassifiersCommand request) {
        return client.getTagClassifiers(request.getType());
    }

    @CommandHandler
    public Mono<CapabilityTagClassifier> getTagClassifier(GetCapabilityTagClassifierCommand request) {
        return client.getTagClassifier(request.getType());
    }

    @CommandHandler
    public Flux<CapabilityTag> getTags(GetCapabilityTagsCommand request) {
        return client.getTags(request.getClassifierId());
    }

}
