package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.agentcenter.bridge.api.dto.RuntimeEventDto;
import com.agentcenter.bridge.domain.runtime.RuntimeEventSource;
import com.agentcenter.bridge.domain.runtime.RuntimeEventType;
import com.agentcenter.bridge.infrastructure.event.SseEmitterRegistry;
import com.agentcenter.bridge.infrastructure.event.WebSocketSessionRegistry;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeEventEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeEventMapper;

class RuntimeEventServiceTest {

    private RuntimeEventMapper eventMapper;
    private IdGenerator idGenerator;
    private SseEmitterRegistry emitterRegistry;
    private WebSocketSessionRegistry webSocketSessionRegistry;
    private RuntimeEventService service;

    @BeforeEach
    void setUp() {
        eventMapper = mock(RuntimeEventMapper.class);
        idGenerator = mock(IdGenerator.class);
        emitterRegistry = mock(SseEmitterRegistry.class);
        webSocketSessionRegistry = mock(WebSocketSessionRegistry.class);
        service = new RuntimeEventService(eventMapper, idGenerator, emitterRegistry, webSocketSessionRegistry);
        when(idGenerator.nextId()).thenReturn("evt-generated");
        when(eventMapper.nextSeqNo("ses-1")).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishEventDefersPersistenceUntilTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        RuntimeEventDto event = runtimeEvent();
        service.publishEvent(event);

        verify(eventMapper, never()).insert(any());
        verify(emitterRegistry, never()).sendToSession(eq("ses-1"), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(eventMapper).insert(any(RuntimeEventEntity.class));
        verify(emitterRegistry).sendToSession(eq("ses-1"), any(RuntimeEventDto.class));
        verify(webSocketSessionRegistry).sendToSession(eq("ses-1"), any());
    }

    @Test
    void publishCommittedEventDoesNotRegisterNestedAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.publishCommittedEvent(runtimeEvent());

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(eventMapper).insert(any(RuntimeEventEntity.class));
        verify(emitterRegistry).sendToSession(eq("ses-1"), any(RuntimeEventDto.class));
    }

    @Test
    void publishEventSerializesConcurrentPersistenceThroughSingleWriter() throws Exception {
        AtomicInteger nextSeqNo = new AtomicInteger(1);
        AtomicInteger activeWriters = new AtomicInteger();
        AtomicInteger maxActiveWriters = new AtomicInteger();
        when(idGenerator.nextId()).thenAnswer(invocation -> "evt-" + nextSeqNo.get());
        when(eventMapper.nextSeqNo("ses-1")).thenAnswer(invocation -> nextSeqNo.getAndIncrement());
        doAnswer(invocation -> {
            int active = activeWriters.incrementAndGet();
            maxActiveWriters.updateAndGet(current -> Math.max(current, active));
            Thread.sleep(10);
            activeWriters.decrementAndGet();
            return null;
        }).when(eventMapper).insert(any(RuntimeEventEntity.class));

        int eventCount = 8;
        CountDownLatch ready = new CountDownLatch(eventCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(eventCount);
        var callers = Executors.newFixedThreadPool(eventCount);
        try {
            for (int i = 0; i < eventCount; i++) {
                callers.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(1, TimeUnit.SECONDS);
                        service.publishEvent(runtimeEvent());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            callers.shutdownNow();
        }

        assertThat(maxActiveWriters.get()).isEqualTo(1);
        verify(eventMapper, atLeastOnce()).insert(any(RuntimeEventEntity.class));
    }

    @Test
    void publishEventRetriesWholePersistenceWhenSqliteBusy() {
        when(eventMapper.nextSeqNo("ses-1")).thenReturn(1, 2);
        doThrow(new RuntimeException("[SQLITE_BUSY] database is locked"))
                .doNothing()
                .when(eventMapper).insert(any(RuntimeEventEntity.class));

        service.publishEvent(runtimeEvent());

        ArgumentCaptor<RuntimeEventEntity> entityCaptor = ArgumentCaptor.forClass(RuntimeEventEntity.class);
        verify(eventMapper, atLeastOnce()).insert(entityCaptor.capture());
        List<RuntimeEventEntity> attempts = new ArrayList<>(entityCaptor.getAllValues());
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).getSeqNo()).isEqualTo(1);
        assertThat(attempts.get(1).getSeqNo()).isEqualTo(2);
        verify(emitterRegistry).sendToSession(eq("ses-1"), any(RuntimeEventDto.class));
    }

    private RuntimeEventDto runtimeEvent() {
        return new RuntimeEventDto(
                null,
                "ses-1",
                "work-1",
                "workflow-1",
                "node-1",
                RuntimeEventType.STATUS,
                RuntimeEventSource.BRIDGE,
                "{\"status\":\"running\"}",
                null
        );
    }
}
