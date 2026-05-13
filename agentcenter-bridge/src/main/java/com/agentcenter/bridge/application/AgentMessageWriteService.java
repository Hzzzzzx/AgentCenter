package com.agentcenter.bridge.application;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.agentcenter.bridge.infrastructure.persistence.entity.AgentMessageEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.AgentMessageMapper;

@Service
public class AgentMessageWriteService {

    private static final int SQLITE_BUSY_MAX_RETRIES = 5;
    private static final long SQLITE_BUSY_RETRY_SLEEP_MS = 80L;

    private final AgentMessageMapper agentMessageMapper;
    private final Object messageWriteLock = new Object();

    public AgentMessageWriteService(AgentMessageMapper agentMessageMapper) {
        this.agentMessageMapper = agentMessageMapper;
    }

    public void insertWithNextSeqNo(AgentMessageEntity message) {
        insertWithNextSeqNoIfAbsent(message, null);
    }

    public boolean insertWithNextSeqNoIfAbsent(AgentMessageEntity message,
                                               Predicate<List<AgentMessageEntity>> skipInsert) {
        synchronized (messageWriteLock) {
            for (int attempt = 1; ; attempt++) {
                List<AgentMessageEntity> existing = agentMessageMapper.findBySessionId(message.getSessionId());
                if (skipInsert != null && skipInsert.test(existing)) {
                    return false;
                }
                message.setSeqNo(nextSeqNo(existing));
                try {
                    agentMessageMapper.insert(message);
                    return true;
                } catch (Exception e) {
                    if (!isSqliteBusy(e) || attempt >= SQLITE_BUSY_MAX_RETRIES) {
                        throw e;
                    }
                    sleepBeforeRetry(attempt);
                }
            }
        }
    }

    private int nextSeqNo(List<AgentMessageEntity> messages) {
        return messages.stream()
                .mapToInt(message -> message.getSeqNo() != null ? message.getSeqNo() : 0)
                .max()
                .orElse(0) + 1;
    }

    private boolean isSqliteBusy(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("SQLITE_BUSY")
                    || message.contains("database is locked")
                    || message.contains("database table is locked"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        long sleepMillis = SQLITE_BUSY_RETRY_SLEEP_MS * attempt;
        try {
            TimeUnit.MILLISECONDS.sleep(sleepMillis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
