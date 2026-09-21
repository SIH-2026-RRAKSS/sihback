package com.sih.dataservice.common;

import com.sih.dataservice.common.filter.MDCFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class MDCFilterTest {

    private MDCFilter mdcFilter;

    @BeforeEach
    void setUp() {
        mdcFilter = new MDCFilter();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_propagatesIncomingRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "custom-req-id-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(MDC.get(MDCFilter.TRACE_ID_MDC_KEY)).isEqualTo("custom-req-id-12345");
            assertThat(MDC.get(MDCFilter.REQUEST_ID_MDC_KEY)).isEqualTo("custom-req-id-12345");
        };

        mdcFilter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("custom-req-id-12345");
        // MDC must be cleared after filter completion
        assertThat(MDC.get(MDCFilter.TRACE_ID_MDC_KEY)).isNull();
    }

    @Test
    void doFilter_generatesUuidIfNoHeaderProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(MDC.get(MDCFilter.TRACE_ID_MDC_KEY)).isNotBlank();
        };

        mdcFilter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(MDC.get(MDCFilter.TRACE_ID_MDC_KEY)).isNull();
    }
}
