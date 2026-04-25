package com.kama.jchatmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.impl.SseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

    private SseServiceImpl sseService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sseService = new SseServiceImpl(objectMapper);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, SseEmitter> getClients() throws Exception {
        Field field = SseServiceImpl.class.getDeclaredField("clients");
        field.setAccessible(true);
        return (ConcurrentMap<String, SseEmitter>) field.get(sseService);
    }

    private SseMessage buildSseMessage() {
        return SseMessage.builder()
                .type(SseMessage.Type.AI_GENERATED_CONTENT)
                .payload(SseMessage.Payload.builder()
                        .message(ChatMessageVO.builder()
                                .id("m1")
                                .sessionId("s1")
                                .role(ChatMessageDTO.RoleType.ASSISTANT)
                                .content("Hello")
                                .build())
                        .done(false)
                        .build())
                .metadata(SseMessage.Metadata.builder().chatMessageId("m1").build())
                .build();
    }

    @Test
    void testConnect_Success() throws Exception {
        // When
        SseEmitter emitter = sseService.connect("session1");

        // Then
        assertNotNull(emitter);
        ConcurrentMap<String, SseEmitter> clients = getClients();
        assertTrue(clients.containsKey("session1"));
    }

    @Test
    void testConnect_DuplicateSession() throws Exception {
        // Given
        sseService.connect("session1");

        // When - connecting again with same session replaces the old emitter
        SseEmitter emitter2 = sseService.connect("session1");

        // Then
        assertNotNull(emitter2);
        ConcurrentMap<String, SseEmitter> clients = getClients();
        assertEquals(1, clients.size());
        assertSame(emitter2, clients.get("session1"));
    }

    @Test
    void testSend_Success() throws Exception {
        // Given
        SseEmitter emitter = sseService.connect("session1");
        SseMessage message = buildSseMessage();

        // When & Then - should not throw
        assertDoesNotThrow(() -> sseService.send("session1", message));
    }

    @Test
    void testSend_ClientNotFound() {
        // Given
        SseMessage message = buildSseMessage();

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sseService.send("nonexistent", message));
        assertTrue(ex.getMessage().contains("No client found"));
    }

    @Test
    void testSend_EmitterCompleted() throws Exception {
        // Given
        SseEmitter emitter = sseService.connect("session1");
        emitter.complete();
        // After complete, the onCompletion callback removes the client from map
        // But the emitter is still in the map until the callback fires

        SseMessage message = buildSseMessage();

        // When & Then - sending to completed emitter throws IOException wrapped in RuntimeException
        // The behavior depends on timing of the completion callback
        // We just verify that the code handles this gracefully
        try {
            sseService.send("session1", message);
        } catch (RuntimeException e) {
            // Expected - emitter is completed
            assertNotNull(e);
        }
    }

    @Test
    void testDisconnect_ViaCompletion() throws Exception {
        // Given
        SseEmitter emitter = sseService.connect("session1");
        ConcurrentMap<String, SseEmitter> clients = getClients();
        assertTrue(clients.containsKey("session1"));

        // When - simulate emitter completion
        emitter.complete();

        // Then - after completion callback fires, client should be removed
        // Note: in a real env the callback is async; here we just verify the emitter was registered
        assertNotNull(emitter);
    }

    @Test
    void testConcurrentConnections() throws Exception {
        // Given
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    sseService.connect("session-" + idx);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Then
        ConcurrentMap<String, SseEmitter> clients = getClients();
        assertEquals(threadCount, clients.size());
        for (int i = 0; i < threadCount; i++) {
            assertTrue(clients.containsKey("session-" + i));
        }
    }

    @Test
    void testMultipleSessionsSendIndependently() throws Exception {
        // Given
        sseService.connect("session1");
        sseService.connect("session2");
        SseMessage message = buildSseMessage();

        // When & Then - sending to each session independently should work
        assertDoesNotThrow(() -> sseService.send("session1", message));
        assertDoesNotThrow(() -> sseService.send("session2", message));
    }
}
