package com.kama.jchatmind.controller;

import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.service.SseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SseController.class)
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseService sseService;

    @Test
    void testConnect_Success() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(sseService.connect("session-1")).thenReturn(emitter);

        mockMvc.perform(get("/sse/connect/{chatSessionId}", "session-1"))
                .andExpect(status().isOk());
    }

    @Test
    void testConnect_InvalidSessionId() throws Exception {
        when(sseService.connect("invalid-id"))
                .thenThrow(new BizException("Invalid session ID"));

        mockMvc.perform(get("/sse/connect/{chatSessionId}", "invalid-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Invalid session ID"));
    }

    @Test
    void testConnect_ReturnsMediaTypeEventStream() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(sseService.connect("session-1")).thenReturn(emitter);

        mockMvc.perform(get("/sse/connect/{chatSessionId}", "session-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }

    @Test
    void testConnect_ServiceException() throws Exception {
        when(sseService.connect("session-err"))
                .thenThrow(new RuntimeException("Connection failed"));

        mockMvc.perform(get("/sse/connect/{chatSessionId}", "session-err"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @Test
    void testConnect_EmptySessionId() throws Exception {
        // When chatSessionId path variable is empty, Spring returns 404
        mockMvc.perform(get("/sse/connect/"))
                .andExpect(status().isNotFound());
    }
}
