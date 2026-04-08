package org.jetlinks.marketplace.client;

/**
 * 包验签或完整性校验失败.
 *
 * @author zhouhao
 * @since 2.12
 */
public class PackageVerificationException extends MarketplaceSdkException {

    public PackageVerificationException(String message) {
        super(message);
    }

    public PackageVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
