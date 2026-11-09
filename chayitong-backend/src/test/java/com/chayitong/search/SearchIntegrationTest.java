package com.chayitong.search;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chayitong.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class SearchIntegrationTest extends AbstractIntegrationTest {

    @Test
    void searchUsesPublicFilterSynonymsAndTwoCharacterFragments() throws Exception {
        insertTea(2001, "puer", "普洱熟茶", "dark", "published");
        insertTea(2002, "puer-draft", "普洱茶草稿", "dark", "draft");
        insertTea(2003, "tieguanyin", "铁观音", "oolong", "published");
        jdbcTemplate.update("""
                INSERT INTO article (id,business_key,slug,title,summary,category,content_blocks,status)
                VALUES (2004,'anxi-article','anxi','冲泡笔记','产地记录','brewing',
                '[{"type":"paragraph","text":"安溪铁观音冲泡"}]','published')
                """);
        mockMvc.perform(get("/api/search").param("keyword", "普洱茶"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teas.list[*].id", hasItem("puer")))
                .andExpect(jsonPath("$.data.teas.list[*].id", not(hasItem("puer-draft"))));
        mockMvc.perform(get("/api/search").param("keyword", "乌龙茶"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teas.list[*].id", hasItem("tieguanyin")));
        mockMvc.perform(get("/api/search").param("keyword", "安溪"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articles.list[*].id", hasItem("anxi-article")));
        mockMvc.perform(get("/api/search").param("keyword", "  "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/search").param("keyword", "乜乜乜乜"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.total").value(0))
                .andExpect(jsonPath("$.data.teas.total").value(0))
                .andExpect(jsonPath("$.data.articles.total").value(0));
    }

    @Test
    void productSearchIncludesSkuButExcludesOffShelfProducts() throws Exception {
        jdbcTemplate.update("INSERT INTO product_category (id,business_key,name,slug) VALUES (2101,'cat','分类','cat')");
        insertProduct(2102, "on-sale", "ON_SALE");
        insertProduct(2103, "off-shelf", "OFF_SHELF");
        jdbcTemplate.update("""
                INSERT INTO product_sku (id,business_key,product_id,sku_name,sku_code,price_fen,original_price_fen)
                VALUES (2104,'sku-on',2102,'100克','ZXQ-VISIBLE-100',19900,23900),
                       (2105,'sku-off',2103,'100克','WQR-HIDDEN-100',19900,23900)
                """);
        mockMvc.perform(get("/api/search").param("keyword", "ZXQ-VISIBLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.total").value(1))
                .andExpect(jsonPath("$.data.products.list[0].id").value("on-sale"))
                .andExpect(jsonPath("$.data.products.list[0].priceFen").value(19900));
        mockMvc.perform(get("/api/search").param("keyword", "WQR-HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.total").value(0));
    }

    private void insertTea(long id, String key, String name, String category, String status) {
        jdbcTemplate.update("""
                INSERT INTO tea (id,business_key,slug,name,category,summary,brewing,status)
                VALUES (?,?,?,?,?,?,'{"teaGrams":3,"waterMl":150,"waterTempMin":80,"waterTempMax":85}',?)
                """, id, key, key, name, category, name + "摘要", status);
    }

    private void insertProduct(long id, String key, String status) {
        jdbcTemplate.update("""
                INSERT INTO product (id,business_key,product_code,slug,category_id,name,status)
                VALUES (?,?,?,?,2101,?,?)
                """, id, key, key, key, key + "商品", status);
    }
}
