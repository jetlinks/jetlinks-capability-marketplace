package org.jetlinks.marketplace.command;

import org.hswebframework.web.api.crud.entity.PagerResult;
import org.jetlinks.core.command.AbstractCommand;
import org.jetlinks.core.command.GenericInputCommand;
import org.jetlinks.marketplace.CapabilityLatestVersionInfo;
import org.jetlinks.marketplace.CapabilitySearchRequest;
import reactor.core.publisher.Mono;

/**
 * 分页查询能力最新版本详情命令。
 *
 * 供运行时通过能力市场 Command 边界获取标准分页结果，搜索条件与版本资源透传开关保持原样。
 *
 * @author zhouhao
 * @since 2.12
 */
public class QueryCapabilityVersionInfoCommand
    extends AbstractCommand<Mono<PagerResult<CapabilityLatestVersionInfo>>, QueryCapabilityVersionInfoCommand>
    implements GenericInputCommand<CapabilitySearchRequest> {

    /**
     * 获取能力搜索条件。
     *
     * @return 搜索条件
     */
    public CapabilitySearchRequest asRequest() {
        return as(CapabilitySearchRequest.class);
    }

    /**
     * 设置能力搜索条件。
     *
     * @param request 搜索条件
     * @return 当前命令
     */
    public QueryCapabilityVersionInfoCommand with(CapabilitySearchRequest request) {
        return with((Object) request);
    }
}
