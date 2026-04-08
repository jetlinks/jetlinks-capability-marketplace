package org.jetlinks.marketplace.client;

/**
 * 许可证校验失败.
 *
 * @author zhouhao
 * @since 2.12
 */
public class LicenseValidationException extends MarketplaceSdkException {

    public LicenseValidationException(String message) {
        super(message);
    }

    public LicenseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
