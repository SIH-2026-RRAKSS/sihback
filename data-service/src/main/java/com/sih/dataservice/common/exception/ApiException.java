package com.sih.dataservice.common.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, String> details;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = null;
    }

    public ApiException(HttpStatus status, String errorCode, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
