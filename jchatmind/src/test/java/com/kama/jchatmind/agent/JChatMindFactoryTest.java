package com.kama.jchatmind.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.agent.tools.DataBaseTools;
import com.kama.jchatmind.agent.tools.KnowledgeTools;
import com.kama.jchatmind.agent.tools.TerminateTool;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.SseService;
import com.kama.jchatmind.service.ToolFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JChatMindFactoryTest {

    @Mock
    private ChatClientRegistry chatClientRegistry;

    @Mock
    private SseService sseService;

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentConverter agentConverter;

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeBaseConverter knowledgeBaseConverter;

    @Mock
    private ToolFacadeService toolFacadeService;

    @Mock
    private ChatMessageFacadeService chatMessageFacadeService;

    @Mock
    private ChatMessageConverter chatMessageConverter;

    @Mock
    private ChatClient chatClient;

    private JChatMindFactory factory;

    private static final String AGENT_ID = "agent-001";
    private static final String SESSION_ID = "session-001";

    @BeforeEach
    void setUp() {
        factory = new JChatMindFactory(
                chatClientRegistry, sseService, agentMapper, agentConverter,
                knowledgeBaseMapper, knowledgeBaseConverter, toolFacadeService,
                chatMessageFacadeService, chatMessageConverter
        );
    }

    // ======================== Helper Methods ========================

    private Agent createTestAgent() {
        return Agent.builder()
                .id(AGENT_ID)
                .name("TestAgent")
                .description("A test agent")
                .systemPrompt("You are a test assistant.")
                .model("deepseek-chat")
                .allowedTools("[\"dataBaseTool\"]")
                .allowedKbs("[\"kb-001\"]")
                .chatOptions("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}")
                .build();
    }

    private AgentDTO createTestAgentDTO() {
        return AgentDTO.builder()
                .id(AGENT_ID)
                .name("TestAgent")
                .description("A test agent")
                .systemPrompt("You are a test assistant.")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(List.of("dataBaseTool"))
                .allowedKbs(List.of("kb-001"))
                .chatOptions(AgentDTO.ChatOptions.builder()
                        .temperature(0.7)
                        .topP(1.0)
                        .messageLength(10)
                        .build())
                .build();
    }

    private AgentDTO createMinimalAgentDTO() {
        return AgentDTO.builder()
                .id(AGENT_ID)
                .name("MinimalAgent")
                .description("Minimal")
                .systemPrompt("Hello")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(Collections.emptyList())
                .allowedKbs(Collections.emptyList())
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();
    }

    /**
     * Setup common mocks for a successful create flow
     */
    private void setupSuccessfulCreateMocks(Agent agent, AgentDTO agentDTO) throws JsonProcessingException {
        when(agentMapper.selectById(AGENT_ID)).thenReturn(agent);
        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);
        when(chatMessageFacadeService.getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt()))
                .thenReturn(Collections.emptyList());
        when(chatClientRegistry.get(agent.getModel())).thenReturn(chatClient);

        // Use real TerminateTool for fixed tools
        TerminateTool terminateTool = new TerminateTool();
        when(toolFacadeService.getFixedTools()).thenReturn(List.of(terminateTool));
        when(toolFacadeService.getOptionalTools()).thenReturn(Collections.emptyList());
    }

    // ======================== Test Cases ========================

    @Test
    @DisplayName("1. 正常创建Agent运行时")
    void testCreateAgent_Success() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO();
        setupSuccessfulCreateMocks(agent, agentDTO);

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(agentMapper).selectById(AGENT_ID);
        verify(agentConverter).toDTO(agent);
        verify(chatClientRegistry).get(agent.getModel());
    }

    @Test
    @DisplayName("2. Agent ID不存在时抛异常")
    void testCreateAgent_AgentNotFound() throws JsonProcessingException {
        // Given
        when(agentMapper.selectById("non-existent")).thenReturn(null);
        when(agentConverter.toDTO((Agent) null)).thenThrow(new IllegalArgumentException("Agent cannot be null"));

        // When & Then
        assertThrows(Exception.class, () -> factory.create("non-existent", SESSION_ID));
    }

    @Test
    @DisplayName("3. 有历史消息时正确加载到ChatMemory")
    void testLoadMemory_WithHistory() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO();
        setupSuccessfulCreateMocks(agent, agentDTO);

        // Setup history messages
        ChatMessageDTO userMsg = ChatMessageDTO.builder()
                .id("msg-1")
                .sessionId(SESSION_ID)
                .role(ChatMessageDTO.RoleType.USER)
                .content("Hello")
                .metadata(null)
                .build();
        ChatMessageDTO assistantMsg = ChatMessageDTO.builder()
                .id("msg-2")
                .sessionId(SESSION_ID)
                .role(ChatMessageDTO.RoleType.ASSISTANT)
                .content("Hi there!")
                .metadata(ChatMessageDTO.MetaData.builder()
                        .toolCalls(Collections.emptyList())
                        .build())
                .build();

        when(chatMessageFacadeService.getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt()))
                .thenReturn(List.of(userMsg, assistantMsg));

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(chatMessageFacadeService).getChatMessagesBySessionIdRecently(eq(SESSION_ID), eq(10));
    }

    @Test
    @DisplayName("4. 无历史消息时ChatMemory为空")
    void testLoadMemory_Empty() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO();
        setupSuccessfulCreateMocks(agent, agentDTO);

        when(chatMessageFacadeService.getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(chatMessageFacadeService).getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt());
    }

    @Test
    @DisplayName("5. Agent未配置可选工具时只有固定工具")
    void testResolveTools_FixedOnly() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO(); // allowedTools = empty
        setupSuccessfulCreateMocks(agent, agentDTO);

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(toolFacadeService).getFixedTools();
        // getOptionalTools should NOT be called when allowedTools is empty
        verify(toolFacadeService, never()).getOptionalTools();
    }

    @Test
    @DisplayName("6. Agent配置了可选工具")
    void testResolveTools_WithOptionalTools() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createTestAgentDTO(); // has "dataBaseTool" in allowedTools
        setupSuccessfulCreateMocks(agent, agentDTO);

        // Setup optional tools
        DataBaseTools dbTool = new DataBaseTools(null);
        when(toolFacadeService.getOptionalTools()).thenReturn(List.of(dbTool));

        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(toolFacadeService).getFixedTools();
        verify(toolFacadeService).getOptionalTools();
    }

    @Test
    @DisplayName("7. Agent配置了知识库时包含知识库信息")
    void testResolveTools_WithKnowledgeTools() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createTestAgentDTO(); // has kb-001 in allowedKbs

        setupSuccessfulCreateMocks(agent, agentDTO);
        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);

        KnowledgeBase kb = KnowledgeBase.builder()
                .id("kb-001")
                .name("TestKB")
                .description("Test knowledge base")
                .build();
        KnowledgeBaseDTO kbDTO = KnowledgeBaseDTO.builder()
                .id("kb-001")
                .name("TestKB")
                .description("Test knowledge base")
                .build();

        when(knowledgeBaseMapper.selectByIdBatch(List.of("kb-001"))).thenReturn(List.of(kb));
        when(knowledgeBaseConverter.toDTO(kb)).thenReturn(kbDTO);

        DataBaseTools dbTool = new DataBaseTools(null);
        when(toolFacadeService.getOptionalTools()).thenReturn(List.of(dbTool));

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(knowledgeBaseMapper).selectByIdBatch(List.of("kb-001"));
        verify(knowledgeBaseConverter).toDTO(kb);
    }

    @Test
    @DisplayName("8. ToolCallback列表正确构建")
    void testBuildToolCallbacks() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO();
        setupSuccessfulCreateMocks(agent, agentDTO);

        // TerminateTool has @Tool annotation, should produce callbacks
        TerminateTool terminateTool = new TerminateTool();
        when(toolFacadeService.getFixedTools()).thenReturn(List.of(terminateTool));

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        // The agent should have been created with tool callbacks from TerminateTool
        verify(chatClientRegistry).get(agent.getModel());
    }

    @Test
    @DisplayName("9. 知识库列表正确解析 - 无知识库配置")
    void testResolveKnowledgeBases_NoneConfigured() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = createMinimalAgentDTO(); // allowedKbs = empty
        setupSuccessfulCreateMocks(agent, agentDTO);

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        verify(knowledgeBaseMapper, never()).selectByIdBatch(any());
    }

    @Test
    @DisplayName("10. 无效模型名时的处理")
    void testInvalidModel() throws JsonProcessingException {
        // Given
        Agent agent = Agent.builder()
                .id(AGENT_ID)
                .name("TestAgent")
                .model("invalid-model")
                .allowedTools("[]")
                .allowedKbs("[]")
                .chatOptions("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}")
                .build();

        AgentDTO agentDTO = createMinimalAgentDTO();
        when(agentMapper.selectById(AGENT_ID)).thenReturn(agent);
        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);
        when(chatMessageFacadeService.getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt()))
                .thenReturn(Collections.emptyList());
        when(toolFacadeService.getFixedTools()).thenReturn(List.of(new TerminateTool()));
        when(toolFacadeService.getOptionalTools()).thenReturn(Collections.emptyList());

        // ChatClientRegistry returns null for invalid model
        when(chatClientRegistry.get("invalid-model")).thenReturn(null);

        // When & Then
        assertThrows(IllegalStateException.class, () -> factory.create(AGENT_ID, SESSION_ID));
    }

    @Test
    @DisplayName("11. 空系统提示词时正常处理")
    void testNullSystemPrompt() throws JsonProcessingException {
        // Given
        Agent agent = Agent.builder()
                .id(AGENT_ID)
                .name("TestAgent")
                .description("Test")
                .systemPrompt(null) // null system prompt
                .model("deepseek-chat")
                .allowedTools("[]")
                .allowedKbs("[]")
                .chatOptions("{\"temperature\":0.7,\"topP\":1.0,\"messageLength\":10}")
                .build();

        AgentDTO agentDTO = createMinimalAgentDTO();
        agentDTO.setSystemPrompt(null);

        when(agentMapper.selectById(AGENT_ID)).thenReturn(agent);
        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);
        when(chatMessageFacadeService.getChatMessagesBySessionIdRecently(eq(SESSION_ID), anyInt()))
                .thenReturn(Collections.emptyList());
        when(chatClientRegistry.get("deepseek-chat")).thenReturn(chatClient);
        when(toolFacadeService.getFixedTools()).thenReturn(List.of(new TerminateTool()));

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then - should create successfully with null system prompt
        assertNotNull(result);
    }

    @Test
    @DisplayName("12. 默认聊天选项 (temperature=0.7, topP=1.0)")
    void testChatOptionsDefault() throws JsonProcessingException {
        // Given
        Agent agent = createTestAgent();
        AgentDTO agentDTO = AgentDTO.builder()
                .id(AGENT_ID)
                .name("TestAgent")
                .description("Test")
                .systemPrompt("Hello")
                .model(AgentDTO.ModelType.DEEPSEEK_CHAT)
                .allowedTools(Collections.emptyList())
                .allowedKbs(Collections.emptyList())
                .chatOptions(AgentDTO.ChatOptions.defaultOptions())
                .build();

        setupSuccessfulCreateMocks(agent, agentDTO);
        when(agentConverter.toDTO(agent)).thenReturn(agentDTO);

        // When
        JChatMind result = factory.create(AGENT_ID, SESSION_ID);

        // Then
        assertNotNull(result);
        // Verify default options: temperature=0.7, topP=1.0, messageLength=10
        AgentDTO.ChatOptions defaultOpts = agentDTO.getChatOptions();
        assertEquals(0.7, defaultOpts.getTemperature());
        assertEquals(1.0, defaultOpts.getTopP());
        assertEquals(10, defaultOpts.getMessageLength());
    }
}
