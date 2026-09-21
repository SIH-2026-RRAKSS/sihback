package com.sih.dataservice.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String code;
    private String message;
    private Map<String, String> details;
    private String traceId;
    private Instant timestamp;

    public ErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message, String traceId) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String code, String message, Map<String, String> details, String traceId) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.traceId = traceId;
        this.timestamp = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
