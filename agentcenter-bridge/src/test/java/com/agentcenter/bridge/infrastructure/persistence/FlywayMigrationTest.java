package com.agentcenter.bridge.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allFifteenTablesExist() {
        List<String> expectedTables = List.of(
                "user_account", "project_member", "work_item",
                "workflow_definition", "workflow_node_definition",
                "workflow_instance", "workflow_node_instance",
                "agent_session", "agent_message",
                "runtime_event", "artifact",
                "confirmation_request", "confirmation_action",
                "skill_definition", "outbox_event"
        );

        for (String table : expectedTables) {
            int count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                    Integer.class, table);
            assertThat(count).as("Table '%s' should exist", table).isGreaterThan(0);
        }
    }

    @Test
    void defaultUserExists() {
        String displayName = jdbcTemplate.queryForObject(
                "SELECT display_name FROM user_account WHERE id='01DEFAULTUSER00000000000000001'",
                String.class);
        assertThat(displayName).isEqualTo("Admin");
    }

    @Test
    void seedWorkItemsExist() {
        assertThat(workItemExists("FE1234")).isTrue();
        assertThat(workItemExists("US1203")).isTrue();
        assertThat(workItemExists("BUG0602")).isTrue();
    }

    @Test
    void feDefaultWorkflowHasSixNodes() {
        Integer nodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workflow_node_definition WHERE workflow_definition_id='01FEDEFAULTWFDEF00000000000001'",
                Integer.class);
        assertThat(nodeCount).isEqualTo(6);
    }

    private boolean workItemExists(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_item WHERE code=?",
                Integer.class, code);
        return count != null && count > 0;
    }
}
