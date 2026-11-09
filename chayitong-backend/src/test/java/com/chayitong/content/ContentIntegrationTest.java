package com.chayitong.content;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ContentIntegrationTest extends AbstractIntegrationTest {

    @Test
    void publicQueriesHideDraftAndAdminCanReadIt() throws Exception {
        insertTea(1001, "tea-published", "发布茶", "published", 1);
        insertTea(1002, "tea-draft", "草稿茶", "draft", 1);
        mockMvc.perform(get("/api/teas").param("keyword", "发布茶").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value("tea-published"));
        mockMvc.perform(get("/api/teas/tea-draft")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/teas/tea-draft").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"));
        mockMvc.perform(get("/api/teas/tea_longjing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brew.teaGrams").value(3))
                .andExpect(jsonPath("$.data.brew.waterMl").value(150))
                .andExpect(jsonPath("$.data.brew.waterTempMin").value(80))
                .andExpect(jsonPath("$.data.brew.waterTempMax").value(85))
                .andExpect(jsonPath("$.data.brew.firstSteepSecondsMin").value(20))
                .andExpect(jsonPath("$.data.brew.firstSteepSecondsMax").value(30))
                .andExpect(jsonPath("$.data.relatedTeas", hasSize(3)));
    }

    @Test
    void allSevenBlocksAreOrderedAndUnknownFieldsAreRejected() throws Exception {
        String blocks = """
                [
                  {"type":"heading","text":"标题","level":2},
                  {"type":"paragraph","text":"段落"},
                  {"type":"image","src":"https://example.cos.ap-beijing.myqcloud.com/a.jpg","alt":"图片"},
                  {"type":"quote","text":"引用","source":"来源"},
                  {"type":"list","items":["一","二"],"ordered":true},
                  {"type":"steps","items":[{"title":"温杯","text":"热水温杯"}]},
                  {"type":"tip","text":"提示","tone":"info","title":"小贴士"}
                ]
                """;
        String request = articleRequest("seven-blocks", blocks);
        String created = mockMvc.perform(post("/api/admin/articles").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn().getResponse().getContentAsString();
        String articleId = new tools.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asText();
        mockMvc.perform(get("/api/admin/articles/{id}", articleId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentBlocks", hasSize(7)))
                .andExpect(jsonPath("$.data.contentBlocks[0].type").value("heading"))
                .andExpect(jsonPath("$.data.contentBlocks[6].type").value("tip"));
        mockMvc.perform(get("/api/admin/articles").param("status", "draft").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        String invalid = articleRequest("bad-block", "[{\"type\":\"paragraph\",\"text\":\"正文\",\"extra\":true}]");
        mockMvc.perform(post("/api/admin/articles").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        String unknown = articleRequest("unknown-block", "[{\"type\":\"html\",\"text\":\"正文\"}]");
        mockMvc.perform(post("/api/admin/articles").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isBadRequest());
    }

    @Test
    void articleLifecycleAndHomeFeaturedOrderAreStable() throws Exception {
        insertTea(1101, "tea-a", "甲茶", "published", 2);
        insertTea(1102, "tea-b", "乙茶", "published", 1);
        insertTea(1103, "tea-offline", "下架茶", "offline", 0);
        String request = articleRequest("life-article", "[{\"type\":\"paragraph\",\"text\":\"完整正文\"}]");
        String created = mockMvc.perform(post("/api/admin/articles").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn().getResponse().getContentAsString();
        String articleId = new tools.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asText();
        String published = mockMvc.perform(put("/api/admin/articles/{id}/publication", articleId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("published"))
                .andReturn().getResponse().getContentAsString();
        String firstPublishedAt = new tools.jackson.databind.ObjectMapper().readTree(published)
                .path("data").path("publishedAt").asText();
        String republished = mockMvc.perform(put("/api/admin/articles/{id}/publication", articleId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondPublishedAt = new tools.jackson.databind.ObjectMapper().readTree(republished)
                .path("data").path("publishedAt").asText();
        org.junit.jupiter.api.Assertions.assertEquals(java.time.Instant.parse(firstPublishedAt),
                java.time.Instant.parse(secondPublishedAt));
        mockMvc.perform(put("/api/admin/articles/{id}", articleId).with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/admin/articles/{id}/publication", articleId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("withdrawn"));
        mockMvc.perform(get("/api/articles/{id}", articleId)).andExpect(status().isNotFound());

        String config = """
                {"id":"production","version":2,"seasonalTheme":null,"banners":[],
                 "featuredTeaIds":["tea-b","tea-a","tea-offline"],"featuredArticleIds":[],
                 "announcement":"公告","features":{},"wikiTopics":[],"wikiHeroArticleIds":[],
                 "searchHotKeywords":["龙井"]}
                """;
        mockMvc.perform(put("/api/admin/app-config").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(config))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.featuredTeas", hasSize(2)))
                .andExpect(jsonPath("$.data.featuredTeas[*].id", contains("tea-b", "tea-a")));
    }

    private String articleRequest(String id, String blocks) {
        return """
                {"slug":"%s","title":"文章标题","summary":"文章摘要","category":"brewing",
                 "tags":[],"contentBlocks":%s,"relatedTeaIds":[],"readMinutes":3,
                 "viewCountDisplay":10,"featured":true,"sort":1}
                """.formatted(id, blocks);
    }

    private void insertTea(long id, String key, String name, String status, int sort) {
        jdbcTemplate.update("""
                INSERT INTO tea (id,business_key,slug,name,category,summary,brewing,status,sort)
                VALUES (?,?,?,?, 'green', ?, '{"teaGrams":3,"waterMl":150,"waterTempMin":80,"waterTempMax":85,
                "firstSteepSecondsMin":20,"firstSteepSecondsMax":30,"infusionsMin":3,"infusionsMax":4}',?,?)
                """, id, key, key, name, name + "摘要", status, sort);
    }
}
