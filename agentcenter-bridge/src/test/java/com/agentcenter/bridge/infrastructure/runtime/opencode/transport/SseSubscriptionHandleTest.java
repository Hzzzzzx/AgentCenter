package com.agentcenter.bridge.infrastructure.runtime.opencode.transport;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SseSubscriptionHandleTest {

    @Test
    void startsActive() {
        SseSubscriptionHandle handle = new SseSubscriptionHandle(null);
        assertTrue(handle.isActive());
    }

    @Test
    void closeSetsInactive() {
        SseSubscriptionHandle handle = new SseSubscriptionHandle(null);
        handle.close();
        assertFalse(handle.isActive());
    }

    @Test
    void closeIsIdempotent() {
        int[] closeCount = {0};
        SseSubscriptionHandle handle = new SseSubscriptionHandle(h -> closeCount[0]++);
        handle.close();
        handle.close();
        handle.close();
        assertEquals(1, closeCount[0]);
        assertFalse(handle.isActive());
    }

    @Test
    void onCloseCallbackInvokedOnFirstClose() {
        boolean[] invoked = {false};
        SseSubscriptionHandle handle = new SseSubscriptionHandle(h -> invoked[0] = true);
        assertFalse(invoked[0]);
        handle.close();
        assertTrue(invoked[0]);
    }

    @Test
    void nullOnCloseCallbackDoesNotThrow() {
        SseSubscriptionHandle handle = new SseSubscriptionHandle(null);
        assertDoesNotThrow(handle::close);
    }
}
