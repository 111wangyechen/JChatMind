package com.kama.jchatmind.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 从 JSON 文件加载测试用例
 */
public class TestCaseLoader {

    private static final String TESTCASES_DIR = "testcases/";
    private static final String[] TEST_FILES = {
            "single_turn_cases.json",
            "multi_turn_cases.json",
            "tool_calling_normal_cases.json",
            "tool_calling_exception_cases.json",
            "rag_retrieval_cases.json",
            "rag_quality_cases.json",
            "edge_cases.json"
    };

    private final ObjectMapper objectMapper;
    private List<TestCase> allTestCases;

    public TestCaseLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 加载所有测试用例
     */
    public List<TestCase> loadAll() {
        if (allTestCases == null) {
            allTestCases = new ArrayList<>();
            for (String file : TEST_FILES) {
                allTestCases.addAll(loadFromFile(file));
            }
        }
        return Collections.unmodifiableList(allTestCases);
    }

    /**
     * 按类别加载测试用例
     */
    public List<TestCase> loadByCategory(String category) {
        return loadAll().stream()
                .filter(tc -> category.equals(tc.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 按ID加载单个测试用例
     */
    public TestCase loadById(String id) {
        return loadAll().stream()
                .filter(tc -> id.equals(tc.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取总测试用例数量
     */
    public int getTotalCount() {
        return loadAll().size();
    }

    /**
     * 获取所有类别
     */
    public List<String> getCategories() {
        return loadAll().stream()
                .map(TestCase::getCategory)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 从JSON文件加载测试用例
     */
    private List<TestCase> loadFromFile(String fileName) {
        String resourcePath = TESTCASES_DIR + fileName;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Test case file not found: " + resourcePath);
            }
            JsonNode root = objectMapper.readTree(is);
            String category = root.has("category") ? root.get("category").asText() : "unknown";
            JsonNode testCasesNode = root.get("testCases");
            if (testCasesNode == null || !testCasesNode.isArray()) {
                return Collections.emptyList();
            }
            List<TestCase> testCases = objectMapper.convertValue(
                    testCasesNode,
                    new TypeReference<List<TestCase>>() {}
            );
            // 设置 category
            for (TestCase tc : testCases) {
                tc.setCategory(category);
            }
            return testCases;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test cases from: " + resourcePath, e);
        }
    }
}
