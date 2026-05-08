package com.agentcenter.bridge.application.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.agentcenter.bridge.domain.runtime.RuntimeOperationStatus;
import com.agentcenter.bridge.infrastructure.id.IdGenerator;
import com.agentcenter.bridge.infrastructure.persistence.entity.RuntimeOperationEntity;
import com.agentcenter.bridge.infrastructure.persistence.mapper.RuntimeOperationMapper;

class RuntimeOperationServiceTest {

    private RuntimeOperationMapper mapper;
    private IdGenerator idGenerator;
    private RuntimeOperationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RuntimeOperationMapper.class);
        idGenerator = mock(IdGenerator.class);
        when(idGenerator.nextId()).thenReturn("op_01TEST");
        service = new RuntimeOperationService(mapper, idGenerator);
    }

    private RuntimeOperationEntity stubCreatedEntity() {
        RuntimeOperationEntity entity = new RuntimeOperationEntity();
        entity.setId("op_01TEST");
        entity.setProjectId("proj_01");
        entity.setRuntimeType("OPENCODE");
        entity.setOperationType("skill.run");
        entity.setStatus(RuntimeOperationStatus.CREATED.name());
        return entity;
    }

    private RuntimeOperationEntity stubEntityWithStatus(RuntimeOperationStatus status) {
        RuntimeOperationEntity entity = stubCreatedEntity();
        entity.setStatus(status.name());
        return entity;
    }

    @Nested
    class CreateOperation {

        @Test
        void createsNewWhenNoIdempotencyKeyMatch() {
            when(mapper.findByIdempotencyKey(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(null);

            RuntimeOperationEntity result = service.createOperation(
                    "proj_01", "OPENCODE", "skill.run", "key_abc",
                    null, null, null, null, null, null, null,
                    null, null, null, null, "user_01");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RuntimeOperationStatus.CREATED.name());
            assertThat(result.getProjectId()).isEqualTo("proj_01");
            verify(mapper).insert(any(RuntimeOperationEntity.class));
        }

        @Test
        void returnsExistingWhenIdempotencyKeyMatches() {
            RuntimeOperationEntity existing = stubCreatedEntity();
            existing.setIdempotencyKey("key_abc");
            when(mapper.findByIdempotencyKey("proj_01", "OPENCODE", "skill.run", "key_abc"))
                    .thenReturn(existing);

            RuntimeOperationEntity result = service.createOperation(
                    "proj_01", "OPENCODE", "skill.run", "key_abc",
                    null, null, null, null, null, null, null,
                    null, null, null, null, "user_01");

            assertThat(result.getId()).isEqualTo("op_01TEST");
            verify(mapper, never()).insert(any());
        }

        @Test
        void createsWithoutIdempotencyKey() {
            RuntimeOperationEntity result = service.createOperation(
                    "proj_01", "OPENCODE", "skill.run", null,
                    null, null, null, null, null, null, null,
                    null, null, null, null, "user_01");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RuntimeOperationStatus.CREATED.name());
            verify(mapper).insert(any(RuntimeOperationEntity.class));
        }
    }

    @Nested
    class Transition {

        @Test
        void createdToTimedOut() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.CREATED));

            service.transition("op_01TEST", RuntimeOperationStatus.TIMED_OUT);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void dispatchingToTimedOut() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.DISPATCHING));

            service.transition("op_01TEST", RuntimeOperationStatus.TIMED_OUT);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void createdToDispatching() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.CREATED));

            service.transition("op_01TEST", RuntimeOperationStatus.DISPATCHING);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void dispatchingToSucceeded() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.DISPATCHING));

            service.transition("op_01TEST", RuntimeOperationStatus.SUCCEEDED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void dispatchingToAccepted() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.DISPATCHING));

            service.transition("op_01TEST", RuntimeOperationStatus.ACCEPTED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void dispatchingToFailed() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.DISPATCHING));

            service.transition("op_01TEST", RuntimeOperationStatus.FAILED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void acceptedToInProgress() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED));

            service.transition("op_01TEST", RuntimeOperationStatus.IN_PROGRESS);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void acceptedToSucceeded() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED));

            service.transition("op_01TEST", RuntimeOperationStatus.SUCCEEDED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void acceptedToFailed() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED));

            service.transition("op_01TEST", RuntimeOperationStatus.FAILED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void acceptedToTimedOut() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED));

            service.transition("op_01TEST", RuntimeOperationStatus.TIMED_OUT);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void inProgressToSucceeded() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transition("op_01TEST", RuntimeOperationStatus.SUCCEEDED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void inProgressToFailed() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transition("op_01TEST", RuntimeOperationStatus.FAILED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void inProgressToCanceled() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transition("op_01TEST", RuntimeOperationStatus.CANCELED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void inProgressToTimedOut() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transition("op_01TEST", RuntimeOperationStatus.TIMED_OUT);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void throwsOnTerminalToAny() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.SUCCEEDED));

            assertThatThrownBy(() -> service.transition("op_01TEST", RuntimeOperationStatus.DISPATCHING))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from SUCCEEDED to DISPATCHING");

            verify(mapper, never()).update(any());
        }

        @Test
        void throwsOnInvalidTransitionSkippingState() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.CREATED));

            assertThatThrownBy(() -> service.transition("op_01TEST", RuntimeOperationStatus.IN_PROGRESS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot transition from CREATED to IN_PROGRESS");

            verify(mapper, never()).update(any());
        }

        @Test
        void setsCompletedAtOnTerminalTransition() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transition("op_01TEST", RuntimeOperationStatus.SUCCEEDED);

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }
    }

    @Nested
    class TransitionToFailed {

        @Test
        void setsErrorCodeAndMessage() {
            when(mapper.findById("op_01TEST")).thenReturn(stubEntityWithStatus(RuntimeOperationStatus.IN_PROGRESS));

            service.transitionToFailed("op_01TEST", "ERR_TIMEOUT", "Operation timed out after 30s");

            verify(mapper).update(any(RuntimeOperationEntity.class));
        }
    }

    @Nested
    class FindOperations {

        @Test
        void findByIdDelegatesToMapper() {
            RuntimeOperationEntity entity = stubCreatedEntity();
            when(mapper.findById("op_01TEST")).thenReturn(entity);

            RuntimeOperationEntity result = service.findById("op_01TEST");

            assertThat(result).isSameAs(entity);
        }

        @Test
        void findByIdempotencyKeyDelegatesToMapper() {
            RuntimeOperationEntity entity = stubCreatedEntity();
            when(mapper.findByIdempotencyKey("proj_01", "OPENCODE", "skill.run", "key_abc"))
                    .thenReturn(entity);

            RuntimeOperationEntity result = service.findByIdempotencyKey(
                    "proj_01", "OPENCODE", "skill.run", "key_abc");

            assertThat(result).isSameAs(entity);
        }
    }

    @Nested
    class TimeoutStaleOperations {

        @Test
        void findsStaleAndTransitionsToTimedOut() {
            RuntimeOperationEntity stale = stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED);
            when(mapper.findStaleNonTerminal(anyString())).thenReturn(List.of(stale));
            when(mapper.findById("op_01TEST")).thenReturn(stale);

            int count = service.timeoutStaleOperations();

            assertThat(count).isEqualTo(1);
            verify(mapper).update(any(RuntimeOperationEntity.class));
        }

        @Test
        void skipsAlreadyTransitionedOperations() {
            RuntimeOperationEntity stale = stubEntityWithStatus(RuntimeOperationStatus.ACCEPTED);
            RuntimeOperationEntity alreadyDone = stubEntityWithStatus(RuntimeOperationStatus.SUCCEEDED);
            when(mapper.findStaleNonTerminal(anyString())).thenReturn(List.of(stale));
            when(mapper.findById("op_01TEST")).thenReturn(alreadyDone);

            int count = service.timeoutStaleOperations();

            assertThat(count).isZero();
            verify(mapper, never()).update(any());
        }
    }
}
