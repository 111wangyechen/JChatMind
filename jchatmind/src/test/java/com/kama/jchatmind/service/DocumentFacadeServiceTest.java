package com.kama.jchatmind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.model.request.CreateDocumentRequest;
import com.kama.jchatmind.model.request.UpdateDocumentRequest;
import com.kama.jchatmind.model.response.CreateDocumentResponse;
import com.kama.jchatmind.model.response.GetDocumentsResponse;
import com.kama.jchatmind.model.vo.DocumentVO;
import com.kama.jchatmind.service.impl.DocumentFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentFacadeServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentConverter documentConverter;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private MarkdownParserService markdownParserService;

    @Mock
    private RagService ragService;

    @Mock
    private ChunkBgeM3Mapper chunkBgeM3Mapper;

    @InjectMocks
    private DocumentFacadeServiceImpl documentFacadeService;

    private Document buildDocument(String id, String kbId, String filename) {
        return Document.builder()
                .id(id)
                .kbId(kbId)
                .filename(filename)
                .filetype("txt")
                .size(1024L)
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private DocumentVO buildDocumentVO(String id, String kbId, String filename) {
        return DocumentVO.builder()
                .id(id)
                .kbId(kbId)
                .filename(filename)
                .filetype("txt")
                .size(1024L)
                .build();
    }

    @Test
    void testGetDocuments_Success() throws JsonProcessingException {
        // Given
        Document doc1 = buildDocument("d1", "kb1", "file1.txt");
        Document doc2 = buildDocument("d2", "kb1", "file2.txt");
        when(documentMapper.selectAll()).thenReturn(Arrays.asList(doc1, doc2));
        when(documentConverter.toVO(doc1)).thenReturn(buildDocumentVO("d1", "kb1", "file1.txt"));
        when(documentConverter.toVO(doc2)).thenReturn(buildDocumentVO("d2", "kb1", "file2.txt"));

        // When
        GetDocumentsResponse response = documentFacadeService.getDocuments();

        // Then
        assertNotNull(response);
        assertEquals(2, response.getDocuments().length);
        verify(documentMapper).selectAll();
    }

    @Test
    void testGetDocumentsByKbId_Success() throws JsonProcessingException {
        // Given
        String kbId = "kb1";
        Document doc1 = buildDocument("d1", kbId, "file1.txt");
        when(documentMapper.selectByKbId(kbId)).thenReturn(List.of(doc1));
        when(documentConverter.toVO(doc1)).thenReturn(buildDocumentVO("d1", kbId, "file1.txt"));

        // When
        GetDocumentsResponse response = documentFacadeService.getDocumentsByKbId(kbId);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getDocuments().length);
        assertEquals("file1.txt", response.getDocuments()[0].getFilename());
    }

    @Test
    void testCreateDocument_Success() throws JsonProcessingException {
        // Given
        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setKbId("kb1");
        request.setFilename("test.txt");
        request.setFiletype("txt");
        request.setSize(512L);

        DocumentDTO dto = DocumentDTO.builder()
                .kbId("kb1").filename("test.txt").filetype("txt").size(512L).build();
        Document entity = buildDocument(null, "kb1", "test.txt");

        when(documentConverter.toDTO(request)).thenReturn(dto);
        when(documentConverter.toEntity(dto)).thenReturn(entity);
        when(documentMapper.insert(any(Document.class))).thenAnswer(invocation -> {
            Document d = invocation.getArgument(0);
            d.setId("gen-doc-id");
            return 1;
        });

        // When
        CreateDocumentResponse response = documentFacadeService.createDocument(request);

        // Then
        assertNotNull(response);
        assertEquals("gen-doc-id", response.getDocumentId());
    }

    @Test
    void testUploadDocument_Success() throws IOException {
        // Given
        String kbId = "kb1";
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.txt");
        when(file.getSize()).thenReturn(1024L);

        Document entity = buildDocument(null, kbId, "test.txt");
        when(documentConverter.toEntity(any(DocumentDTO.class))).thenReturn(entity);
        when(documentMapper.insert(any(Document.class))).thenAnswer(invocation -> {
            Document d = invocation.getArgument(0);
            d.setId("gen-doc-id");
            return 1;
        });
        when(documentStorageService.saveFile(eq(kbId), eq("gen-doc-id"), eq(file))).thenReturn("/path/to/file");
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);

        // When
        CreateDocumentResponse response = documentFacadeService.uploadDocument(kbId, file);

        // Then
        assertNotNull(response);
        assertEquals("gen-doc-id", response.getDocumentId());
        verify(documentStorageService).saveFile(eq(kbId), eq("gen-doc-id"), eq(file));
    }

    @Test
    void testDeleteDocument_Success() throws JsonProcessingException {
        // Given
        Document doc = buildDocument("d1", "kb1", "file.txt");
        DocumentDTO dto = DocumentDTO.builder().id("d1").kbId("kb1").filename("file.txt").build();

        when(documentMapper.selectById("d1")).thenReturn(doc);
        when(documentConverter.toDTO(doc)).thenReturn(dto);
        when(documentMapper.deleteById("d1")).thenReturn(1);

        // When
        documentFacadeService.deleteDocument("d1");

        // Then
        verify(documentMapper).deleteById("d1");
    }

    @Test
    void testUpdateDocument_Success() throws JsonProcessingException {
        // Given
        String docId = "d1";
        Document existing = buildDocument(docId, "kb1", "old.txt");
        UpdateDocumentRequest request = new UpdateDocumentRequest();
        request.setFilename("new.txt");

        DocumentDTO dto = DocumentDTO.builder()
                .id(docId).kbId("kb1").filename("old.txt").filetype("txt").size(1024L).build();
        Document updatedEntity = buildDocument(null, "kb1", "new.txt");

        when(documentMapper.selectById(docId)).thenReturn(existing);
        when(documentConverter.toDTO(existing)).thenReturn(dto);
        doNothing().when(documentConverter).updateDTOFromRequest(dto, request);
        when(documentConverter.toEntity(dto)).thenReturn(updatedEntity);
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);

        // When
        documentFacadeService.updateDocument(docId, request);

        // Then
        verify(documentMapper).updateById(any(Document.class));
    }
}
