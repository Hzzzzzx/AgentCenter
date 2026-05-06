package com.agentcenter.bridge.infrastructure.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UlidIdGeneratorTest {

    private final UlidIdGenerator generator = new UlidIdGenerator();

    @Test
    void generatesNonEmptySortableId() throws InterruptedException {
        String id1 = generator.nextId();
        Thread.sleep(1); // Ensure next ULID has a later timestamp
        String id2 = generator.nextId();
        assertNotNull(id1);
        assertEquals(26, id1.length());
        assertTrue(id2.compareTo(id1) >= 0, "ULIDs should be monotonically sortable across time");
    }

    @Test
    void generatesUniqueIds() {
        String id1 = generator.nextId();
        String id2 = generator.nextId();
        assertTrue(!id1.equals(id2));
    }

    @Test
    void generatesValidUlidFormat() {
        String id = generator.nextId();
        assertTrue(id.matches("[0-9A-HJKMNP-TV-Z]{26}"));
    }
}
