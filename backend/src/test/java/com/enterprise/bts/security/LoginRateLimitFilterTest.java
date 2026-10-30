package com.enterprise.bts.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginRateLimitFilterTest {

    private static final long SECOND = 1_000_000_000L;

    private final AtomicLong nanos = new AtomicLong(10 * SECOND);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // 3 attempts per 60 s per client; one token refills every 20 s.
        filter = new LoginRateLimitFilter(3, 60, mapper, nanos::get);
    }

    private MockHttpServletResponse login(String ip, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void allowsTheConfiguredNumberOfAttemptsThenAnswers429() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = login("10.0.0.1", chain);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isBetween(20L, 21L);
        JsonNode body = mapper.readTree(blocked.getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("code").asText()).isEqualTo("RATE_LIMITED");
        assertThat(body.get("correlationId").asText()).isNotBlank();
        verify(chain, times(3)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void tokensRefillContinuouslyOverTime() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1", chain);
        }
        assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(429);

        nanos.addAndGet(10 * SECOND);                                  // half a token: still blocked
        assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(429);

        nanos.addAndGet(11 * SECOND);                                  // 21 s in total: one token is back
        assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(200);
        assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(429);
    }

    @Test
    void bucketNeverRefillsBeyondItsCapacity() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        login("10.0.0.1", chain);
        nanos.addAndGet(3600 * SECOND);                                // an hour idle
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (login("10.0.0.1", chain).getStatus() == 200) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(3);
    }

    @Test
    void clientsAreRateLimitedIndependently() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1", chain);
        }
        assertThat(login("10.0.0.1", chain).getStatus()).isEqualTo(429);
        assertThat(login("10.0.0.2", chain).getStatus()).isEqualTo(200);
        assertThat(filter.trackedClients()).isEqualTo(2);
    }

    @Test
    void forwardedForHeaderCannotBeUsedToEvadeTheLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        int blocked = 0;
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("10.0.0.9");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);   // attacker rotates a forged address
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            if (response.getStatus() == 429) {
                blocked++;
            }
        }
        assertThat(blocked).isEqualTo(3);
    }

    @Test
    void onlyPostToTheLoginEndpointIsLimited() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
            get.setRemoteAddr("10.0.0.1");
            filter.doFilter(get, new MockHttpServletResponse(), chain);

            MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/projects");
            other.setRemoteAddr("10.0.0.1");
            filter.doFilter(other, new MockHttpServletResponse(), chain);
        }
        verify(chain, times(20)).doFilter(any(ServletRequest.class), any(ServletResponse.class));
        assertThat(filter.trackedClients()).isZero();
    }

    @Test
    void aBlockedRequestNeverReachesTheRestOfTheChain() throws Exception {
        FilterChain passing = mock(FilterChain.class);
        for (int i = 0; i < 3; i++) {
            login("10.0.0.1", passing);
        }
        FilterChain neverCalled = mock(FilterChain.class);
        assertThat(login("10.0.0.1", neverCalled).getStatus()).isEqualTo(429);
        verify(neverCalled, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    void thresholdHoldsUnderConcurrentLoad() throws Exception {
        AtomicInteger passed = new AtomicInteger();
        FilterChain chain = (request, response) -> passed.incrementAndGet();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Callable<Integer> attempt = () -> login("10.0.0.7", chain).getStatus();
                futures.add(pool.submit(attempt));
            }
            int rejected = 0;
            for (Future<Integer> future : futures) {
                if (future.get(30, TimeUnit.SECONDS) == 429) {
                    rejected++;
                }
            }
            assertThat(passed.get()).isEqualTo(3);
            assertThat(rejected).isEqualTo(97);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidConfigurationIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new LoginRateLimitFilter(0, 60, mapper)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginRateLimitFilter(3, 0, mapper)).isInstanceOf(IllegalArgumentException.class);
    }
}
