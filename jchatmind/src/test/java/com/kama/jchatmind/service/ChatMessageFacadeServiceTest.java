package com.kama.jchatmind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ChatMessageMapper;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.entity.ChatMessage;
import com.kama.jchatmind.model.request.CreateChatMessageRequest;
import com.kama.jchatmind.model.request.UpdateChatMessageRequest;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.response.GetChatMessagesResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.impl.ChatMessageFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageFacadeServiceTest {

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ChatMessageConverter chatMessageConverter;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private ChatMessageFacadeServiceImpl chatMessageFacadeService;

    private ChatMessage buildChatMessage(String id, String sessionId, String role, String content) {
        return ChatMessage.builder()
                .id(id)
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private ChatMessageVO buildChatMessageVO(String id, String sessionId, String content) {
        return ChatMessageVO.builder()
                .id(id)
                .sessionId(sessionId)
                .role(ChatMessageDTO.RoleType.USER)
                .content(content)
                .build();
    }

    @Test
    void testGetChatMessagesBySessionId_Success() throws JsonProcessingException {
        // Given
        String sessionId = "s1";
        ChatMessage msg1 = buildChatMessage("m1", sessionId, "user", "Hello");
        ChatMessage msg2 = buildChatMessage("m2", sessionId, "assistant", "Hi");
        when(chatMessageMapper.selectBySessionId(sessionId)).thenReturn(Arrays.asList(msg1, msg2));
        when(chatMessageConverter.toVO(msg1)).thenReturn(buildChatMessageVO("m1", sessionId, "Hello"));
        when(chatMessageConverter.toVO(msg2)).thenReturn(
                ChatMessageVO.builder().id("m2").sessionId(sessionId)
                        .role(ChatMessageDTO.RoleType.ASSISTANT).content("Hi").build());

        // When
        GetChatMessagesResponse response = chatMessageFacadeService.getChatMessagesBySessionId(sessionId);

        // Then
        assertNotNull(response);
        assertEquals(2, response.getChatMessages().length);
        assertEquals("Hello", response.getChatMessages()[0].getContent());
        verify(chatMessageMapper).selectBySessionId(sessionId);
    }

    @Test
    void testGetChatMessagesBySessionId_Empty() {
        // Given
        when(chatMessageMapper.selectBySessionId("s1")).thenReturn(Collections.emptyList());

        // When
        GetChatMessagesResponse response = chatMessageFacadeService.getChatMessagesBySessionId("s1");

        // Then
        assertNotNull(response);
        assertEquals(0, response.getChatMessages().length);
    }

    @Test
    void testGetChatMessagesBySessionIdRecently() throws JsonProcessingException {
        // Given
        String sessionId = "s1";
        ChatMessage msg1 = buildChatMessage("m1", sessionId, "user", "Recent msg");
        when(chatMessageMapper.selectBySessionIdRecently(sessionId, 5)).thenReturn(List.of(msg1));

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .id("m1").sessionId(sessionId)
                .role(ChatMessageDTO.RoleType.USER).content("Recent msg").build();
        when(chatMessageConverter.toDTO(msg1)).thenReturn(dto);

        // When
        List<ChatMessageDTO> result = chatMessageFacadeService.getChatMessagesBySessionIdRecently(sessionId, 5);

        // Then
        assertEquals(1, result.size());
        assertEquals("Recent msg", result.get(0).getContent());
        verify(chatMessageMapper).selectBySessionIdRecently(sessionId, 5);
    }

    @Test
    void testCreateChatMessage_Success() throws JsonProcessingException {
        // Given
        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .agentId("agent1")
                .sessionId("s1")
                .role(ChatMessageDTO.RoleType.USER)
                .content("Hello")
                .build();

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .sessionId("s1").role(ChatMessageDTO.RoleType.USER).content("Hello").build();
        ChatMessage entity = buildChatMessage(null, "s1", "user", "Hello");

        when(chatMessageConverter.toDTO(request)).thenReturn(dto);
        when(chatMessageConverter.toEntity(dto)).thenReturn(entity);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage m = invocation.getArgument(0);
            m.setId("gen-id");
            return 1;
        });

        // When
        CreateChatMessageResponse response = chatMessageFacadeService.createChatMessage(request);

        // Then
        assertNotNull(response);
        assertEquals("gen-id", response.getChatMessageId());
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void testCreateChatMessage_InsertFails() throws JsonProcessingException {
        // Given
        CreateChatMessageRequest request = CreateChatMessageRequest.builder()
                .agentId("agent1").sessionId("s1")
                .role(ChatMessageDTO.RoleType.USER).content("Hello").build();

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .sessionId("s1").role(ChatMessageDTO.RoleType.USER).content("Hello").build();
        ChatMessage entity = buildChatMessage(null, "s1", "user", "Hello");

        when(chatMessageConverter.toDTO(request)).thenReturn(dto);
        when(chatMessageConverter.toEntity(dto)).thenReturn(entity);
        when(chatMessageMapper.insert(any(ChatMessage.class))).thenReturn(0);

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> chatMessageFacadeService.createChatMessage(request));
        assertTrue(ex.getMessage().contains("创建聊天消息失败"));
    }

    @Test
    void testDeleteChatMessage_Success() {
        // Given
        ChatMessage msg = buildChatMessage("m1", "s1", "user", "Hello");
        when(chatMessageMapper.selectById("m1")).thenReturn(msg);
        when(chatMessageMapper.deleteById("m1")).thenReturn(1);

        // When
        chatMessageFacadeService.deleteChatMessage("m1");

        // Then
        verify(chatMessageMapper).selectById("m1");
        verify(chatMessageMapper).deleteById("m1");
    }

    @Test
    void testUpdateChatMessage_Success() throws JsonProcessingException {
        // Given
        String msgId = "m1";
        ChatMessage existing = buildChatMessage(msgId, "s1", "user", "Old content");
        UpdateChatMessageRequest request = new UpdateChatMessageRequest();
        request.setContent("New content");

        ChatMessageDTO dto = ChatMessageDTO.builder()
                .id(msgId).sessionId("s1")
                .role(ChatMessageDTO.RoleType.USER).content("Old content").build();
        ChatMessage updatedEntity = buildChatMessage(null, "s1", "user", "New content");

        when(chatMessageMapper.selectById(msgId)).thenReturn(existing);
        when(chatMessageConverter.toDTO(existing)).thenReturn(dto);
        doNothing().when(chatMessageConverter).updateDTOFromRequest(dto, request);
        when(chatMessageConverter.toEntity(dto)).thenReturn(updatedEntity);
        when(chatMessageMapper.updateById(any(ChatMessage.class))).thenReturn(1);

        // When
        chatMessageFacadeService.updateChatMessage(msgId, request);

        // Then
        verify(chatMessageMapper).updateById(any(ChatMessage.class));
    }

    @Test
    void testUpdateChatMessage_NotFound() {
        // Given
        when(chatMessageMapper.selectById("nonexistent")).thenReturn(null);
        UpdateChatMessageRequest request = new UpdateChatMessageRequest();
        request.setContent("New content");

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> chatMessageFacadeService.updateChatMessage("nonexistent", request));
        assertTrue(ex.getMessage().contains("聊天消息不存在"));
    }
}
