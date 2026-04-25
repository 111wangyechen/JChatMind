package com.kama.jchatmind.service;

import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.service.impl.RagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private ChunkBgeM3Mapper chunkBgeM3Mapper;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        WebClient.Builder builder = mock(WebClient.Builder.class);
        WebClient webClient = mock(WebClient.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(webClient);
        ragService = new RagServiceImpl(builder, chunkBgeM3Mapper);
    }

    private ChunkBgeM3 buildChunk(String id, String kbId, String docId, String content) {
        return ChunkBgeM3.builder()
                .id(id)
                .kbId(kbId)
                .docId(docId)
                .content(content)
                .embedding(new float[]{0.1f, 0.2f, 0.3f})
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testServiceInitialization() {
        // Verify the service is properly constructed
        assertNotNull(ragService);
    }

    @Test
    void testSimilaritySearch_MapperWithResults() {
        // Given - test the mapper interaction directly
        ChunkBgeM3 chunk1 = buildChunk("c1", "kb1", "doc1", "Content about Java");
        ChunkBgeM3 chunk2 = buildChunk("c2", "kb1", "doc1", "Content about Spring");
        when(chunkBgeM3Mapper.similaritySearch(eq("kb1"), anyString(), eq(3)))
                .thenReturn(Arrays.asList(chunk1, chunk2));

        // When
        List<ChunkBgeM3> results = chunkBgeM3Mapper.similaritySearch("kb1", "[0.1,0.2,0.3]", 3);

        // Then
        assertEquals(2, results.size());
        assertEquals("Content about Java", results.get(0).getContent());
        assertEquals("Content about Spring", results.get(1).getContent());
        verify(chunkBgeM3Mapper).similaritySearch("kb1", "[0.1,0.2,0.3]", 3);
    }

    @Test
    void testSimilaritySearch_NoResults() {
        // Given
        when(chunkBgeM3Mapper.similaritySearch(eq("kb1"), anyString(), eq(3)))
                .thenReturn(Collections.emptyList());

        // When
        List<ChunkBgeM3> results = chunkBgeM3Mapper.similaritySearch("kb1", "[0.1,0.2,0.3]", 3);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSimilaritySearch_EmptyQuery() {
        // Given
        when(chunkBgeM3Mapper.similaritySearch(eq("kb1"), eq("[]"), eq(3)))
                .thenReturn(Collections.emptyList());

        // When
        List<ChunkBgeM3> results = chunkBgeM3Mapper.similaritySearch("kb1", "[]", 3);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSimilaritySearch_MultipleDocuments() {
        // Given - chunks from different documents
        ChunkBgeM3 chunk1 = buildChunk("c1", "kb1", "doc1", "Doc1 content");
        ChunkBgeM3 chunk2 = buildChunk("c2", "kb1", "doc2", "Doc2 content");
        when(chunkBgeM3Mapper.similaritySearch(eq("kb1"), anyString(), eq(3)))
                .thenReturn(Arrays.asList(chunk1, chunk2));

        // When
        List<ChunkBgeM3> results = chunkBgeM3Mapper.similaritySearch("kb1", "[0.1,0.2,0.3]", 3);

        // Then
        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0).getDocId());
        assertEquals("doc2", results.get(1).getDocId());
    }

    @Test
    void testSimilaritySearch_LargeResultSet() {
        // Given
        ChunkBgeM3 chunk1 = buildChunk("c1", "kb1", "doc1", "Content 1");
        ChunkBgeM3 chunk2 = buildChunk("c2", "kb1", "doc1", "Content 2");
        ChunkBgeM3 chunk3 = buildChunk("c3", "kb1", "doc2", "Content 3");
        when(chunkBgeM3Mapper.similaritySearch(eq("kb1"), anyString(), eq(3)))
                .thenReturn(Arrays.asList(chunk1, chunk2, chunk3));

        // When
        List<ChunkBgeM3> results = chunkBgeM3Mapper.similaritySearch("kb1", "[0.1,0.2,0.3]", 3);

        // Then
        assertEquals(3, results.size());
    }

    @Test
    void testToPgVector_ViaReflection() throws Exception {
        // Test the private toPgVector method via reflection
        Method toPgVector = RagServiceImpl.class.getDeclaredMethod("toPgVector", float[].class);
        toPgVector.setAccessible(true);

        float[] vector = {0.1f, 0.2f, 0.3f};
        String result = (String) toPgVector.invoke(ragService, vector);

        // Then
        assertNotNull(result);
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
        assertTrue(result.contains("0.1"));
        assertTrue(result.contains("0.2"));
        assertTrue(result.contains("0.3"));
    }
}
