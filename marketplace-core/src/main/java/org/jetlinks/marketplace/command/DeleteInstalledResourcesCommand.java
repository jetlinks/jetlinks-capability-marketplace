package org.jetlinks.marketplace.command;

import org.jetlinks.core.command.AbstractCommand;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * 删除已安装能力资源绑定命令.
 *
 * @author zhouhao
 * @since 2.12
 */
public class DeleteInstalledResourcesCommand extends AbstractCommand<Mono<Integer>, DeleteInstalledResourcesCommand> {

    public String getType() {
        return getOrNull("type", String.class);
    }

    public DeleteInstalledResourcesCommand setType(String type) {
        return with("type", type);
    }

    @SuppressWarnings("unchecked")
    public List<String> getDataIds() {
        Collection<String> dataIds = getOrNull("dataIds", Collection.class);
        return dataIds == null ? List.of() : List.copyOf(dataIds);
    }

    public DeleteInstalledResourcesCommand setDataIds(Collection<String> dataIds) {
        return with("dataIds", dataIds == null ? List.of() : List.copyOf(dataIds));
    }
}
