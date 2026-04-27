package org.jetlinks.marketplace;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.marketplace.enums.CapabilityUseCondition;

import java.io.Serial;
import java.io.Serializable;

/**
 * 能力可用性检查结果.
 *
 * @author zhouhao
 * @since 2.12
 */
@Getter
@Setter
public class CapabilityAvailability implements Serializable {

    public static final String REASON_LOGIN_REQUIRED = "error.marketplace.capability.login_required";
    public static final String REASON_LOGIN_REQUIRED_FOR_PURCHASE = "error.marketplace.capability.login_required_for_purchase";
    public static final String REASON_PURCHASE_REQUIRED = "error.marketplace.capability.purchase_required";
    public static final String REASON_PROJECT_UNAVAILABLE = "error.marketplace.capability.project_unavailable";

    @Serial
    private static final long serialVersionUID = 1L;

    private String capabilityId;

    private boolean available;

    private CapabilityUseCondition useCondition;

    private String reasonCode;

    private String reason;

    /**
     * 预留给客户端跳转购买或下单的地址.
     */
    private String purchaseUrl;
}
