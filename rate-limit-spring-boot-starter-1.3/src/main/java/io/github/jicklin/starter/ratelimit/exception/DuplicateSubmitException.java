package io.github.jicklin.starter.ratelimit.exception;

/**
 * 重复提交异常
 *
 * @author marry
 */
public class DuplicateSubmitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 剩余等待时间（毫秒）
     */
    private final long remainingTime;

    public DuplicateSubmitException(String message) {
        super(message);
        this.remainingTime = 0;
    }

    public DuplicateSubmitException(String message, long remainingTime) {
        super(message);
        this.remainingTime = remainingTime;
    }

    public DuplicateSubmitException(String message, Throwable cause) {
        super(message, cause);
        this.remainingTime = 0;
    }

    public long getRemainingTime() {
        return remainingTime;
    }

    /**
     * 获取剩余等待时间（秒）
     */
    public long getRemainingTimeInSeconds() {
        return remainingTime / 1000;
    }
}
