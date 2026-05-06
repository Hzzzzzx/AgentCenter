package com.agentcenter.bridge.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowExecutorConfig {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);

    @Bean
    @Qualifier("workflowExecutor")
    public ExecutorService workflowExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agentcenter-workflow-" + THREAD_COUNTER.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }
}
