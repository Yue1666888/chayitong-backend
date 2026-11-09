package com.chayitong.recommendation;

import com.chayitong.common.api.ErrorCode;
import com.chayitong.common.exceptions.BusinessException;
import com.chayitong.content.JsonSupport;
import com.chayitong.recommendation.RecommendationModels.Score;
import com.chayitong.tea.TeaEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class RecommendationScorer {

    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("taste:fresh", "清鲜风味更贴合你的口味"),
            Map.entry("taste:sweet", "甜香与顺滑感是这款茶的长处"),
            Map.entry("taste:soft", "柔和茶汤适合慢慢感受"),
            Map.entry("taste:rich", "醇厚层次回应你偏好的浓郁感"),
            Map.entry("aroma:floral", "它的花香清透，正是你偏好的那一类"),
            Map.entry("aroma:fruity", "果香与蜜香是这款茶的主调"),
            Map.entry("aroma:roasted", "焙火与焦糖气息回应你选的香型"),
            Map.entry("aroma:aged", "陈香与木香是它最耐读的一面"),
            Map.entry("texture:crisp", "入口干净利落，收口快"),
            Map.entry("texture:smooth", "汤感顺滑，几乎没有棱角"),
            Map.entry("texture:thick", "茶汤有骨架，稠度是它的长处"),
            Map.entry("texture:brisk", "有轻微的收敛感，随后转成回甘"),
            Map.entry("aftertaste:clean", "喝完口腔清爽，不留负担"),
            Map.entry("aftertaste:sweet", "回甘来得清楚，甜味会慢慢返上来"),
            Map.entry("aftertaste:deep", "喉韵绵长，咽下之后仍有留存"),
            Map.entry("aftertaste:warm", "香气在口腔里停得久"),
            Map.entry("strength:light", "轻盈入口，适合从淡茶开始"),
            Map.entry("strength:medium", "滋味有分寸，适合日常稳定冲泡"),
            Map.entry("strength:rich", "耐泡而有层次，适合慢慢品饮"),
            Map.entry("time:morning", "上午饮用能保留清爽的起手感"),
            Map.entry("time:afternoon", "午后适合展开花香与回甘"),
            Map.entry("time:evening", "晚间更适合温暖醇厚的茶席"),
            Map.entry("scene:work", "放在工作间隙也能轻松冲开"),
            Map.entry("scene:ritual", "适合把冲泡过程变成一段仪式"),
            Map.entry("scene:meal", "醇厚茶汤适合餐后慢饮"),
            Map.entry("scene:share", "香气表达清楚，适合几个人分着喝"),
            Map.entry("effort:quick", "随手一泡就能喝，不挑手法"),
            Map.entry("effort:normal", "按部就班泡就好，容错度高"),
            Map.entry("effort:slow", "值得为它多花几分钟"),
            Map.entry("vessel:glass", "用玻璃杯就能泡出它的样子"),
            Map.entry("vessel:gaiwan", "盖碗是它最稳妥的器具"),
            Map.entry("vessel:zisha", "紫砂聚香保温，正合它的性格"),
            Map.entry("vessel:tumbler", "飘逸杯或马克杯也能泡得不错"),
            Map.entry("temp:hot", "需要足温才能把香气顶出来"),
            Map.entry("temp:warm", "对水温的宽容度比较高"),
            Map.entry("temp:cooled", "放凉之后香气反而更清楚"),
            Map.entry("temp:cold", "冷泡清甜，几乎不会苦涩"),
            Map.entry("season:spring", "属于春天的那一口鲜"),
            Map.entry("season:summer", "夏天喝起来清爽不压口"),
            Map.entry("season:autumn", "秋天的香气层次最经得起细闻"),
            Map.entry("season:winter", "冬天适合一杯温厚的茶"),
            Map.entry("pairing:nothing", "单独喝就很完整，不必配点心"),
            Map.entry("pairing:snack", "配清淡茶点不会互相抢味"),
            Map.entry("pairing:heavy", "汤感够厚，压得住重味的饭菜"),
            Map.entry("explore:classic", "是公认的经典款，先喝它不容易走弯路"),
            Map.entry("explore:niche", "属于相对小众的一路，值得专门认识"),
            Map.entry("experience:new", "冲泡门槛友好，适合作为入门茶"),
            Map.entry("experience:familiar", "更能承接你已有的冲泡经验"),
            Map.entry("focus:bud", "它的看点正在于满披白毫的芽头"),
            Map.entry("focus:leaf", "叶形舒展，冲泡时看得见变化"),
            Map.entry("focus:cake", "紧压成形，撬茶本身就是一道仪式"),
            Map.entry("focus:fire", "焙火功夫是它最值得琢磨的地方"));

    private static final Map<String, Map<String, List<String>>> TRAITS = Map.of(
            "aroma", Map.of(
                    "floral", List.of("花香", "兰香", "栀子", "桂花", "清香"),
                    "fruity", List.of("果香", "蜜香", "甜香", "蜜"),
                    "roasted", List.of("焙火", "焦糖", "炭焙", "火香"),
                    "aged", List.of("陈香", "木香", "木质", "陈韵")),
            "texture", Map.of(
                    "crisp", List.of("鲜爽", "清爽", "甘爽", "清鲜"),
                    "smooth", List.of("顺滑", "柔和", "醇和"),
                    "thick", List.of("醇厚", "稠", "厚重", "饱满"),
                    "brisk", List.of("回甘", "收敛")),
            "aftertaste", Map.of(
                    "clean", List.of("清爽", "干净", "清鲜"),
                    "sweet", List.of("回甘", "甘爽", "清甜", "甜"),
                    "deep", List.of("喉韵", "绵长", "韵味"),
                    "warm", List.of("留香", "醇厚", "香")),
            "season", Map.of(
                    "spring", List.of("春"), "summer", List.of("夏"),
                    "autumn", List.of("秋"), "winter", List.of("冬")),
            "scene", Map.of(
                    "work", List.of("办公", "上午", "专注"),
                    "ritual", List.of("独饮", "清饮", "品鉴", "静"),
                    "meal", List.of("餐后"),
                    "share", List.of("待客", "商务", "分享")),
            "strength", Map.of(
                    "light", List.of("清鲜", "清爽", "轻盈", "轻柔"),
                    "medium", List.of("醇和", "均衡", "适中"),
                    "rich", List.of("醇厚", "浓郁", "饱满")));

    private final JsonSupport jsonSupport;

    public RecommendationScorer(JsonSupport jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public Score score(TeaEntity tea, Map<String, String> answers, String scoringRules) {
        JsonNode rules = jsonSupport.parse(scoringRules).path("rules");
        if (!rules.isArray() || rules.size() != 15) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "评分规则不完整");
        }
        int score = 0;
        List<String> reasons = new ArrayList<>();
        List<JsonNode> orderedRules = new ArrayList<>();
        rules.forEach(orderedRules::add);
        orderedRules.sort(java.util.Comparator.comparingInt(rule ->
                QuizService.QUESTION_KEYS.indexOf(rule.path("key").asText())));
        for (JsonNode rule : orderedRules) {
            String key = rule.path("key").asText();
            JsonNode types = rule.path("mapping").path(answers.get(key));
            if (!types.isArray()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "评分选项映射不完整");
            }
            if (contains(types, tea.getCategory())) {
                score += rule.path("weight").intValue();
                String reason = REASONS.get(key + ":" + answers.get(key));
                if (reason != null) {
                    reasons.add(reason);
                }
            }
        }
        if (reasons.isEmpty()) {
            List<String> aromas = jsonSupport.stringArray(tea.getAromas());
            String aroma = aromas.isEmpty() ? "本真茶香" : String.join("、", aromas.subList(0, Math.min(2, aromas.size())));
            reasons.add(aroma + "，是值得认识的风味线索");
        }
        if (reasons.size() < 3) {
            reasons.add("产地为" + (tea.getOriginProvince() == null ? "传统茶区" : tea.getOriginProvince()));
        }
        if (reasons.size() < 3) {
            JsonNode brewing = jsonSupport.parse(tea.getBrewing());
            JsonNode temperature = brewing.path("temperatureC");
            int min = brewing.has("waterTempMin") ? brewing.path("waterTempMin").asInt()
                    : temperature.path("min").asInt();
            int max = brewing.has("waterTempMax") ? brewing.path("waterTempMax").asInt()
                    : temperature.path("max").asInt();
            reasons.add("建议从" + min + (min == max ? "" : "至" + max) + "℃开始冲泡");
        }
        int traitScore = traitScore(tea, answers);
        int matchPercent = Math.min(98, 62 + Math.round(score / 39.0f * 36));
        return new Score(score, traitScore, matchPercent, reasons.stream().limit(3).toList());
    }

    private int traitScore(TeaEntity tea, Map<String, String> answers) {
        String text = String.join(" ", jsonSupport.stringArray(tea.getAromas()) + " "
                + jsonSupport.stringArray(tea.getFlavors()) + " " + jsonSupport.stringArray(tea.getScenes()));
        int score = 0;
        for (String dimension : TRAITS.keySet()) {
            List<String> keywords = TRAITS.get(dimension).get(answers.get(dimension));
            if (keywords != null && keywords.stream().anyMatch(text::contains)) {
                score++;
            }
        }
        return score;
    }

    private boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (item.isTextual() && item.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
