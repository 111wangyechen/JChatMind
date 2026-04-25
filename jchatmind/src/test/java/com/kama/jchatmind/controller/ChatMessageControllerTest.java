package com.kama.jchatmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.request.CreateChatMessageRequest;
import com.kama.jchatmind.model.request.UpdateChatMessageRequest;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.response.GetChatMessagesResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatMessageController.class)
class ChatMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatMessageFacadeService chatMessageFacadeService;

    @Test
    void testGetChatMessages_Success() throws Exception {
        ChatMessageVO msg = ChatMessageVO.builder()
                .id("msg-1")
                .sessionId("session-1")
                .role(ChatMessageDTO.RoleType.USER)
                .content("Hello")
                .build();
        GetChatMessagesResponse response = GetChatMessagesResponse.builder()
                .chatMessages(new ChatMessageVO[]{msg})
                .build();
        when(chatMessageFacadeService.getChatMessagesBySessionId("session-1")).thenReturn(response);

        mockMvc.perform(get("/api/chat-messages/session/{sessionId}", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatMessages").isArray())
                .andExpect(jsonPath("$.data.chatMessages[0].id").value("msg-1"))
                .andExpect(jsonPath("$.data.chatMessages[0].content").value("Hello"));
    }

    @Test
    void testGetChatMessages_Empty() throws Exception {
        GetChatMessagesResponse response = GetChatMessagesResponse.builder()
                .chatMessages(new ChatMessageVO[]{})
                .build();
        when(chatMessageFacadeService.getChatMessagesBySessionId("session-1")).thenReturn(response);

        mockMvc.perform(get("/api/chat-messages/session/{sessionId}", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatMessages").isArray())
                .andExpect(jsonPath("$.data.chatMessages").isEmpty());
    }

    @Test
    void testCreateChatMessage_Success() throws Exception {
        CreateChatMessageResponse response = CreateChatMessageResponse.builder()
                .chatMessageId("msg-new")
                .build();
        when(chatMessageFacadeService.createChatMessage(any(CreateChatMessageRequest.class))).thenReturn(response);

        String requestBody = """
                {
                    "agentId": "agent-1",
                    "sessionId": "session-1",
                    "role": "user",
                    "content": "Hello AI"
                }
                """;

        mockMvc.perform(post("/api/chat-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatMessageId").value("msg-new"));
    }

    @Test
    void testCreateChatMessage_InvalidRequest() throws Exception {
        when(chatMessageFacadeService.createChatMessage(any(CreateChatMessageRequest.class)))
                .thenThrow(new BizException("Content is required"));

        mockMvc.perform(post("/api/chat-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Content is required"));
    }

    @Test
    void testCreateChatMessage_MissingSessionId() throws Exception {
        when(chatMessageFacadeService.createChatMessage(any(CreateChatMessageRequest.class)))
                .thenThrow(new BizException("Session ID is required"));

        String requestBody = """
                {
                    "role": "user",
                    "content": "Hello"
                }
                """;

        mockMvc.perform(post("/api/chat-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Session ID is required"));
    }

    @Test
    void testDeleteChatMessage_Success() throws Exception {
        doNothing().when(chatMessageFacadeService).deleteChatMessage("msg-1");

        mockMvc.perform(delete("/api/chat-messages/{chatMessageId}", "msg-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testDeleteChatMessage_NotFound() throws Exception {
        doThrow(new BizException("Chat message not found"))
                .when(chatMessageFacadeService).deleteChatMessage("non-existent");

        mockMvc.perform(delete("/api/chat-messages/{chatMessageId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Chat message not found"));
    }

    @Test
    void testUpdateChatMessage_Success() throws Exception {
        UpdateChatMessageRequest request = new UpdateChatMessageRequest();
        request.setContent("Updated content");
        doNothing().when(chatMessageFacadeService).updateChatMessage(eq("msg-1"), any(UpdateChatMessageRequest.class));

        mockMvc.perform(patch("/api/chat-messages/{chatMessageId}", "msg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testUpdateChatMessage_NotFound() throws Exception {
        doThrow(new BizException("Chat message not found"))
                .when(chatMessageFacadeService).updateChatMessage(eq("non-existent"), any(UpdateChatMessageRequest.class));

        UpdateChatMessageRequest request = new UpdateChatMessageRequest();
        request.setContent("Updated");

        mockMvc.perform(patch("/api/chat-messages/{chatMessageId}", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Chat message not found"));
    }

    @Test
    void testUpdateChatMessage_EmptyContent() throws Exception {
        doThrow(new BizException("Content cannot be empty"))
                .when(chatMessageFacadeService).updateChatMessage(eq("msg-1"), any(UpdateChatMessageRequest.class));

        mockMvc.perform(patch("/api/chat-messages/{chatMessageId}", "msg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Content cannot be empty"));
    }
}
