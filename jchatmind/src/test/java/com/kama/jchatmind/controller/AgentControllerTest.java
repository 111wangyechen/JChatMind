package com.kama.jchatmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.exception.GlobalExceptionHandler;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.request.CreateAgentRequest;
import com.kama.jchatmind.model.request.UpdateAgentRequest;
import com.kama.jchatmind.model.response.CreateAgentResponse;
import com.kama.jchatmind.model.response.GetAgentsResponse;
import com.kama.jchatmind.model.vo.AgentVO;
import com.kama.jchatmind.service.AgentFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgentFacadeService agentFacadeService;

    @Test
    void testGetAgents_Success() throws Exception {
        AgentVO agent = AgentVO.builder()
                .id("agent-1")
                .name("Test Agent")
                .description("A test agent")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(List.of("tool1"))
                .allowedKbs(List.of("kb1"))
                .build();
        GetAgentsResponse response = GetAgentsResponse.builder()
                .agents(new AgentVO[]{agent})
                .build();
        when(agentFacadeService.getAgents()).thenReturn(response);

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.agents").isArray())
                .andExpect(jsonPath("$.data.agents[0].id").value("agent-1"))
                .andExpect(jsonPath("$.data.agents[0].name").value("Test Agent"));
    }

    @Test
    void testGetAgents_Empty() throws Exception {
        GetAgentsResponse response = GetAgentsResponse.builder()
                .agents(new AgentVO[]{})
                .build();
        when(agentFacadeService.getAgents()).thenReturn(response);

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.agents").isArray())
                .andExpect(jsonPath("$.data.agents").isEmpty());
    }

    @Test
    void testCreateAgent_Success() throws Exception {
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName("New Agent");
        request.setDescription("desc");
        request.setModel("deepseek-chat");

        CreateAgentResponse response = CreateAgentResponse.builder()
                .agentId("agent-new")
                .build();
        when(agentFacadeService.createAgent(any(CreateAgentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.agentId").value("agent-new"));
    }

    @Test
    void testCreateAgent_InvalidRequest() throws Exception {
        // Send empty JSON body — service may throw BizException
        when(agentFacadeService.createAgent(any(CreateAgentRequest.class)))
                .thenThrow(new BizException("Agent name is required"));

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Agent name is required"));
    }

    @Test
    void testCreateAgent_MissingName() throws Exception {
        when(agentFacadeService.createAgent(any(CreateAgentRequest.class)))
                .thenThrow(new BizException("Agent name is required"));

        CreateAgentRequest request = new CreateAgentRequest();
        request.setDescription("desc only");

        mockMvc.perform(post("/api/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Agent name is required"));
    }

    @Test
    void testDeleteAgent_Success() throws Exception {
        doNothing().when(agentFacadeService).deleteAgent("agent-1");

        mockMvc.perform(delete("/api/agents/{agentId}", "agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testDeleteAgent_NotFound() throws Exception {
        doThrow(new BizException("Agent not found")).when(agentFacadeService).deleteAgent("non-existent");

        mockMvc.perform(delete("/api/agents/{agentId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Agent not found"));
    }

    @Test
    void testUpdateAgent_Success() throws Exception {
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setName("Updated Agent");
        doNothing().when(agentFacadeService).updateAgent(eq("agent-1"), any(UpdateAgentRequest.class));

        mockMvc.perform(patch("/api/agents/{agentId}", "agent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testUpdateAgent_NotFound() throws Exception {
        doThrow(new BizException("Agent not found"))
                .when(agentFacadeService).updateAgent(eq("non-existent"), any(UpdateAgentRequest.class));

        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setName("Updated");

        mockMvc.perform(patch("/api/agents/{agentId}", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Agent not found"));
    }

    @Test
    void testUpdateAgent_InvalidRequest() throws Exception {
        doThrow(new BizException("Invalid update request"))
                .when(agentFacadeService).updateAgent(eq("agent-1"), any(UpdateAgentRequest.class));

        mockMvc.perform(patch("/api/agents/{agentId}", "agent-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Invalid update request"));
    }
}
