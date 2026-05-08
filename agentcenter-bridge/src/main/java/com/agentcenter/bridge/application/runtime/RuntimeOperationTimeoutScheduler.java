package com.agentcenter.bridge.application.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RuntimeOperationTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(RuntimeOperationTimeoutScheduler.class);

    private final RuntimeOperationService operationService;

    public RuntimeOperationTimeoutScheduler(RuntimeOperationService operationService) {
        this.operationService = operationService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void timeoutStaleOperations() {
        try {
            int count = operationService.timeoutStaleOperations();
            if (count > 0) {
                log.info("Timed out {} stale runtime operation(s)", count);
            }
        } catch (Exception e) {
            log.error("Failed to timeout stale operations", e);
        }
    }
}
