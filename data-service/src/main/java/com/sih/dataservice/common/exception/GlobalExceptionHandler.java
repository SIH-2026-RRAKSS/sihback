package com.sih.dataservice.common.exception;

import com.sih.dataservice.common.dto.ErrorResponse;
import com.sih.dataservice.common.filter.MDCFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        String traceId = getTraceId();
        log.warn("API exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getDetails(),
                traceId
        );
        return new ResponseEntity<>(errorResponse, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String traceId = getTraceId();
        Map<String, String> details = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation error on request: {}", details);
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                "Input validation failed",
                details,
                traceId
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        String traceId = getTraceId();
        log.warn("Access denied [traceId={}]: {}", traceId, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                "ACCESS_DENIED",
                "Access is denied",
                traceId
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = getTraceId();
        log.error("Unhandled internal server error [traceId={}]", traceId, ex);
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred",
                traceId
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String getTraceId() {
        String traceId = MDC.get(MDCFilter.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : "unknown";
    }
}
