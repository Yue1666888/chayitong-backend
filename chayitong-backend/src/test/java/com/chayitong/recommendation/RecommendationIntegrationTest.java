package com.chayitong.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class RecommendationIntegrationTest extends AbstractIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private com.chayitong.tea.TeaMapper teaMapper;

    private static final String FIXED_ANSWERS = """
            {"taste":"fresh","aroma":"floral","texture":"crisp","aftertaste":"clean",
             "strength":"light","time":"morning","scene":"work","effort":"quick","vessel":"glass",
             "temp":"cold","season":"spring","pairing":"snack","explore":"classic",
             "experience":"new","focus":"leaf"}
            """;

    @Test
    void quizHasFifteenQuestionsWithoutWeights() throws Exception {
        mockMvc.perform(get("/api/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizVersion").value("v1"))
                .andExpect(jsonPath("$.data.questions.length()").value(15))
                .andExpect(jsonPath("$.data.questions[0].weight").doesNotExist());
    }

    @Test
    void fixedAnswersProduceExpectedTopThreeAndPersistedResult() throws Exception {
        String body = "{\"quizVersion\":\"v1\",\"answers\":" + FIXED_ANSWERS + "}";
        String response = mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                .andExpect(jsonPath("$.data.recommendations[0].tea.name").value("黄山毛峰"))
                .andExpect(jsonPath("$.data.recommendations[0].score").value(39))
                .andExpect(jsonPath("$.data.recommendations[0].traitScore").value(6))
                .andExpect(jsonPath("$.data.recommendations[0].matchPercent").value(98))
                .andExpect(jsonPath("$.data.recommendations[1].tea.id").value("tea_biluochun"))
                .andExpect(jsonPath("$.data.recommendations[1].traitScore").value(5))
                .andExpect(jsonPath("$.data.recommendations[2].tea.name").value("西湖龙井"))
                .andExpect(jsonPath("$.data.recommendations[2].traitScore").value(3))
                .andReturn().getResponse().getContentAsString();
        String resultId = new tools.jackson.databind.ObjectMapper().readTree(response)
                .path("data").path("resultId").asText();
        assertEquals(22, resultId.length());
        mockMvc.perform(get("/api/recommendations/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(3))
                .andExpect(jsonPath("$.data.recommendations[0].tea.id").value("tea_huangshan_maofeng"));
        com.chayitong.tea.TeaEntity offline = teaMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chayitong.tea.TeaEntity>()
                        .eq(com.chayitong.tea.TeaEntity::getBusinessKey, "tea_huangshan_maofeng"));
        offline.setStatus("offline");
        teaMapper.updateById(offline);
        mockMvc.perform(get("/api/recommendations/{id}", resultId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.length()").value(2))
                .andExpect(jsonPath("$.data.recommendations[0].score").value(39));
    }

    @Test
    void incompleteUnknownOrWrongVersionAnswersAreRejected() throws Exception {
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizVersion\":\"v1\",\"answers\":{}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizVersion\":\"missing\",\"answers\":" + FIXED_ANSWERS + "}"))
                .andExpect(status().isBadRequest());
        String invalid = FIXED_ANSWERS.replace("\"fresh\"", "\"unknown\"");
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizVersion\":\"v1\",\"answers\":" + invalid + "}"))
                .andExpect(status().isBadRequest());
    }

    private void insertTea(long id, String key, String name, int sort, String aromas, String flavors, String scenes) {
        jdbcTemplate.update("""
                INSERT INTO tea (id,business_key,slug,name,category,summary,aromas,flavors,scenes,brewing,status,sort)
                VALUES (?,?,?,?,'green',?,?,?,?,?, 'published',?)
                """, id, key, key, name, name + "摘要", aromas, flavors, scenes,
                "{\"teaGrams\":3,\"waterMl\":150,\"waterTempMin\":80,\"waterTempMax\":85,"
                        + "\"firstSteepSecondsMin\":20,\"firstSteepSecondsMax\":30,"
                        + "\"infusionsMin\":3,\"infusionsMax\":4}", sort);
    }
}
