package com.kama.jchatmind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.request.CreateAgentRequest;
import com.kama.jchatmind.model.request.UpdateAgentRequest;
import com.kama.jchatmind.model.response.CreateAgentResponse;
import com.kama.jchatmind.model.response.GetAgentsResponse;
import com.kama.jchatmind.model.vo.AgentVO;
import com.kama.jchatmind.service.impl.AgentFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentFacadeServiceTest {

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentConverter agentConverter;

    @InjectMocks
    private AgentFacadeServiceImpl agentFacadeService;

    private Agent buildAgent(String id, String name) {
        return Agent.builder()
                .id(id)
                .name(name)
                .description("desc")
                .systemPrompt("prompt")
                .model("deepseek-chat")
                .allowedTools("[\"tool1\"]")
                .allowedKbs("[\"kb1\"]")
                .chatOptions("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private AgentVO buildAgentVO(String id, String name) {
        return AgentVO.builder()
                .id(id)
                .name(name)
                .description("desc")
                .systemPrompt("prompt")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(List.of("tool1"))
                .allowedKbs(List.of("kb1"))
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();
    }

    @Test
    void testGetAgents_Success() throws JsonProcessingException {
        // Given
        Agent agent1 = buildAgent("a1", "Agent1");
        Agent agent2 = buildAgent("a2", "Agent2");
        when(agentMapper.selectAll()).thenReturn(Arrays.asList(agent1, agent2));
        when(agentConverter.toVO(agent1)).thenReturn(buildAgentVO("a1", "Agent1"));
        when(agentConverter.toVO(agent2)).thenReturn(buildAgentVO("a2", "Agent2"));

        // When
        GetAgentsResponse response = agentFacadeService.getAgents();

        // Then
        assertNotNull(response);
        assertEquals(2, response.getAgents().length);
        assertEquals("Agent1", response.getAgents()[0].getName());
        assertEquals("Agent2", response.getAgents()[1].getName());
        verify(agentMapper).selectAll();
    }

    @Test
    void testGetAgents_Empty() {
        // Given
        when(agentMapper.selectAll()).thenReturn(Collections.emptyList());

        // When
        GetAgentsResponse response = agentFacadeService.getAgents();

        // Then
        assertNotNull(response);
        assertEquals(0, response.getAgents().length);
    }

    @Test
    void testCreateAgent_Success() throws JsonProcessingException {
        // Given
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName("NewAgent");
        request.setModel("deepseek-chat");
        request.setAllowedTools(List.of("tool1"));
        request.setAllowedKbs(List.of("kb1"));
        request.setChatOptions(AgentDTO.ChatOptions.defaultOptions());

        AgentDTO dto = AgentDTO.builder()
                .name("NewAgent")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(List.of("tool1"))
                .allowedKbs(List.of("kb1"))
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();
        Agent entity = buildAgent(null, "NewAgent");

        when(agentConverter.toDTO(request)).thenReturn(dto);
        when(agentConverter.toEntity(dto)).thenReturn(entity);
        when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
            Agent a = invocation.getArgument(0);
            a.setId("generated-id");
            return 1;
        });

        // When
        CreateAgentResponse response = agentFacadeService.createAgent(request);

        // Then
        assertNotNull(response);
        assertEquals("generated-id", response.getAgentId());
        verify(agentMapper).insert(any(Agent.class));
    }

    @Test
    void testCreateAgent_InsertFails() throws JsonProcessingException {
        // Given
        CreateAgentRequest request = new CreateAgentRequest();
        request.setName("NewAgent");

        AgentDTO dto = AgentDTO.builder().name("NewAgent").build();
        Agent entity = buildAgent(null, "NewAgent");

        when(agentConverter.toDTO(request)).thenReturn(dto);
        when(agentConverter.toEntity(dto)).thenReturn(entity);
        when(agentMapper.insert(any(Agent.class))).thenReturn(0);

        // When & Then
        BizException ex = assertThrows(BizException.class, () -> agentFacadeService.createAgent(request));
        assertTrue(ex.getMessage().contains("创建 agent 失败"));
    }

    @Test
    void testDeleteAgent_Success() {
        // Given
        Agent agent = buildAgent("a1", "Agent1");
        when(agentMapper.selectById("a1")).thenReturn(agent);
        when(agentMapper.deleteById("a1")).thenReturn(1);

        // When
        agentFacadeService.deleteAgent("a1");

        // Then
        verify(agentMapper).selectById("a1");
        verify(agentMapper).deleteById("a1");
    }

    @Test
    void testDeleteAgent_NotFound() {
        // Given
        when(agentMapper.selectById("nonexistent")).thenReturn(null);

        // When & Then
        BizException ex = assertThrows(BizException.class, () -> agentFacadeService.deleteAgent("nonexistent"));
        assertTrue(ex.getMessage().contains("Agent 不存在"));
    }

    @Test
    void testUpdateAgent_Success() throws JsonProcessingException {
        // Given
        String agentId = "a1";
        Agent existingAgent = buildAgent(agentId, "OldName");
        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setName("NewName");

        AgentDTO dto = AgentDTO.builder()
                .id(agentId)
                .name("OldName")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(List.of("tool1"))
                .allowedKbs(List.of("kb1"))
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();
        Agent updatedEntity = buildAgent(null, "NewName");

        when(agentMapper.selectById(agentId)).thenReturn(existingAgent);
        when(agentConverter.toDTO(existingAgent)).thenReturn(dto);
        doNothing().when(agentConverter).updateDTOFromRequest(dto, request);
        when(agentConverter.toEntity(dto)).thenReturn(updatedEntity);
        when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

        // When
        agentFacadeService.updateAgent(agentId, request);

        // Then
        verify(agentMapper).selectById(agentId);
        verify(agentMapper).updateById(any(Agent.class));
    }

    @Test
    void testUpdateAgent_NotFound() {
        // Given
        when(agentMapper.selectById("nonexistent")).thenReturn(null);

        UpdateAgentRequest request = new UpdateAgentRequest();
        request.setName("NewName");

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> agentFacadeService.updateAgent("nonexistent", request));
        assertTrue(ex.getMessage().contains("Agent 不存在"));
    }
}
