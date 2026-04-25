package com.kama.jchatmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.model.request.CreateKnowledgeBaseRequest;
import com.kama.jchatmind.model.request.UpdateKnowledgeBaseRequest;
import com.kama.jchatmind.model.response.CreateKnowledgeBaseResponse;
import com.kama.jchatmind.model.response.GetKnowledgeBasesResponse;
import com.kama.jchatmind.model.vo.KnowledgeBaseVO;
import com.kama.jchatmind.service.KnowledgeBaseFacadeService;
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

@WebMvcTest(KnowledgeBaseController.class)
class KnowledgeBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KnowledgeBaseFacadeService knowledgeBaseFacadeService;

    @Test
    void testGetKnowledgeBases_Success() throws Exception {
        KnowledgeBaseVO kb = KnowledgeBaseVO.builder()
                .id("kb-1").name("Test KB").description("A test knowledge base").build();
        GetKnowledgeBasesResponse response = GetKnowledgeBasesResponse.builder()
                .knowledgeBases(new KnowledgeBaseVO[]{kb}).build();
        when(knowledgeBaseFacadeService.getKnowledgeBases()).thenReturn(response);

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.knowledgeBases").isArray())
                .andExpect(jsonPath("$.data.knowledgeBases[0].id").value("kb-1"))
                .andExpect(jsonPath("$.data.knowledgeBases[0].name").value("Test KB"));
    }

    @Test
    void testGetKnowledgeBases_Empty() throws Exception {
        GetKnowledgeBasesResponse response = GetKnowledgeBasesResponse.builder()
                .knowledgeBases(new KnowledgeBaseVO[]{}).build();
        when(knowledgeBaseFacadeService.getKnowledgeBases()).thenReturn(response);

        mockMvc.perform(get("/api/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.knowledgeBases").isEmpty());
    }

    @Test
    void testCreateKnowledgeBase_Success() throws Exception {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("New KB");
        request.setDescription("New knowledge base");

        CreateKnowledgeBaseResponse response = CreateKnowledgeBaseResponse.builder()
                .knowledgeBaseId("kb-new").build();
        when(knowledgeBaseFacadeService.createKnowledgeBase(any(CreateKnowledgeBaseRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.knowledgeBaseId").value("kb-new"));
    }

    @Test
    void testCreateKnowledgeBase_InvalidRequest() throws Exception {
        when(knowledgeBaseFacadeService.createKnowledgeBase(any(CreateKnowledgeBaseRequest.class)))
                .thenThrow(new BizException("Name is required"));

        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Name is required"));
    }

    @Test
    void testCreateKnowledgeBase_MissingName() throws Exception {
        when(knowledgeBaseFacadeService.createKnowledgeBase(any(CreateKnowledgeBaseRequest.class)))
                .thenThrow(new BizException("Knowledge base name is required"));

        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setDescription("desc only");

        mockMvc.perform(post("/api/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Knowledge base name is required"));
    }

    @Test
    void testDeleteKnowledgeBase_Success() throws Exception {
        doNothing().when(knowledgeBaseFacadeService).deleteKnowledgeBase("kb-1");

        mockMvc.perform(delete("/api/knowledge-bases/{knowledgeBaseId}", "kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testDeleteKnowledgeBase_NotFound() throws Exception {
        doThrow(new BizException("Knowledge base not found"))
                .when(knowledgeBaseFacadeService).deleteKnowledgeBase("non-existent");

        mockMvc.perform(delete("/api/knowledge-bases/{knowledgeBaseId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Knowledge base not found"));
    }

    @Test
    void testUpdateKnowledgeBase_Success() throws Exception {
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("Updated KB");
        doNothing().when(knowledgeBaseFacadeService).updateKnowledgeBase(eq("kb-1"), any(UpdateKnowledgeBaseRequest.class));

        mockMvc.perform(patch("/api/knowledge-bases/{knowledgeBaseId}", "kb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testUpdateKnowledgeBase_NotFound() throws Exception {
        doThrow(new BizException("Knowledge base not found"))
                .when(knowledgeBaseFacadeService).updateKnowledgeBase(eq("non-existent"), any(UpdateKnowledgeBaseRequest.class));

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("Updated");

        mockMvc.perform(patch("/api/knowledge-bases/{knowledgeBaseId}", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Knowledge base not found"));
    }

    @Test
    void testUpdateKnowledgeBase_InvalidRequest() throws Exception {
        doThrow(new BizException("Invalid update request"))
                .when(knowledgeBaseFacadeService).updateKnowledgeBase(eq("kb-1"), any(UpdateKnowledgeBaseRequest.class));

        mockMvc.perform(patch("/api/knowledge-bases/{knowledgeBaseId}", "kb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Invalid update request"));
    }
}
