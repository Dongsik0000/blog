package blog.post.service.impl;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// 시리즈 정의(content/series.yml → WEB-INF/series.yml). 글의 series·chapter 가 여기 있는 값을 가리킨다.
// 목록 순서 = 화면 순서, chapters 순서 = 장 순서. 형식이 틀리면 IllegalStateException(앱 시작 실패, mvn test 가 먼저 막는다)
final class SeriesCatalog {

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    // [{slug, title, summary, stack(List), repo, chapters: [{id, title}]}]
    private final List<Map<String, Object>> series;

    private SeriesCatalog(List<Map<String, Object>> series) {
        this.series = series;
    }

    static SeriesCatalog empty() {
        return new SeriesCatalog(List.of());
    }

    static SeriesCatalog parse(String yaml) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(loaded instanceof List<?> list)) {
            throw new IllegalStateException("series.yml: 시리즈 목록이어야 합니다");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> slugs = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalStateException("series.yml: 항목은 slug·title·chapters 를 가진 객체여야 합니다");
            }
            String slug = text(raw, "slug");
            if (!ID.matcher(slug).matches() || !slugs.add(slug)) {
                throw new IllegalStateException("series.yml: slug 가 형식에 맞지 않거나 겹칩니다: " + slug);
            }
            List<Map<String, Object>> chapters = new ArrayList<>();
            Set<String> chapterIds = new LinkedHashSet<>();
            if (!(raw.get("chapters") instanceof List<?> rawChapters) || rawChapters.isEmpty()) {
                throw new IllegalStateException("series.yml: " + slug + " 에 chapters 가 없습니다");
            }
            for (Object c : rawChapters) {
                if (!(c instanceof Map<?, ?> chapter)) {
                    throw new IllegalStateException("series.yml: " + slug + " 의 장은 id·title 을 가진 객체여야 합니다");
                }
                String id = text(chapter, "id");
                if (!ID.matcher(id).matches() || !chapterIds.add(id)) {
                    throw new IllegalStateException("series.yml: " + slug + " 의 장 id 가 형식에 맞지 않거나 겹칩니다: " + id);
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", id);
                entry.put("title", text(chapter, "title"));
                chapters.add(entry);
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slug", slug);
            entry.put("title", text(raw, "title"));
            entry.put("summary", raw.get("summary") instanceof String s ? s.strip() : null);
            entry.put("stack", raw.get("stack") instanceof List<?> stack ? stack.stream().map(String::valueOf).toList() : List.of());
            entry.put("repo", raw.get("repo") instanceof String r && r.startsWith("https://") ? r : null);
            entry.put("chapters", List.copyOf(chapters));
            result.add(entry);
        }
        return new SeriesCatalog(List.copyOf(result));
    }

    private static String text(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("series.yml: " + key + " 가 필요합니다");
        }
        return value.strip();
    }

    List<Map<String, Object>> list() {
        return series;
    }

    // 없으면 null
    Map<String, Object> find(String slug) {
        for (Map<String, Object> s : series) {
            if (s.get("slug").equals(slug)) {
                return s;
            }
        }
        return null;
    }

    // 시리즈 안 장의 순서(0부터). 없으면 -1
    @SuppressWarnings("unchecked")
    int chapterIndex(String slug, String chapterId) {
        Map<String, Object> s = find(slug);
        if (s == null) {
            return -1;
        }
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) s.get("chapters");
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).get("id").equals(chapterId)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    String chapterTitle(String slug, String chapterId) {
        int index = chapterIndex(slug, chapterId);
        return index < 0 ? null : (String) ((List<Map<String, Object>>) find(slug).get("chapters")).get(index).get("title");
    }
}
