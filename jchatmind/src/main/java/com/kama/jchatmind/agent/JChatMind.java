package com.kama.jchatmind.agent;

import com.kama.jchatmind.agent.harness.HarnessEngine;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class JChatMind {
    // 智能体 ID
    private String agentId;

    // 名称
    private String name;

    // 描述
    private String description;

    // 默认系统提示词
    private String systemPrompt;

    // 交互实例
    private ChatClient chatClient;

    // 状态
    private AgentState agentState;

    // 可用的工具
    private List<ToolCallback> availableTools;

    // 可访问的知识库
    private List<KnowledgeBaseDTO> availableKbs;

    // 工具调用管理器
    private ToolCallingManager toolCallingManager;

    // 模型的聊天记录
    private ChatMemory chatMemory;

    // 模型的聊天会话 ID
    private String chatSessionId;

    // 最多循环次数
    private static final Integer MAX_STEPS = 20;

    private static final Integer DEFAULT_MAX_MESSAGES = 20;

    // SpringAI 自带的 ChatOptions, 不是 AgentDTO.ChatOptions
    private ChatOptions chatOptions;

    // SSE 服务, 用于发送消息给前端
    private SseService sseService;

    private ChatMessageConverter chatMessageConverter;

    private ChatMessageFacadeService chatMessageFacadeService;

    // 最后一次的 ChatResponse
    private ChatResponse lastChatResponse;

    // AI 返回的，已经持久化，但是需要 sse 发给前端的消息
    private final List<ChatMessageDTO> pendingChatMessages = new ArrayList<>();

    // Harness 引擎
    private final HarnessEngine harness;

    public JChatMind() {
        this.harness = null;
    }

    public JChatMind(String agentId,
                     String name,
                     String description,
                     String systemPrompt,
                     ChatClient chatClient,
                     Integer maxMessages,
                     List<Message> memory,
                     List<ToolCallback> availableTools,
                     List<KnowledgeBaseDTO> availableKbs,
                     String chatSessionId,
                     SseService sseService,
                     ChatMessageFacadeService chatMessageFacadeService,
                     ChatMessageConverter chatMessageConverter,
                     HarnessEngine harness
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;

        this.chatClient = chatClient;

        this.availableTools = availableTools;
        this.availableKbs = availableKbs;

        this.chatSessionId = chatSessionId;
        this.sseService = sseService;

        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;

        this.harness = harness;

        this.agentState = AgentState.IDLE;

        // 保存聊天记录
        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(maxMessages == null ? DEFAULT_MAX_MESSAGES : maxMessages)
                .build();
        this.chatMemory.add(chatSessionId, memory);

        // 添加系统提示
        if (StringUtils.hasLength(systemPrompt)) {
            this.chatMemory.add(chatSessionId, new SystemMessage(systemPrompt));
        }

        // 关闭 SpringAI 自带的内部的工具调用自动执行功能
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();

        // 工具调用管理器
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    // 打印工具调用信息
    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("\n\n[ToolCalling] 无工具调用");
            return;
        }
        String logMessage = IntStream.range(0, toolCalls.size())
                .mapToObj(i -> {
                    AssistantMessage.ToolCall call = toolCalls.get(i);
                    return String.format(
                            "[ToolCalling #%d]\n- name      : %s\n- arguments : %s",
                            i + 1,
                            call.name(),
                            call.arguments()
                    );
                })
                .collect(Collectors.joining("\n\n"));
        log.info("\n\n========== Tool Calling ==========\n{}\n=================================\n", logMessage);
    }

    // 持久化 Message, 返回 chatMessageId
    // 需要 Agent 持久化的 Message 子类有以下两类
    // AssistantMessage
    // ToolResponseMessage

    // SystemMessage 不需要持久化
    // UserMessage 在每次用户发送问题之间就已经持久化过了
    private void saveMessage(Message message) {
        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder();
        if (message instanceof AssistantMessage assistantMessage) {
            ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.ASSISTANT)
                    .content(assistantMessage.getText())
                    .sessionId(this.chatSessionId)
                    .metadata(ChatMessageDTO.MetaData.builder()
                            .toolCalls(assistantMessage.getToolCalls())
                            .build())
                    .build();
            CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
            chatMessageDTO.setId(chatMessage.getChatMessageId());
            pendingChatMessages.add(chatMessageDTO);
        } else if (message instanceof ToolResponseMessage toolResponseMessage) {
            // 持久化 ToolResponseMessage
            for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
                ChatMessageDTO chatMessageDTO = builder.role(ChatMessageDTO.RoleType.TOOL)
                        .content(toolResponse.responseData())
                        .sessionId(this.chatSessionId)
                        .metadata(ChatMessageDTO.MetaData.builder()
                                .toolResponse(toolResponse)
                                .build())
                        .build();
                CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
                chatMessageDTO.setId(chatMessage.getChatMessageId());
                pendingChatMessages.add(chatMessageDTO);
            }
        } else {
            throw new IllegalArgumentException("不支持的 Message 类型: " + message.getClass().getName());
        }
    }

    // 刷新 pendingMessages, 将数据通过 sse 发送给前端
    private void refreshPendingMessages() {
        for (ChatMessageDTO message : pendingChatMessages) {
            ChatMessageVO vo = chatMessageConverter.toVO(message);
            SseMessage sseMessage = SseMessage.builder()
                    .type(SseMessage.Type.AI_GENERATED_CONTENT)
                    .payload(SseMessage.Payload.builder()
                            .message(vo)
                            .build())
                    .metadata(SseMessage.Metadata.builder()
                            .chatMessageId(message.getId())
                            .build())
                    .build();
            sseService.send(this.chatSessionId, sseMessage);
        }
        pendingChatMessages.clear();
    }

    // thinkPrompt 应该放到 system 中还是
    private boolean think() {
        String thinkPrompt = """
                现在你是一个智能的的具体「决策模块」
                请根据当前对话上下文，决定下一步的动作。
                                \s
                【额外信息】
                - 你目前拥有的知识库列表以及描述：%s
                - 如果有缺失的上下文时，优先从知识库中进行搜索
                """.formatted(this.availableKbs);

        // 将 thinkPrompt 通过 .user(thinkPrompt) 的方式构造进入 chatClient 中
        // 既能让每次 messageList 的最后一条是 本条提示词，
        // 又能够避免将 thinkPrompt 加入到聊天记录中
        Prompt prompt = Prompt.builder()
                .chatOptions(this.chatOptions)
                .messages(this.chatMemory.get(this.chatSessionId))
                .build();

        this.lastChatResponse = this.chatClient
                .prompt(prompt)
                .system(thinkPrompt)
                .toolCallbacks(this.availableTools.toArray(new ToolCallback[0]))
                .call()
                .chatClientResponse()
                .chatResponse();

        Assert.notNull(lastChatResponse, "Last chat client response cannot be null");

        AssistantMessage output = this.lastChatResponse
                .getResult()
                .getOutput();

        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 保存
        saveMessage(output);
        refreshPendingMessages();

        // 打印工具调用
        logToolCalls(toolCalls);

        // 如果工具调用不为空，则进入执行阶段
        return !toolCalls.isEmpty();
    }

    // 执行
    private void execute() {
        Assert.notNull(this.lastChatResponse, "Last chat client response cannot be null");

        if (!this.lastChatResponse.hasToolCalls()) {
            return;
        }

        Prompt prompt = Prompt.builder()
                .messages(this.chatMemory.get(this.chatSessionId))
                .chatOptions(this.chatOptions)
                .build();

        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, this.lastChatResponse);

        this.chatMemory.clear(this.chatSessionId);
        this.chatMemory.add(this.chatSessionId, toolExecutionResult.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult
                .conversationHistory()
                .get(toolExecutionResult.conversationHistory().size() - 1);

        String collect = toolResponseMessage.getResponses()
                .stream()
                .map(resp -> "工具" + resp.name() + "的返回结果为：" + resp.responseData())
                .collect(Collectors.joining("\n"));

        log.info("工具调用结果：{}", collect);

        // 保存工具调用
        saveMessage(toolResponseMessage);
        refreshPendingMessages();

        if (toolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().equals("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("任务结束");
        }
    }

    // 单个步骤模板
    private void step() {
        if (think()) {
            execute();
        } else { // 没有工具调用
            agentState = AgentState.FINISHED;
        }
    }

    // 运行
    public void run() {
        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent is not idle");
        }

        // Harness: 运行开始
        if (harness != null) {
            try {
                harness.onRunStart(this.chatSessionId);
            } catch (Exception e) {
                log.warn("[Harness] onRunStart 异常，降级跳过: {}", e.getMessage());
            }
        }
        int verificationRetryCount = 0;

        try {
            for (int i = 0; i < MAX_STEPS; i++) {
                int currentStep = i + 1;

                // Harness L1: 上下文预算检查和压缩
                if (harness != null) {
                    try {
                        agentState = AgentState.THINKING;
                        List<Message> currentMessages = this.chatMemory.get(this.chatSessionId);
                        List<Message> processedMessages = harness.beforeThink(currentMessages, this.chatSessionId);
                        // 如果消息被压缩了，更新 chatMemory
                        if (processedMessages != currentMessages) {
                            this.chatMemory.clear(this.chatSessionId);
                            this.chatMemory.add(this.chatSessionId, processedMessages);
                        }
                    } catch (Exception e) {
                        log.warn("[Harness] beforeThink 异常，降级跳过: {}", e.getMessage());
                    }
                }

                // Harness L3: 首轮可选规划
                if (harness != null && i == 0) {
                    try {
                        if (harness.shouldPlan(getLastUserMessage())) {
                            agentState = AgentState.PLANNING;
                            String plan = harness.plan(getLastUserMessage(), getAvailableToolNames());
                            log.info("[Harness] 执行计划: {}", plan);
                        }
                    } catch (Exception e) {
                        log.warn("[Harness] 规划异常，降级跳过: {}", e.getMessage());
                    }
                }

                // 原有 think
                agentState = AgentState.THINKING;
                boolean hasToolCalls = think();

                // Harness L5: 输出自验证
                if (harness != null) {
                    try {
                        AssistantMessage lastOutput = this.lastChatResponse.getResult().getOutput();
                        List<AssistantMessage.ToolCall> toolCalls = lastOutput.getToolCalls();
                        boolean verified = harness.afterThink(lastOutput, toolCalls,
                                this.chatMemory.get(this.chatSessionId));

                        if (!verified && verificationRetryCount < harness.getConfig().getMaxVerificationRetries()) {
                            // 验证失败，注入修正提示并重试
                            verificationRetryCount++;
                            String hint = harness.generateCorrectionHint();
                            if (hint != null) {
                                this.chatMemory.add(this.chatSessionId,
                                        new UserMessage(hint));
                            }
                            log.warn("[Harness] 输出验证未通过，第 {} 次重试", verificationRetryCount);
                            if (harness != null) {
                                try {
                                    harness.onStepComplete(currentStep, agentState.name());
                                } catch (Exception e) {
                                    log.warn("[Harness] onStepComplete 异常: {}", e.getMessage());
                                }
                            }
                            continue; // 重新 think
                        }
                        verificationRetryCount = 0; // 重置
                    } catch (Exception e) {
                        log.warn("[Harness] afterThink 异常，降级跳过: {}", e.getMessage());
                        verificationRetryCount = 0;
                    }
                }

                if (hasToolCalls) {
                    agentState = AgentState.EXECUTING;

                    // Harness L2+L6: 工具调用验证 + Guardrails
                    boolean executeAllowed = true;
                    if (harness != null) {
                        try {
                            AssistantMessage lastOutput = this.lastChatResponse.getResult().getOutput();
                            List<AssistantMessage.ToolCall> toolCalls = lastOutput.getToolCalls();
                            executeAllowed = harness.beforeExecute(toolCalls, this.availableTools);
                        } catch (Exception e) {
                            log.warn("[Harness] beforeExecute 异常，降级允许执行: {}", e.getMessage());
                            executeAllowed = true;
                        }
                    }

                    if (executeAllowed) {
                        try {
                            execute();
                            // Harness: 执行后处理
                            if (harness != null) {
                                try {
                                    List<Message> msgs = this.chatMemory.get(this.chatSessionId);
                                    Message lastMsg = msgs.get(msgs.size() - 1);
                                    if (lastMsg instanceof ToolResponseMessage trm) {
                                        harness.afterExecute(trm);
                                    }
                                } catch (Exception e) {
                                    log.warn("[Harness] afterExecute 异常: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            // Harness L6: 错误恢复
                            boolean recovered = false;
                            if (harness != null) {
                                try {
                                    recovered = harness.onError(e, "EXECUTE");
                                } catch (Exception ex) {
                                    log.warn("[Harness] onError 异常: {}", ex.getMessage());
                                }
                            }
                            if (!recovered) {
                                throw e;
                            }
                            log.info("[Harness] 工具执行错误已恢复");
                        }
                    } else {
                        // 工具调用被拒绝，注入拒绝原因到上下文
                        log.warn("[Harness] 工具调用被安全护栏拒绝");
                        this.chatMemory.add(this.chatSessionId,
                                new UserMessage(
                                        "系统提示：上一次的工具调用请求因安全原因被拒绝，请使用其他方式回答用户问题。"));
                    }
                } else {
                    agentState = AgentState.FINISHED;
                }

                // Harness L3+L5: 步骤完成追踪
                if (harness != null) {
                    try {
                        harness.onStepComplete(currentStep, agentState.name());
                    } catch (Exception e) {
                        log.warn("[Harness] onStepComplete 异常: {}", e.getMessage());
                    }
                }

                if (currentStep >= MAX_STEPS) {
                    agentState = AgentState.FINISHED;
                    log.warn("Max steps reached, stopping agent");
                }

                if (agentState == AgentState.FINISHED) {
                    break;
                }
            }
            agentState = AgentState.FINISHED;
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Error running agent", e);
            throw new RuntimeException("Error running agent", e);
        } finally {
            // Harness: 运行结束，输出指标报告
            if (harness != null) {
                try {
                    harness.onRunComplete(this.chatSessionId);
                    log.info("[Harness] 执行指标报告: {}", harness.getMetricsReport());
                } catch (Exception e) {
                    log.warn("[Harness] onRunComplete 异常: {}", e.getMessage());
                }
            }
        }
    }

    /** 获取最后一条用户消息文本 */
    private String getLastUserMessage() {
        List<Message> messages = this.chatMemory.get(this.chatSessionId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage userMsg) {
                return userMsg.getText();
            }
        }
        return "";
    }

    /** 获取可用工具名称列表 */
    private List<String> getAvailableToolNames() {
        return this.availableTools.stream()
                .map(tc -> tc.getToolDefinition().name())
                .toList();
    }

    @Override
    public String toString() {
        return "JChatMind {" +
                "name = " + name + ",\n" +
                "description = " + description + ",\n" +
                "agentId = " + agentId + ",\n" +
                "systemPrompt = " + systemPrompt + "}";
    }
}
