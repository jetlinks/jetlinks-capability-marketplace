package org.jetlinks.marketplace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 能力状态枚举.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@AllArgsConstructor
public enum CapabilityState {

    notInstalled("未安装"),
    downloading("下载中"),
    installing("安装中"),
    installed("已安装"),
    upgrading("升级中"),
    disabled("已禁用"),
    error("异常"),
    uninstalling("卸载中");

    private final String text;

    public String getValue() {
        return name();
    }
}
