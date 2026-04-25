package com.kama.jchatmind.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.model.request.CreateKnowledgeBaseRequest;
import com.kama.jchatmind.model.request.UpdateKnowledgeBaseRequest;
import com.kama.jchatmind.model.response.CreateKnowledgeBaseResponse;
import com.kama.jchatmind.model.response.GetKnowledgeBasesResponse;
import com.kama.jchatmind.model.vo.KnowledgeBaseVO;
import com.kama.jchatmind.service.impl.KnowledgeBaseFacadeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseFacadeServiceTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private KnowledgeBaseConverter knowledgeBaseConverter;

    @InjectMocks
    private KnowledgeBaseFacadeServiceImpl knowledgeBaseFacadeService;

    private KnowledgeBase buildKB(String id, String name) {
        return KnowledgeBase.builder()
                .id(id)
                .name(name)
                .description("desc")
                .metadata(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private KnowledgeBaseVO buildKBVO(String id, String name) {
        return KnowledgeBaseVO.builder().id(id).name(name).description("desc").build();
    }

    @Test
    void testGetKnowledgeBases_Success() throws JsonProcessingException {
        // Given
        KnowledgeBase kb1 = buildKB("kb1", "KB1");
        KnowledgeBase kb2 = buildKB("kb2", "KB2");
        when(knowledgeBaseMapper.selectAll()).thenReturn(Arrays.asList(kb1, kb2));
        when(knowledgeBaseConverter.toVO(kb1)).thenReturn(buildKBVO("kb1", "KB1"));
        when(knowledgeBaseConverter.toVO(kb2)).thenReturn(buildKBVO("kb2", "KB2"));

        // When
        GetKnowledgeBasesResponse response = knowledgeBaseFacadeService.getKnowledgeBases();

        // Then
        assertNotNull(response);
        assertEquals(2, response.getKnowledgeBases().length);
        verify(knowledgeBaseMapper).selectAll();
    }

    @Test
    void testGetKnowledgeBases_Empty() {
        // Given
        when(knowledgeBaseMapper.selectAll()).thenReturn(Collections.emptyList());

        // When
        GetKnowledgeBasesResponse response = knowledgeBaseFacadeService.getKnowledgeBases();

        // Then
        assertNotNull(response);
        assertEquals(0, response.getKnowledgeBases().length);
    }

    @Test
    void testCreateKnowledgeBase_Success() throws JsonProcessingException {
        // Given
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("NewKB");
        request.setDescription("A new knowledge base");

        KnowledgeBaseDTO dto = KnowledgeBaseDTO.builder().name("NewKB").description("A new knowledge base").build();
        KnowledgeBase entity = buildKB(null, "NewKB");

        when(knowledgeBaseConverter.toDTO(request)).thenReturn(dto);
        when(knowledgeBaseConverter.toEntity(dto)).thenReturn(entity);
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase kb = invocation.getArgument(0);
            kb.setId("gen-kb-id");
            return 1;
        });

        // When
        CreateKnowledgeBaseResponse response = knowledgeBaseFacadeService.createKnowledgeBase(request);

        // Then
        assertNotNull(response);
        assertEquals("gen-kb-id", response.getKnowledgeBaseId());
    }

    @Test
    void testDeleteKnowledgeBase_Success() {
        // Given
        KnowledgeBase kb = buildKB("kb1", "KB1");
        when(knowledgeBaseMapper.selectById("kb1")).thenReturn(kb);
        when(knowledgeBaseMapper.deleteById("kb1")).thenReturn(1);

        // When
        knowledgeBaseFacadeService.deleteKnowledgeBase("kb1");

        // Then
        verify(knowledgeBaseMapper).deleteById("kb1");
    }

    @Test
    void testUpdateKnowledgeBase_Success() throws JsonProcessingException {
        // Given
        String kbId = "kb1";
        KnowledgeBase existing = buildKB(kbId, "OldName");
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("NewName");

        KnowledgeBaseDTO dto = KnowledgeBaseDTO.builder().id(kbId).name("OldName").description("desc").build();
        KnowledgeBase updatedEntity = buildKB(null, "NewName");

        when(knowledgeBaseMapper.selectById(kbId)).thenReturn(existing);
        when(knowledgeBaseConverter.toDTO(existing)).thenReturn(dto);
        doNothing().when(knowledgeBaseConverter).updateDTOFromRequest(dto, request);
        when(knowledgeBaseConverter.toEntity(dto)).thenReturn(updatedEntity);
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);

        // When
        knowledgeBaseFacadeService.updateKnowledgeBase(kbId, request);

        // Then
        verify(knowledgeBaseMapper).updateById(any(KnowledgeBase.class));
    }

    @Test
    void testUpdateKnowledgeBase_NotFound() {
        // Given
        when(knowledgeBaseMapper.selectById("nonexistent")).thenReturn(null);
        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("NewName");

        // When & Then
        BizException ex = assertThrows(BizException.class,
                () -> knowledgeBaseFacadeService.updateKnowledgeBase("nonexistent", request));
        assertTrue(ex.getMessage().contains("知识库不存在"));
    }
}
