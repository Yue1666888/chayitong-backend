package com.chayitong.article;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class ArticleRelatedTeasIntegrationTest extends AbstractIntegrationTest {

    @Test
    void relatedTeasUseGlobalSortAndIdOrderBeforeTakingThree() throws Exception {
        insertTea(4004, "related-d", 50);
        insertTea(4002, "related-b", 10);
        insertTea(4001, "related-a", 10);
        insertTea(4003, "related-c", 10);
        jdbcTemplate.update("""
                INSERT INTO article (id,business_key,slug,title,summary,category,content_blocks,related_tea_keys,status)
                VALUES (4101,'article-related-order','article-related-order','关联顺序','验证稳定排序','tea_basics',
                '[{"type":"paragraph","text":"正文"}]',
                '["related-d","related-c","related-a","related-b"]','published')
                """);

        mockMvc.perform(get("/api/articles/article-related-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relatedTeaIds", contains(
                        "related-d", "related-c", "related-a", "related-b")))
                .andExpect(jsonPath("$.data.relatedTeas", hasSize(3)))
                .andExpect(jsonPath("$.data.relatedTeas[*].id", contains(
                        "related-a", "related-b", "related-c")));
    }

    private void insertTea(long id, String businessKey, int sort) {
        jdbcTemplate.update("""
                INSERT INTO tea (id,business_key,slug,name,category,summary,brewing,status,sort)
                VALUES (?,?,?,?,'green',?,'{"teaGrams":3,"waterMl":150,"waterTempMin":80,"waterTempMax":85}',
                'published',?)
                """, id, businessKey, businessKey, businessKey, businessKey + "摘要", sort);
    }
}
