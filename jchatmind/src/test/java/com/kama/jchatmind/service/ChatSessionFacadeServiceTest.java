package com.kama.jchatmind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.ChatSessionConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.model.dto.ChatSessionDTO;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.model.request.CreateChatSessionRequest;
import com.kama.jchatmind.model.request.UpdateChatSessionRequest;
import com.kama.jchatmind.model.response.CreateChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionResponse;
import com.kama.jchatmind.model.response.GetChatSessionsResponse;
import com.kama.jchatmind.model.vo.ChatSessionVO;
import com.kama.jchatmind.service.impl.ChatSessionFacadeServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionFacadeServiceTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Mock
    private ChatSessionConverter chatSessionConverter;

    @InjectMocks
    private ChatSessionFacadeServiceImpl chatSessionFacadeService;

    private ChatSession buildChatSession(String id, String agentId, String title) {
        return ChatSession.builder()
                .id(id)
                .agentId(agentId)
                .title(title)
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ChatSessionVO buildChatSessionVO(String id, String agentId, String title) {
        return ChatSessionVO.builder()
                .id(id)
                .agentId(agentId)
                .title(title)
                .build();
    }

    @Test
    void testGetChatSessions_Success() throws JsonProcessingException {
        // Given
        ChatSession s1 = buildChatSession("s1", "a1", "Session 1");
        ChatSession s2 = buildChatSession("s2", "a1", "Session 2");
        when(chatSessionMapper.selectAll()).thenReturn(Arrays.asList(s1, s2));
        when(chatSessionConverter.toVO(s1)).thenReturn(buildChatSessionVO("s1", "a1", "Session 1"));
        when(chatSessionConverter.toVO(s2)).thenReturn(buildChatSessionVO("s2", "a1", "Session 2"));

        // When
        GetChatSessionsResponse response = chatSessionFacadeService.getChatSessions();

        // Then
        assertNotNull(response);
        assertEquals(2, response.getChatSessions().length);
        verify(chatSessionMapper).selectAll();
    }

    @Test
    void testGetChatSessions_Empty() {
        // Given
        when(chatSessionMapper.selectAll()).thenReturn(Collections.emptyList());

        // When
        GetChatSessionsResponse response = chatSessionFacadeService.getChatSessions();

        // Then
        assertNotNull(response);
        assertEquals(0, response.getChatSessions().length);
    }

    @Test
    void testGetChatSessionById_Success() throws JsonProcessingException {
        // Given
        ChatSession session = buildChatSession("s1", "a1", "Test Session");
        ChatSessionVO vo = buildChatSessionVO("s1", "a1", "Test Session");
        when(chatSessionMapper.selectById("s1")).thenReturn(session);
        when(chatSessionConverter.toVO(session)).thenReturn(vo);

        // When
        GetChatSessionResponse response = chatSessionFacadeService.getChatSession("s1");

        // Then
        assertNotNull(response);
        assertEquals("s1", response.getChatSession().getId());
        assertEquals("Test Session", response.getChatSession().getTitle());
    }

    @Test
    void testGetChatSessionById_NotFound() {
        // Given
        when(chatSessionMapper.selectById("nonexistent")).thenReturn(null);

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> chatSessionFacadeService.getChatSession("nonexistent"));
        assertTrue(ex.getMessage().contains("聊天会话不存在"));
    }

    @Test
    void testGetChatSessionsByAgentId_Success() throws JsonProcessingException {
        // Given
        String agentId = "a1";
        ChatSession s1 = buildChatSession("s1", agentId, "Session 1");
        when(chatSessionMapper.selectByAgentId(agentId)).thenReturn(List.of(s1));
        when(chatSessionConverter.toVO(s1)).thenReturn(buildChatSessionVO("s1", agentId, "Session 1"));

        // When
        GetChatSessionsResponse response = chatSessionFacadeService.getChatSessionsByAgentId(agentId);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getChatSessions().length);
        assertEquals(agentId, response.getChatSessions()[0].getAgentId());
    }

    @Test
    void testCreateChatSession_Success() throws JsonProcessingException {
        // Given
        CreateChatSessionRequest request = new CreateChatSessionRequest();
        request.setAgentId("a1");
        request.setTitle("New Session");

        ChatSessionDTO dto = ChatSessionDTO.builder().agentId("a1").title("New Session").build();
        ChatSession entity = buildChatSession(null, "a1", "New Session");

        when(chatSessionConverter.toDTO(request)).thenReturn(dto);
        when(chatSessionConverter.toEntity(dto)).thenReturn(entity);
        when(chatSessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId("gen-session-id");
            return 1;
        });

        // When
        CreateChatSessionResponse response = chatSessionFacadeService.createChatSession(request);

        // Then
        assertNotNull(response);
        assertEquals("gen-session-id", response.getChatSessionId());
        verify(chatSessionMapper).insert(any(ChatSession.class));
    }

    @Test
    void testDeleteChatSession_Success() {
        // Given
        ChatSession session = buildChatSession("s1", "a1", "Session");
        when(chatSessionMapper.selectById("s1")).thenReturn(session);
        when(chatSessionMapper.deleteById("s1")).thenReturn(1);

        // When
        chatSessionFacadeService.deleteChatSession("s1");

        // Then
        verify(chatSessionMapper).selectById("s1");
        verify(chatSessionMapper).deleteById("s1");
    }

    @Test
    void testUpdateChatSession_Success() throws JsonProcessingException {
        // Given
        String sessionId = "s1";
        ChatSession existing = buildChatSession(sessionId, "a1", "Old Title");
        UpdateChatSessionRequest request = new UpdateChatSessionRequest();
        request.setTitle("New Title");

        ChatSessionDTO dto = ChatSessionDTO.builder()
                .id(sessionId).agentId("a1").title("Old Title").build();
        ChatSession updatedEntity = buildChatSession(null, "a1", "New Title");

        when(chatSessionMapper.selectById(sessionId)).thenReturn(existing);
        when(chatSessionConverter.toDTO(existing)).thenReturn(dto);
        doNothing().when(chatSessionConverter).updateDTOFromRequest(dto, request);
        when(chatSessionConverter.toEntity(dto)).thenReturn(updatedEntity);
        when(chatSessionMapper.updateById(any(ChatSession.class))).thenReturn(1);

        // When
        chatSessionFacadeService.updateChatSession(sessionId, request);

        // Then
        verify(chatSessionMapper).updateById(any(ChatSession.class));
    }

    @Test
    void testUpdateChatSession_NotFound() {
        // Given
        when(chatSessionMapper.selectById("nonexistent")).thenReturn(null);
        UpdateChatSessionRequest request = new UpdateChatSessionRequest();
        request.setTitle("New Title");

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> chatSessionFacadeService.updateChatSession("nonexistent", request));
        assertTrue(ex.getMessage().contains("聊天会话不存在"));
    }
}
