package com.agentcenter.bridge.application.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class RuntimeOperationTimeoutSchedulerTest {

    @Test
    void callsTimeoutStaleOperations() {
        RuntimeOperationService service = mock(RuntimeOperationService.class);
        when(service.timeoutStaleOperations()).thenReturn(3);

        RuntimeOperationTimeoutScheduler scheduler = new RuntimeOperationTimeoutScheduler(service);
        scheduler.timeoutStaleOperations();

        verify(service).timeoutStaleOperations();
    }

    @Test
    void handlesZeroStaleOperations() {
        RuntimeOperationService service = mock(RuntimeOperationService.class);
        when(service.timeoutStaleOperations()).thenReturn(0);

        RuntimeOperationTimeoutScheduler scheduler = new RuntimeOperationTimeoutScheduler(service);
        scheduler.timeoutStaleOperations();

        verify(service).timeoutStaleOperations();
    }

    @Test
    void handlesExceptionFromService() {
        RuntimeOperationService service = mock(RuntimeOperationService.class);
        when(service.timeoutStaleOperations()).thenThrow(new RuntimeException("DB error"));

        RuntimeOperationTimeoutScheduler scheduler = new RuntimeOperationTimeoutScheduler(service);

        assertDoesNotThrow(() -> scheduler.timeoutStaleOperations());
        verify(service).timeoutStaleOperations();
    }
}
