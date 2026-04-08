package org.jetlinks.marketplace.client;

/**
 * SDK 运行时异常.
 *
 * @author zhouhao
 * @since 2.12
 */
public class MarketplaceSdkException extends RuntimeException {

    public MarketplaceSdkException(String message) {
        super(message);
    }

    public MarketplaceSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
