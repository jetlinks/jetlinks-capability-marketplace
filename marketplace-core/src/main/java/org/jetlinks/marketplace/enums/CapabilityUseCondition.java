package org.jetlinks.marketplace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 能力使用条件.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@AllArgsConstructor
public enum CapabilityUseCondition {

    free("完全免费"),
    registered("注册用户"),
    needPurchase("需要购买");

    private final String text;

    public String getValue() {
        return name();
    }
}
