package com.chayitong.tea;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class TeaModels {
    private TeaModels() {
    }

    public record Origin(String province, String city, String area) {
    }

    public record Brewing(Integer teaGrams, Integer waterMl, String ratio, Integer waterTempMin,
            Integer waterTempMax, List<String> vessels, Integer firstSteepSecondsMin,
            Integer firstSteepSecondsMax, String nextSteepHint, Integer infusionsMin,
            Integer infusionsMax, String methodHint) {
    }

    public record Storage(String method, String shelfLifeHint) {
    }

    public record TeaRequest(String slug, String name, List<String> aliases, String type,
            Origin origin, String summary, List<String> craft, List<String> aromas,
            List<String> flavors, List<String> suitableScenes, Brewing brew, Storage storage,
            String notice, String cover, List<String> gallery, JsonNode contentBlocks,
            List<String> relatedProductIds, Boolean featured, Integer sort, String status) {
    }

    public record TeaSummary(String id, String slug, String name, List<String> aliases,
            String type, Origin origin, String summary, String cover, Boolean featured,
            Integer sort, String status, Instant createdAt, Instant updatedAt) {
    }

    public record TeaDetail(String id, String slug, String name, List<String> aliases,
            String type, Origin origin, String summary, List<String> craft, List<String> aromas,
            List<String> flavors, List<String> suitableScenes, Brewing brew, Storage storage,
            String notice, String cover, List<String> gallery, List<JsonNode> contentBlocks,
            List<String> relatedProductIds, Boolean featured, Integer sort, String status,
            Instant createdAt, Instant updatedAt, List<TeaSummary> relatedTeas) {
    }
}
