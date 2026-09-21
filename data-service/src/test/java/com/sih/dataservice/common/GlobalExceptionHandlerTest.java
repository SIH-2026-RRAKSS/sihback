package com.sih.dataservice.common;

import com.sih.dataservice.common.dto.ErrorResponse;
import com.sih.dataservice.common.exception.ApiException;
import com.sih.dataservice.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleApiException_returnsConfiguredStatusAndShape() {
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "Case does not exist", Map.of("caseId", "123"));
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleApiException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("CASE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Case does not exist");
        assertThat(response.getBody().getDetails()).containsEntry("caseId", "123");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void handleGenericException_returnsInternalServerError() {
        Exception ex = new RuntimeException("Unexpected db crash");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected internal error occurred");
    }
}
