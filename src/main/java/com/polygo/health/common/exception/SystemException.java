package com.polygo.health.common.exception;

import lombok.Getter;

@Getter
public class SystemException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    public SystemException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public SystemException(ErrorCode errorCode, String details) {
        super(details != null ? details : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public SystemException(ErrorCode errorCode, String details, Throwable cause) {
        super(details != null ? details : errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.details = null;
    }
}
