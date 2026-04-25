package com.kama.jchatmind.agent;

import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.agent.harness.HarnessConfig;
import com.kama.jchatmind.agent.harness.HarnessEngine;
import com.kama.jchatmind.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JChatMindTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private SseService sseService;

    @Mock
    private ChatMessageFacadeService chatMessageFacadeService;

    @Mock
    private ChatMessageConverter chatMessageConverter;

    @Mock
    private ToolCallingManager toolCallingManager;

    @Mock
    private HarnessEngine harnessEngine;

    private JChatMind agent;

    private static final String AGENT_ID = "test-agent-id";
    private static final String SESSION_ID = "test-session-id";
    private static final String SYSTEM_PROMPT = "You are a test assistant.";

    @BeforeEach
    void setUp() {
        agent = new JChatMind(
                AGENT_ID, "TestAgent", "Test Description", SYSTEM_PROMPT,
                chatClient, 20, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), SESSION_ID, sseService,
                chatMessageFacadeService, chatMessageConverter,
                harnessEngine
        );
        // Replace internally created toolCallingManager with mock
        ReflectionTestUtils.setField(agent, "toolCallingManager", toolCallingManager);

        // Configure harness mock to pass-through (non-intrusive defaults)
        when(harnessEngine.beforeThink(any(), any())).thenAnswer(i -> i.getArgument(0));
        when(harnessEngine.afterThink(any(), any(), any())).thenReturn(true);
        when(harnessEngine.beforeExecute(any(), any())).thenReturn(true);
        when(harnessEngine.shouldPlan(any())).thenReturn(false);
        when(harnessEngine.getConfig()).thenReturn(HarnessConfig.builder().build());
        when(harnessEngine.getMetricsReport()).thenReturn("");

        // Common mock setups
        when(chatMessageFacadeService.createChatMessage(any(ChatMessageDTO.class)))
                .thenReturn(CreateChatMessageResponse.builder().chatMessageId("msg-1").build());
        when(chatMessageConverter.toVO(any(ChatMessageDTO.class)))
                .thenReturn(ChatMessageVO.builder().id("msg-1").sessionId(SESSION_ID).build());
    }

    // ======================== Helper Methods ========================

    /**
     * Mock ChatClient chain to return the given ChatResponse
     */
    private void mockChatClientChain(ChatResponse chatResponse) {
        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(chatResponse);
    }

    /**
     * Create a ChatResponse with no tool calls
     */
    private ChatResponse createNoToolCallResponse(String content) {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(assistantMessage.getText()).thenReturn(content);
        when(assistantMessage.getToolCalls()).thenReturn(Collections.emptyList());
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.hasToolCalls()).thenReturn(false);

        return chatResponse;
    }

    /**
     * Create a ChatResponse with tool calls
     */
    private ChatResponse createToolCallResponse(String content, List<AssistantMessage.ToolCall> toolCalls) {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);

        when(assistantMessage.getText()).thenReturn(content);
        when(assistantMessage.getToolCalls()).thenReturn(toolCalls);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.hasToolCalls()).thenReturn(true);

        return chatResponse;
    }

    /**
     * Create a ToolExecutionResult with a non-terminate tool response
     */
    private ToolExecutionResult createToolExecutionResult(String toolName, String responseData) {
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse("call-id-1", toolName, responseData);
        ToolResponseMessage toolResponseMsg = ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();

        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("test"));
        history.add(toolResponseMsg);

        ToolExecutionResult result = mock(ToolExecutionResult.class);
        when(result.conversationHistory()).thenReturn(history);
        return result;
    }

    /**
     * Create a ToolExecutionResult with "terminate" tool response
     */
    private ToolExecutionResult createTerminateToolExecutionResult() {
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse("call-id-term", "terminate", "");
        ToolResponseMessage toolResponseMsg = ToolResponseMessage.builder()
                .responses(List.of(toolResponse))
                .build();

        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("test"));
        history.add(toolResponseMsg);

        ToolExecutionResult result = mock(ToolExecutionResult.class);
        when(result.conversationHistory()).thenReturn(history);
        return result;
    }

    // ======================== Test Cases ========================

    @Test
    @DisplayName("1. 无工具调用的基本聊天 - AI回复不包含工具调用，状态变为FINISHED")
    void testRunBasicChat_NoToolCalls() {
        // Given
        ChatResponse chatResponse = createNoToolCallResponse("Hello, I am an AI assistant.");
        mockChatClientChain(chatResponse);

        // When
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(chatMessageFacadeService, atLeastOnce()).createChatMessage(any(ChatMessageDTO.class));
        verify(sseService, atLeastOnce()).send(eq(SESSION_ID), any());
        verify(toolCallingManager, never()).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("2. 单次工具调用 - AI回复包含一个工具调用后结束")
    void testRunWithSingleToolCall() {
        // Given - first think returns tool call, second think returns no tool call
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "someTool", "{\"arg\":\"value\"}");
        ChatResponse toolCallResponse = createToolCallResponse("I'll call a tool", List.of(toolCall));
        ChatResponse finalResponse = createNoToolCallResponse("Here is the result.");

        ToolExecutionResult executionResult = createToolExecutionResult("someTool", "tool result");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, times(1)).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("3. 多个工具调用 - AI回复包含多个工具调用")
    void testRunWithMultipleToolCalls() {
        // Given - think returns multiple tool calls in one response
        AssistantMessage.ToolCall toolCall1 =
                new AssistantMessage.ToolCall("tc-1", "function", "tool1", "{\"arg\":\"val1\"}");
        AssistantMessage.ToolCall toolCall2 =
                new AssistantMessage.ToolCall("tc-2", "function", "tool2", "{\"arg\":\"val2\"}");
        ChatResponse toolCallResponse = createToolCallResponse("I'll call tools", List.of(toolCall1, toolCall2));
        ChatResponse finalResponse = createNoToolCallResponse("Done.");

        // Multiple tool responses
        ToolResponseMessage.ToolResponse resp1 =
                new ToolResponseMessage.ToolResponse("tc-1", "tool1", "result1");
        ToolResponseMessage.ToolResponse resp2 =
                new ToolResponseMessage.ToolResponse("tc-2", "tool2", "result2");
        ToolResponseMessage toolResponseMsg = ToolResponseMessage.builder()
                .responses(List.of(resp1, resp2))
                .build();
        List<Message> history = new ArrayList<>();
        history.add(new UserMessage("test"));
        history.add(toolResponseMsg);
        ToolExecutionResult executionResult = mock(ToolExecutionResult.class);
        when(executionResult.conversationHistory()).thenReturn(history);

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, times(1)).executeToolCalls(any(), any());
        // createChatMessage called for assistant + each tool response
        verify(chatMessageFacadeService, atLeast(3)).createChatMessage(any(ChatMessageDTO.class));
    }

    @Test
    @DisplayName("4. 链式工具调用 - 多轮think-execute循环")
    void testRunWithChainedToolCalls() {
        // Given - 3 rounds: think+execute, think+execute, think(no tools)->finish
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "myTool", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Calling tool", List.of(toolCall));
        ChatResponse finalResponse = createNoToolCallResponse("All done.");

        ToolExecutionResult executionResult = createToolExecutionResult("myTool", "result");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse)
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, times(2)).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("5. 达到最大迭代次数后停止")
    void testRunMaxIterationsReached() {
        // Given - think always returns tool calls
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "infiniteTool", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Calling", List.of(toolCall));

        ToolExecutionResult executionResult = createToolExecutionResult("infiniteTool", "result");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then - should stop at MAX_STEPS (20)
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, times(20)).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("6. 状态转换: IDLE -> FINISHED")
    void testRunStateTransition_IdleToFinished() {
        // Given
        AgentState initialState = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.IDLE, initialState);

        ChatResponse chatResponse = createNoToolCallResponse("Hello");
        mockChatClientChain(chatResponse);

        // When
        agent.run();

        // Then
        AgentState finalState = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, finalState);
    }

    @Test
    @DisplayName("7. 状态转换: IDLE -> ERROR (异常时)")
    void testRunStateTransition_IdleToError() {
        // Given - ChatClient throws exception
        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenThrow(new RuntimeException("AI service unavailable"));

        // When & Then
        assertThrows(RuntimeException.class, () -> agent.run());
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
    }

    @Test
    @DisplayName("8. think返回无工具调用 - 循环应立即结束")
    void testThinkReturnsNoToolCalls() {
        // Given
        ChatResponse chatResponse = createNoToolCallResponse("Direct answer");
        mockChatClientChain(chatResponse);

        // When
        agent.run();

        // Then - only one think call, no execute
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, never()).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("9. think返回有工具调用 - 应进入execute阶段")
    void testThinkReturnsToolCalls() {
        // Given
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "myTool", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Will execute tool", List.of(toolCall));
        ChatResponse finalResponse = createNoToolCallResponse("Done");

        ToolExecutionResult executionResult = createToolExecutionResult("myTool", "success");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then
        verify(toolCallingManager, times(1)).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("10. 工具执行成功")
    void testExecuteToolSuccess() {
        // Given
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "successTool", "{\"query\":\"test\"}");
        ChatResponse toolCallResponse = createToolCallResponse("Executing", List.of(toolCall));
        ChatResponse finalResponse = createNoToolCallResponse("Tool executed successfully");

        ToolExecutionResult executionResult = createToolExecutionResult("successTool", "Execution OK");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse)
                .thenReturn(finalResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(executionResult);

        // When
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager).executeToolCalls(any(Prompt.class), eq(toolCallResponse));
    }

    @Test
    @DisplayName("11. 工具执行时抛出异常")
    void testExecuteToolException() {
        // Given
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "failTool", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Calling", List.of(toolCall));

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException("Tool execution failed"));

        // When & Then
        assertThrows(RuntimeException.class, () -> agent.run());
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
    }

    @Test
    @DisplayName("12. 工具执行超时")
    void testExecuteToolTimeout() {
        // Given - simulate timeout via exception
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-1", "function", "slowTool", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Calling slow tool", List.of(toolCall));

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenThrow(new RuntimeException(new TimeoutException("Tool execution timed out")));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> agent.run());
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
        assertTrue(exception.getCause().getMessage().contains("timed out")
                || exception.getMessage().contains("Tool execution timed out"));
    }

    @Test
    @DisplayName("13. 消息成功保存到数据库")
    void testSaveMessageSuccess() {
        // Given
        ChatResponse chatResponse = createNoToolCallResponse("Saved message");
        mockChatClientChain(chatResponse);

        CreateChatMessageResponse saveResponse =
                CreateChatMessageResponse.builder().chatMessageId("saved-msg-1").build();
        when(chatMessageFacadeService.createChatMessage(any(ChatMessageDTO.class)))
                .thenReturn(saveResponse);

        // When
        agent.run();

        // Then
        verify(chatMessageFacadeService, atLeastOnce()).createChatMessage(any(ChatMessageDTO.class));
    }

    @Test
    @DisplayName("14. 消息保存失败不阻塞主流程（抛异常导致ERROR）")
    void testSaveMessageFailure() {
        // Given - createChatMessage throws exception
        ChatResponse chatResponse = createNoToolCallResponse("Message to save");

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(chatResponse);

        when(chatMessageFacadeService.createChatMessage(any(ChatMessageDTO.class)))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then - saveMessage failure causes run to throw
        assertThrows(RuntimeException.class, () -> agent.run());
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
    }

    @Test
    @DisplayName("15. SSE消息推送被正确调用")
    void testRefreshPendingMessages() {
        // Given
        ChatResponse chatResponse = createNoToolCallResponse("SSE test message");
        mockChatClientChain(chatResponse);

        // When
        agent.run();

        // Then
        verify(chatMessageConverter, atLeastOnce()).toVO(any(ChatMessageDTO.class));
        verify(sseService, atLeastOnce()).send(eq(SESSION_ID), any());
    }

    @Test
    @DisplayName("16. Terminate工具调用后循环终止")
    void testTerminateToolStopsLoop() {
        // Given - think returns tool call, execute returns terminate result
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("tc-term", "function", "terminate", "{}");
        ChatResponse toolCallResponse = createToolCallResponse("Task complete", List.of(toolCall));

        ToolExecutionResult terminateResult = createTerminateToolExecutionResult();

        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(toolCallResponse);

        when(toolCallingManager.executeToolCalls(any(Prompt.class), any(ChatResponse.class)))
                .thenReturn(terminateResult);

        // When
        agent.run();

        // Then - should stop after first execute (terminate tool stops loop)
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
        verify(toolCallingManager, times(1)).executeToolCalls(any(), any());
    }

    @Test
    @DisplayName("17. 空用户输入处理")
    void testEmptyUserInput() {
        // Given - agent created with empty memory (no user input)
        ChatResponse chatResponse = createNoToolCallResponse("No input received");
        mockChatClientChain(chatResponse);

        // When - run with empty memory (set in setUp)
        agent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.FINISHED, state);
    }

    @Test
    @DisplayName("18. 超长用户输入处理")
    void testVeryLongUserInput() {
        // Given - create agent with very long input in memory
        String longInput = "a".repeat(10000);
        List<Message> longMemory = new ArrayList<>();
        longMemory.add(new UserMessage(longInput));

        JChatMind longInputAgent = new JChatMind(
                AGENT_ID, "TestAgent", "Test", SYSTEM_PROMPT,
                chatClient, 20, longMemory, new ArrayList<>(),
                new ArrayList<>(), SESSION_ID, sseService,
                chatMessageFacadeService, chatMessageConverter,
                harnessEngine
        );
        ReflectionTestUtils.setField(longInputAgent, "toolCallingManager", toolCallingManager);

        ChatResponse chatResponse = createNoToolCallResponse("Processed long input");
        mockChatClientChain(chatResponse);

        // When
        longInputAgent.run();

        // Then
        AgentState state = (AgentState) ReflectionTestUtils.getField(longInputAgent, "agentState");
        assertEquals(AgentState.FINISHED, state);
    }

    @Test
    @DisplayName("19. AI模型返回null时的处理")
    void testChatClientReturnsNull() {
        // Given - chatResponse is null, Assert.notNull will throw
        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenReturn(null);

        // When & Then - Assert.notNull throws IllegalArgumentException wrapped in RuntimeException
        assertThrows(RuntimeException.class, () -> agent.run());
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
    }

    @Test
    @DisplayName("20. AI模型调用抛异常")
    void testChatClientThrowsException() {
        // Given
        when(chatClient.prompt(any(Prompt.class))
                .system(anyString())
                .toolCallbacks(any(ToolCallback[].class))
                .call()
                .chatClientResponse()
                .chatResponse())
                .thenThrow(new RuntimeException("Model API error: rate limit exceeded"));

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> agent.run());
        assertTrue(ex.getMessage().contains("Error running agent"));
        AgentState state = (AgentState) ReflectionTestUtils.getField(agent, "agentState");
        assertEquals(AgentState.ERROR, state);
    }
}
