package com.agentcenter.bridge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;

class AgentMessageWriteServiceTest {

    @Test
    void insertWithNextSeqNoRetriesAndRecomputesSeqNoWhenSqliteBusy() {
        AgentMessageMapper mapper = mock(AgentMessageMapper.class);
        AgentMessageWriteService service = new AgentMessageWriteService(mapper);
        when(mapper.findBySessionId("ses-1"))
                .thenReturn(List.of(messageWithSeq(5)))
                .thenReturn(List.of(messageWithSeq(6)));
        List<Integer> attemptedSeqNos = new ArrayList<>();
        doAnswer(invocation -> {
                    attemptedSeqNos.add(invocation.getArgument(0, AgentMessageEntity.class).getSeqNo());
                    throw new RuntimeException("[SQLITE_BUSY] database is locked");
                })
                .doAnswer(invocation -> {
                    attemptedSeqNos.add(invocation.getArgument(0, AgentMessageEntity.class).getSeqNo());
                    return null;
                })
                .when(mapper).insert(any(AgentMessageEntity.class));

        AgentMessageEntity message = newMessage("ses-1");

        service.insertWithNextSeqNo(message);

        verify(mapper, atLeastOnce()).insert(any(AgentMessageEntity.class));
        assertThat(attemptedSeqNos).containsExactly(6, 7);
        assertThat(message.getSeqNo()).isEqualTo(7);
    }

    @Test
    void insertWithNextSeqNoSerializesConcurrentWriters() throws Exception {
        AgentMessageMapper mapper = mock(AgentMessageMapper.class);
        AgentMessageWriteService service = new AgentMessageWriteService(mapper);
        when(mapper.findBySessionId("ses-1")).thenReturn(List.of());
        AtomicInteger activeWriters = new AtomicInteger();
        AtomicInteger maxActiveWriters = new AtomicInteger();
        doAnswer(invocation -> {
            int active = activeWriters.incrementAndGet();
            maxActiveWriters.updateAndGet(current -> Math.max(current, active));
            Thread.sleep(10);
            activeWriters.decrementAndGet();
            return null;
        }).when(mapper).insert(any(AgentMessageEntity.class));

        int writerCount = 6;
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writerCount);
        var callers = Executors.newFixedThreadPool(writerCount);
        try {
            for (int i = 0; i < writerCount; i++) {
                callers.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(1, TimeUnit.SECONDS);
                        service.insertWithNextSeqNo(newMessage("ses-1"));
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
    }

    @Test
    void insertWithNextSeqNoIfAbsentSkipsInsertInsideWriteLock() {
        AgentMessageMapper mapper = mock(AgentMessageMapper.class);
        AgentMessageWriteService service = new AgentMessageWriteService(mapper);
        when(mapper.findBySessionId("ses-1")).thenReturn(List.of(messageWithSeq(1)));

        boolean inserted = service.insertWithNextSeqNoIfAbsent(
                newMessage("ses-1"),
                existing -> !existing.isEmpty());

        assertThat(inserted).isFalse();
        verify(mapper, never()).insert(any());
    }

    @Test
    void insertWithNextSeqNoDoesNotRetryNonSqliteErrors() {
        AgentMessageMapper mapper = mock(AgentMessageMapper.class);
        AgentMessageWriteService service = new AgentMessageWriteService(mapper);
        when(mapper.findBySessionId("ses-1")).thenReturn(List.of());
        doThrow(new IllegalStateException("validation failed"))
                .when(mapper).insert(any(AgentMessageEntity.class));

        AgentMessageEntity message = newMessage("ses-1");

        assertThatThrownBy(() -> service.insertWithNextSeqNo(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("validation failed");
        verify(mapper).insert(message);
    }

    private AgentMessageEntity newMessage(String sessionId) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setId("msg-new");
        message.setSessionId(sessionId);
        return message;
    }

    private AgentMessageEntity messageWithSeq(int seqNo) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setId("msg-" + seqNo);
        message.setSessionId("ses-1");
        message.setSeqNo(seqNo);
        return message;
    }
}
