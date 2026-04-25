package com.kama.jchatmind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.model.request.CreateDocumentRequest;
import com.kama.jchatmind.model.request.UpdateDocumentRequest;
import com.kama.jchatmind.model.response.CreateDocumentResponse;
import com.kama.jchatmind.model.response.GetDocumentsResponse;
import com.kama.jchatmind.model.vo.DocumentVO;
import com.kama.jchatmind.service.DocumentFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentFacadeService documentFacadeService;

    @Test
    void testGetDocuments_Success() throws Exception {
        DocumentVO doc = DocumentVO.builder()
                .id("doc-1").kbId("kb-1").filename("test.pdf").filetype("pdf").size(1024L).build();
        GetDocumentsResponse response = GetDocumentsResponse.builder()
                .documents(new DocumentVO[]{doc}).build();
        when(documentFacadeService.getDocuments()).thenReturn(response);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documents").isArray())
                .andExpect(jsonPath("$.data.documents[0].id").value("doc-1"))
                .andExpect(jsonPath("$.data.documents[0].filename").value("test.pdf"));
    }

    @Test
    void testGetDocuments_Empty() throws Exception {
        GetDocumentsResponse response = GetDocumentsResponse.builder()
                .documents(new DocumentVO[]{}).build();
        when(documentFacadeService.getDocuments()).thenReturn(response);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documents").isEmpty());
    }

    @Test
    void testGetDocumentsByKbId_Success() throws Exception {
        DocumentVO doc = DocumentVO.builder()
                .id("doc-1").kbId("kb-1").filename("file.txt").filetype("txt").size(512L).build();
        GetDocumentsResponse response = GetDocumentsResponse.builder()
                .documents(new DocumentVO[]{doc}).build();
        when(documentFacadeService.getDocumentsByKbId("kb-1")).thenReturn(response);

        mockMvc.perform(get("/api/documents/kb/{kbId}", "kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documents[0].kbId").value("kb-1"));
    }

    @Test
    void testGetDocumentsByKbId_Empty() throws Exception {
        GetDocumentsResponse response = GetDocumentsResponse.builder()
                .documents(new DocumentVO[]{}).build();
        when(documentFacadeService.getDocumentsByKbId("kb-999")).thenReturn(response);

        mockMvc.perform(get("/api/documents/kb/{kbId}", "kb-999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documents").isEmpty());
    }

    @Test
    void testCreateDocument_Success() throws Exception {
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setKbId("kb-1");
        request.setFilename("newfile.pdf");
        request.setFiletype("pdf");
        request.setSize(2048L);

        CreateDocumentResponse response = CreateDocumentResponse.builder()
                .documentId("doc-new").build();
        when(documentFacadeService.createDocument(any(CreateDocumentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentId").value("doc-new"));
    }

    @Test
    void testCreateDocument_InvalidRequest() throws Exception {
        when(documentFacadeService.createDocument(any(CreateDocumentRequest.class)))
                .thenThrow(new BizException("Knowledge base ID is required"));

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Knowledge base ID is required"));
    }

    @Test
    void testUploadDocument_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "upload.pdf", "application/pdf", "file content".getBytes());

        CreateDocumentResponse response = CreateDocumentResponse.builder()
                .documentId("doc-uploaded").build();
        when(documentFacadeService.uploadDocument(eq("kb-1"), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("kbId", "kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.documentId").value("doc-uploaded"));
    }

    @Test
    void testUploadDocument_NoFile() throws Exception {
        // Missing required 'file' parameter — caught by GlobalExceptionHandler
        mockMvc.perform(multipart("/api/documents/upload")
                        .param("kbId", "kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void testDeleteDocument_Success() throws Exception {
        doNothing().when(documentFacadeService).deleteDocument("doc-1");

        mockMvc.perform(delete("/api/documents/{documentId}", "doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testDeleteDocument_NotFound() throws Exception {
        doThrow(new BizException("Document not found"))
                .when(documentFacadeService).deleteDocument("non-existent");

        mockMvc.perform(delete("/api/documents/{documentId}", "non-existent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Document not found"));
    }

    @Test
    void testUpdateDocument_Success() throws Exception {
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setFilename("updated.pdf");
        doNothing().when(documentFacadeService).updateDocument(eq("doc-1"), any(UpdateDocumentRequest.class));

        mockMvc.perform(patch("/api/documents/{documentId}", "doc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
    }

    @Test
    void testUpdateDocument_NotFound() throws Exception {
        doThrow(new BizException("Document not found"))
                .when(documentFacadeService).updateDocument(eq("non-existent"), any(UpdateDocumentRequest.class));

        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setFilename("updated.pdf");

        mockMvc.perform(patch("/api/documents/{documentId}", "non-existent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Document not found"));
    }
}
