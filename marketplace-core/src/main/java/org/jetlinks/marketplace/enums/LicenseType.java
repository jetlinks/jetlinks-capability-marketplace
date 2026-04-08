package org.jetlinks.marketplace.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 许可证类型枚举.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@AllArgsConstructor
public enum LicenseType {

    free("免费"),
    trial("试用"),
    commercial("商业"),
    enterprise("企业");

    private final String text;

    public String getValue() {
        return name();
    }
}
