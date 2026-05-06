package com.agentcenter.bridge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void updateDefinitionCreatesEditableNextVersion() throws Exception {
        var listResult = mockMvc.perform(get("/api/workflow-definitions"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode current = findEnabledDefinition(listResult.getResponse().getContentAsString(), "FE");
        int nextVersion = current.get("versionNo").asInt() + 1;

        String payload = """
                {
                  "name": "FE 自定义编排",
                  "isDefault": true,
                  "nodes": [
                    {
                      "nodeKey": "custom_prd",
                      "name": "自定义需求",
                      "skillName": "prd-desingn",
                      "inputPolicy": "WORK_ITEM_ONLY",
                      "outputArtifactType": "MARKDOWN",
                      "requiredConfirmation": false,
                      "stageKey": "custom_prd",
                      "stageGoal": "整理需求",
                      "recommendedSkillNames": ["prd-desingn"],
                      "allowDynamicActions": true
                    },
                    {
                      "nodeKey": "custom_hld",
                      "name": "自定义方案",
                      "skillName": "hld-design",
                      "inputPolicy": "PREVIOUS_ARTIFACT",
                      "outputArtifactType": "MARKDOWN",
                      "requiredConfirmation": true,
                      "stageKey": "custom_hld",
                      "stageGoal": "输出方案",
                      "recommendedSkillNames": ["hld-design"],
                      "allowDynamicActions": true
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/workflow-definitions/" + current.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FE 自定义编排"))
                .andExpect(jsonPath("$.workItemType").value("FE"))
                .andExpect(jsonPath("$.versionNo").value(nextVersion))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].name").value("自定义需求"))
                .andExpect(jsonPath("$.nodes[1].skillName").value("hld-design"))
                .andExpect(jsonPath("$.nodes[1].requiredConfirmation").value(true));
    }

    private JsonNode findEnabledDefinition(String body, String workItemType) throws Exception {
        JsonNode definitions = objectMapper.readTree(body);
        for (JsonNode definition : definitions) {
            if (workItemType.equals(definition.get("workItemType").asText())
                    && "ENABLED".equals(definition.get("status").asText())) {
                return definition;
            }
        }
        throw new AssertionError("No enabled workflow definition for " + workItemType);
    }
}
