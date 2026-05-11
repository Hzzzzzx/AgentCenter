package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentSessionServiceTest {

    @Test
    void safeAutoRetryableIncludesTransportAndHttp5xxFailures() {
        assertThat(AgentSessionService.isSafeAutoRetryable("Connection refused")).isTrue();
        assertThat(AgentSessionService.isSafeAutoRetryable("HTTP 503 Service Unavailable")).isTrue();
        assertThat(AgentSessionService.isSafeAutoRetryable("请求超时")).isTrue();
    }

    @Test
    void safeAutoRetryableDoesNotTreatAnyDigitFiveAsRetryable() {
        assertThat(AgentSessionService.isSafeAutoRetryable("validation failed for step 5")).isFalse();
        assertThat(AgentSessionService.isSafeAutoRetryable("permission denied after 5 attempts")).isFalse();
    }

    @Test
    void retryGuardConfigFallsBackWhenNegative() {
        assertThat(AgentSessionService.normalizeRetryLimit(-1)).isEqualTo(2);
        assertThat(AgentSessionService.normalizeRetryBackoffMs(-1)).isEqualTo(700L);
    }

    @Test
    void retryGuardConfigAllowsZeroToDisableAutoRetryOrBackoff() {
        assertThat(AgentSessionService.normalizeRetryLimit(0)).isZero();
        assertThat(AgentSessionService.normalizeRetryBackoffMs(0)).isZero();
    }
}
