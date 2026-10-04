package com.journalflow.llm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock Provider：不依赖网络与 API Key，返回与输入耦合的确定性 JSON，
 * 保证答辩断网时可完整演示三场景；同时作为一致性样例批跑的基线实现。
 */
@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public ChatResult chat(String scene, String systemPrompt, String userPrompt) {
        String content = switch (scene) {
            case "EXTRACT" -> extract(userPrompt);
            case "SUMMARY" -> summary(userPrompt);
            case "EXPLAIN" -> explain(userPrompt);
            default -> "{\"error\":\"unknown scene\"}";
        };
        return new ChatResult(content, 60);
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public String model() {
        return "journalflow-mock-v1";
    }

    // ---------------- 场景 A：元数据抽取 ----------------

    private String extract(String input) {
        String title = between(input, "【标题】", "【摘要】");
        String abstractText = between(input, "【摘要】", "【关键词】");
        String keywordLine = between(input, "【关键词】", "【正文】");
        String body = between(input, "【正文】", "【参考文献】");
        if (body.isEmpty()) {
            body = tail(input, "【正文】");
        }
        String refs = tail(input, "【参考文献】");
        if (refs.isBlank()) {
            Matcher heading = Pattern.compile("(?im)^\\s*(?:参考文献|references)\\s*[:：]?\\s*$").matcher(body);
            if (heading.find()) refs = body.substring(heading.end()).trim();
        }

        String lang = cjkRatio(title + abstractText + body) > 0.3 ? "zh" : "en";
        int wordCount = (title + abstractText + body).replaceAll("\\s", "").length();
        int refCount = countReferences(refs);

        List<String> keywords = new ArrayList<>();
        for (String k : keywordLine.split("[;；,，]")) {
            String t = k.replaceAll("(作者|关键词|等)$", "").trim();
            if (!t.isEmpty() && t.length() <= 20 && !keywords.contains(t)) {
                keywords.add(t);
            }
        }
        if (keywords.isEmpty()) {
            for (String cand : List.of("大模型", "深度学习", "自然语言处理", "软件工程", "信息安全")) {
                if ((title + abstractText + body).contains(cand)) {
                    keywords.add(cand);
                    if (keywords.size() >= 3) break;
                }
            }
        }
        while (keywords.size() < 3 && !keywords.isEmpty()) {
            keywords.add("学术写作");
        }

        List<String> issues = new ArrayList<>();
        if (abstractText.isBlank()) {
            issues.add("缺少摘要");
        }
        if (keywords.size() > 8) {
            issues.add("关键词数量超过 8 个");
        }
        if (input.contains("【正文】（略）")) {
            issues.add("正文内容缺失，无法核对");
        }

        String category = "人工智能";
        for (String c : List.of("人工智能", "软件工程", "网络与信息安全", "数学", "物理学", "生物学", "经济学")) {
            if ((title + abstractText + body).contains(c) || containsAny((title + abstractText + body), synonyms(c))) {
                category = c;
                break;
            }
        }

        String escapedAbstract = jsonEscape(abstractText.isBlank() ? "" : abbreviate(abstractText, 200));
        String escapedTitle = jsonEscape(title.isBlank() ? "未识别标题" : title);
        String kwJson = keywords.stream().map(k -> "\"" + jsonEscape(k) + "\"").reduce((a, b) -> a + "," + b).orElse("");
        String issuesJson = issues.stream().map(i -> "\"" + jsonEscape(i) + "\"").reduce((a, b) -> a + "," + b).orElse("");

        return "{\"title\":\"%s\",\"abstract\":\"%s\",\"keywords\":[%s],\"primary_category\":\"%s\","
                .formatted(escapedTitle, escapedAbstract, kwJson, category)
                + "\"language\":\"%s\",\"word_count_est\":%d,\"reference_count_est\":%d,"
                .formatted(lang, wordCount, refCount)
                + "\"has_ethics_statement\":%b,\"possible_issues\":[%s]}"
                .formatted(input.contains("伦理") || input.contains("知情同意"), issuesJson);
    }

    private static final Map<String, List<String>> SYNONYMS = Map.of(
            "人工智能", List.of("机器学习", "神经网络", "大语言模型", "自然语言处理"),
            "软件工程", List.of("微服务", "代码缺陷", "测试", "架构"),
            "网络与信息安全", List.of("供应链安全", "隐私", "联邦学习", "泄露"),
            "经济学", List.of("市场", "金融"));

    private boolean containsAny(String text, List<String> candidates) {
        return candidates.stream().anyMatch(text::contains);
    }

    private List<String> synonyms(String category) {
        return SYNONYMS.getOrDefault(category, List.of());
    }

    // ---------------- 场景 B：意见汇总 ----------------

    private String summary(String input) {
        List<String> blocks = new ArrayList<>();
        Matcher m = Pattern.compile("意见\\d+：(.*?)(?=意见\\d+：|$)", Pattern.DOTALL).matcher(input);
        while (m.find()) {
            blocks.add(m.group(1));
        }
        if (blocks.isEmpty()) {
            blocks.add(input);
        }
        List<String> recs = new ArrayList<>();
        List<String> major = new ArrayList<>();
        List<String> minor = new ArrayList<>();
        for (String b : blocks) {
            Matcher rm = Pattern.compile("建议等级：(ACCEPT|MINOR|MAJOR|REJECT)").matcher(b);
            if (rm.find()) {
                recs.add(rm.group(1));
            }
            for (String line : b.split("\\n")) {
                String t = line.replaceAll("^[-\\d）).、\\s]*给作者[:：]?\\s*", "").trim();
                if (t.startsWith("1)") || t.startsWith("1.") || t.contains("缺少") || t.contains("不足")
                        || t.contains("建议补充") || t.contains("不统一") || t.contains("不一致")) {
                    (major.size() < 4 ? major : minor).add(abbreviate(t, 60));
                } else if (!t.isEmpty() && t.length() > 8 && minor.size() < 3) {
                    minor.add(abbreviate(t, 60));
                }
            }
        }
        String consensus = recs.contains("REJECT") ? "reject" : recs.contains("MAJOR") ? "major"
                : recs.contains("MINOR") ? "minor" : recs.contains("ACCEPT") ? "accept" : "minor";
        String suggested = switch (consensus) {
            case "accept" -> "ACCEPT";
            case "minor" -> "REVISE";
            case "major" -> "REVISE";
            default -> "REJECT";
        };
        String dissent = recs.stream().distinct().count() > 1
                ? "各审稿人建议等级存在差异：" + String.join("、", recs) + "。"
                : "无实质分歧。";
        String summaryText = "共归纳 %d 份审稿意见。审稿人%s稿件价值，主要意见集中在：%s。"
                .formatted(blocks.size(),
                        consensus.equals("reject") ? "对" : "认可",
                        major.isEmpty() ? "细节完善" : abbreviate(String.join("；", major), 80));

        return "{\"summary\":\"%s\",\"major_issues\":[%s],\"minor_issues\":[%s],\"consensus\":\"%s\",\"dissent\":\"%s\",\"suggested_decision\":\"%s\"}"
                .formatted(jsonEscape(summaryText),
                        toJsonArray(major), toJsonArray(minor),
                        consensus, jsonEscape(dissent), suggested);
    }

    // ---------------- 场景 C：意见解释 ----------------

    private String explain(String input) {
        Matcher m = Pattern.compile("意见\\s*\\d+[：:](.*)").matcher(input);
        List<String> issues = new ArrayList<>();
        while (m.find()) {
            String t = m.group(1).trim();
            if (!t.isEmpty()) {
                issues.add(abbreviate(t.replaceAll("\\s+", " "), 80));
            }
        }
        if (issues.isEmpty()) {
            issues.add(abbreviate(input.replaceAll("\\s+", " "), 80));
        }
        StringBuilder explanations = new StringBuilder();
        StringBuilder actions = new StringBuilder();
        for (String issue : issues) {
            if (explanations.length() > 0) {
                explanations.append(",");
                actions.append(",");
            }
            explanations.append("{\"issue\":\"%s\",\"plain\":\"%s\",\"suggestion\":\"%s\"}"
                    .formatted(jsonEscape(issue),
                            jsonEscape("审稿人认为该方面需要改进：" + issue + "。请结合稿件内容理解其关注点。"),
                            jsonEscape("逐条回应该意见：在修改稿中落实相应修改，并在修改说明中标注修改位置与理由。")));
            actions.append("\"").append(jsonEscape("处理意见：" + abbreviate(issue, 40) + "")).append("\"");
        }
        return "{\"explanations\":[%s],\"action_items\":[%s]}".formatted(explanations, actions);
    }

    // ---------------- 工具 ----------------

    private String between(String text, String start, String end) {
        int i = text.indexOf(start);
        if (i < 0) return "";
        int j = text.indexOf(end, i + start.length());
        return j < 0 ? "" : text.substring(i + start.length(), j).trim();
    }

    private String tail(String text, String start) {
        int i = text.indexOf(start);
        return i < 0 ? "" : text.substring(i + start.length()).trim();
    }

    private int countReferences(String refs) {
        Matcher m = Pattern.compile("(?m)^\\s*\\[\\d+\\]|(?m)^\\s*\\d+[.)]").matcher(refs);
        int count = 0;
        while (m.find()) count++;
        if (count == 0 && !refs.isBlank()) {
            return refs.split("\\n").length;
        }
        return count;
    }

    private double cjkRatio(String text) {
        if (text == null || text.isEmpty()) return 0;
        long cjk = text.chars().filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN).count();
        return (double) cjk / text.length();
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    private String toJsonArray(List<String> items) {
        return items.stream().map(i -> "\"" + jsonEscape(i) + "\"").reduce((a, b) -> a + "," + b).orElse("");
    }
}
