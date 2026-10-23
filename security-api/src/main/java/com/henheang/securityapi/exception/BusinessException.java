package com.henheang.securityapi.exception;

import com.henheang.commonapi.components.common.api.StatusCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private Object body;
    private final StatusCode errorCode;

    public BusinessException(StatusCode errorCode, Object body) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.body = body;
    }

    public BusinessException(StatusCode errorCode, String message) {

        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(StatusCode errorCode) {

        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(StatusCode errorCode, Throwable e) {

        this(errorCode);
        //        AppLogManager.error(e);

    }
}
