package com.kama.jchatmind.controller;

import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.tools.ToolType;
import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.service.ToolFacadeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ToolController.class)
class ToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ToolFacadeService toolFacadeService;

    @Test
    void testGetTools_Success() throws Exception {
        Tool tool = new Tool() {
            @Override
            public String getName() { return "db_query"; }
            @Override
            public String getDescription() { return "Query the database"; }
            @Override
            public ToolType getType() { return ToolType.OPTIONAL; }
        };
        when(toolFacadeService.getOptionalTools()).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("db_query"))
                .andExpect(jsonPath("$.data[0].description").value("Query the database"));
    }

    @Test
    void testGetTools_Empty() throws Exception {
        when(toolFacadeService.getOptionalTools()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void testGetTools_ReturnsCorrectToolFormat() throws Exception {
        Tool tool1 = new Tool() {
            @Override
            public String getName() { return "file_search"; }
            @Override
            public String getDescription() { return "Search files"; }
            @Override
            public ToolType getType() { return ToolType.OPTIONAL; }
        };
        Tool tool2 = new Tool() {
            @Override
            public String getName() { return "email_send"; }
            @Override
            public String getDescription() { return "Send emails"; }
            @Override
            public ToolType getType() { return ToolType.OPTIONAL; }
        };
        when(toolFacadeService.getOptionalTools()).thenReturn(List.of(tool1, tool2));

        mockMvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("file_search"))
                .andExpect(jsonPath("$.data[1].name").value("email_send"))
                .andExpect(jsonPath("$.data[0].type").value("OPTIONAL"));
    }
}
