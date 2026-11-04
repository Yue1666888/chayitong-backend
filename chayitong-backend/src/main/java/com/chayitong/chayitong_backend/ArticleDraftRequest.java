package com.chayitong.chayitong_backend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record ArticleDraftRequest(
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 100, message = "文章标题长度不能超过 100")
    String title,

    @NotBlank(message = "文章分类不能为空")
    @Size(max = 32, message = "文章分类长度不能超过 32")
    String category,

    @Size(max = 500, message = "文章摘要长度不能超过 500")
    String summary,

    @Size(max = 1024, message = "封面地址长度不能超过 1024")
    String cover,

    // 保留请求中的原始 JSON 值类型，交给正文读取器严格转换。
    @NotNull(message = "请提供正文数组")
    JsonNode contentBlocks) {
}
