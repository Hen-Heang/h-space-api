package com.henheang.apicore.components;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Runs the filter and returns the id the downstream chain saw in the MDC. */
    private String run(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        seen.set(MDC.get(RequestIdFilter.MDC_KEY));
                    }
                };
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Test
    void withoutHeader_generatesUuid_inMdcAndResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String id = run(new MockHttpServletRequest(), response);

        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
    }

    @Test
    void safeIncomingId_isKept() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "web-1a2B.3_c");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(run(request, response)).isEqualTo("web-1a2B.3_c");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("web-1a2B.3_c");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "has space",
                "line\nbreak",
                "fake] [INFO forged",
                "<script>",
                "x'; DROP TABLE users;--"
            })
    void unsafeIncomingId_isReplaced(String incoming) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, incoming);

        String id = run(request, new MockHttpServletResponse());

        assertThat(id).isNotEqualTo(incoming);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void overlongIncomingId_isReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "a".repeat(65));

        assertThat(UUID.fromString(run(request, new MockHttpServletResponse()))).isNotNull();
    }

    @Test
    void maxLengthIncomingId_isKept() throws Exception {
        String id = "a".repeat(64);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, id);

        assertThat(run(request, new MockHttpServletResponse())).isEqualTo(id);
    }

    @Test
    void mdcIsClearedAfterRequest_evenWhenChainThrows() throws Exception {
        MockFilterChain failing =
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        throw new IllegalStateException("boom");
                    }
                };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (IllegalStateException expected) {
            // the filter must not swallow it
        }

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
