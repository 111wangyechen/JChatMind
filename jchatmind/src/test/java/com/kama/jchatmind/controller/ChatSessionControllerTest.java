package com.kama.jchatmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.model.request.CreateChatSessionRequest;
import com.kama.jchatmind.model.request.UpdateChatSessionRequest;
import com.kama.jchatmind.model.response.CreateChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionsResponse;
import com.kama.jchatmind.model.vo.ChatSessionVO;
import com.kama.jchatmind.service.ChatSessionFacadeService;
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

@WebMvcTest(ChatSessionController.class)
class ChatSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatSessionFacadeService chatSessionFacadeService;

    @Test
    void testGetChatSessions_Success() throws Exception {
        ChatSessionVO session = ChatSessionVO.builder()
                .id("session-1").agentId("agent-1").title("Test Session").build();
        GetChatSessionsResponse response = GetChatSessionsResponse.builder()
                .chatSessions(new ChatSessionVO[]{session}).build();
        when(chatSessionFacadeService.getChatSessions()).thenReturn(response);

        mockMvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSessions").isArray())
                .andExpect(jsonPath("$.data.chatSessions[0].id").value("session-1"))
                .andExpect(jsonPath("$.data.chatSessions[0].title").value("Test Session"));
    }

    @Test
    void testGetChatSessions_Empty() throws Exception {
        GetChatSessionsResponse response = GetChatSessionsResponse.builder()
                .chatSessions(new ChatSessionVO[]{}).build();
        when(chatSessionFacadeService.getChatSessions()).thenReturn(response);

        mockMvc.perform(get("/api/chat-sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSessions").isEmpty());
    }

    @Test
    void testGetChatSessionById_Success() throws Exception {
        ChatSessionVO session = ChatSessionVO.builder()
                .id("session-1").agentId("agent-1").title("My Session").build();
        GetChatSessionResponse response = GetChatSessionResponse.builder()
                .chatSession(session).build();
        when(chatSessionFacadeService.getChatSession("session-1")).thenReturn(response);

        mockMvc.perform(get("/api/chat-sessions/{chatSessionId}", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSession.id").value("session-1"))
                .andExpect(jsonPath("$.data.chatSession.title").value("My Session"));
    }

    @Test
    void testGetChatSessionById_NotFound() throws Exception {
        when(chatSessionFacadeService.getChatSession("non-existent"))
                .thenThrow(new BizException("Chat session not found"));

        mockMvc.perform(get("/api/chat-sessions/{chatSessionId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Chat session not found"));
    }

    @Test
    void testGetChatSessionsByAgentId_Success() throws Exception {
        ChatSessionVO session = ChatSessionVO.builder()
                .id("session-1").agentId("agent-1").title("Agent Session").build();
        GetChatSessionsResponse response = GetChatSessionsResponse.builder()
                .chatSessions(new ChatSessionVO[]{session}).build();
        when(chatSessionFacadeService.getChatSessionsByAgentId("agent-1")).thenReturn(response);

        mockMvc.perform(get("/api/chat-sessions/agent/{agentId}", "agent-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSessions[0].agentId").value("agent-1"));
    }

    @Test
    void testGetChatSessionsByAgentId_Empty() throws Exception {
        GetChatSessionsResponse response = GetChatSessionsResponse.builder()
                .chatSessions(new ChatSessionVO[]{}).build();
        when(chatSessionFacadeService.getChatSessionsByAgentId("agent-999")).thenReturn(response);

        mockMvc.perform(get("/api/chat-sessions/agent/{agentId}", "agent-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSessions").isEmpty());
    }

    @Test
    void testCreateChatSession_Success() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        request.setAgentId("agent-1");
        request.setTitle("New Session");

        CreateChatSessionResponse response = CreateChatSessionResponse.builder()
                .chatSessionId("session-new").build();
        when(chatSessionFacadeService.createChatSession(any(CreateChatSessionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/chat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.chatSessionId").value("session-new"));
    }

    @Test
    void testCreateChatSession_InvalidRequest() throws Exception {
        when(chatSessionFacadeService.createChatSession(any(CreateChatSessionRequest.class)))
                .thenThrow(new BizException("Agent ID is required"));

        mockMvc.perform(post("/api/chat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Agent ID is required"));
    }

    @Test
    void testDeleteChatSession_Success() throws Exception {
        doNothing().when(chatSessionFacadeService).deleteChatSession("session-1");

        mockMvc.perform(delete("/api/chat-sessions/{chatSessionId}", "session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testDeleteChatSession_NotFound() throws Exception {
        doThrow(new BizException("Chat session not found"))
                .when(chatSessionFacadeService).deleteChatSession("non-existent");

        mockMvc.perform(delete("/api/chat-sessions/{chatSessionId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Chat session not found"));
    }

    @Test
    void testUpdateChatSession_Success() throws Exception {
        UpdateChatSessionRequest request = new UpdateChatSessionRequest();
        request.setTitle("Updated Title");
        doNothing().when(chatSessionFacadeService).updateChatSession(eq("session-1"), any(UpdateChatSessionRequest.class));

        mockMvc.perform(patch("/api/chat-sessions/{chatSessionId}", "session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testUpdateChatSession_NotFound() throws Exception {
        doThrow(new BizException("Chat session not found"))
                .when(chatSessionFacadeService).updateChatSession(eq("non-existent"), any(UpdateChatSessionRequest.class));

        UpdateChatSessionRequest request = new UpdateChatSessionRequest();
        request.setTitle("Updated");

        mockMvc.perform(patch("/api/chat-sessions/{chatSessionId}", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Chat session not found"));
    }
}
