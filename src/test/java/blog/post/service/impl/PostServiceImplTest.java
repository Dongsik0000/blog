package blog.post.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 배포 전에 글 형식을 검사한다. 형식이 틀린 글이 있으면 mvn test 가 실패해 배포되지 않는다.
class PostServiceImplTest {

    private final PostServiceImpl service = new PostServiceImpl();

    // 시리즈 글은 content/series.yml 의 시리즈·장을 가리켜야 한다
    @BeforeEach
    void loadSeries() throws IOException {
        service.loadSeries(Files.readString(Path.of("content/series.yml")));
    }

    // content/posts(하위 폴더 포함)와 content/samples 의 모든 글이 해석되어야 한다
    @Test
    void allPostsParse() throws IOException {
        List<String> problems = new java.util.ArrayList<>(); // 첫 문제에서 멈추지 않고 모두 모아 보여 준다
        for (String dir : List.of("content/posts", "content/samples")) {
            try (Stream<Path> files = Files.walk(Path.of(dir))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                    try {
                        Map<String, Object> post = service.parse(file.getFileName().toString(), Files.readString(file));
                        List<Map<String, Object>> blocks = service.toBlocks((String) post.get("body"));
                        if (blocks.isEmpty()) {
                            problems.add(file + ": 본문이 비어 있습니다");
                        }
                        // 굵게가 적용되지 않고 ** 가 글자로 남은 곳(코드 글자·코드 블록 안은 제외).
                        // 예: **이름(설명)**은 → **이름**(설명)은 으로 고친다
                        if (proseText(blocks).contains("**")) {
                            problems.add(file + ": 굵게가 적용되지 않은 ** 가 있습니다");
                        }
                    } catch (IllegalStateException e) {
                        problems.add(e.getMessage());
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "글 형식 문제 " + problems.size() + "건\n" + String.join("\n", problems));
    }

    // 블록 중 코드가 아닌 글자만 이어 붙인다 (코드 블록, 코드 글자 구간 제외)
    @SuppressWarnings("unchecked")
    private static String proseText(Object node) {
        StringBuilder text = new StringBuilder();
        if (node instanceof List<?> list) {
            for (Object item : list) {
                text.append(proseText(item)).append(' ');
            }
        } else if (node instanceof Map<?, ?> map) {
            if ("code".equals(map.get("type")) || Boolean.TRUE.equals(map.get("code"))) {
                return "";
            }
            if (map.get("text") instanceof String t) {
                text.append(t);
            }
            for (String key : List.of("segments", "items", "head", "rows")) {
                text.append(proseText(map.get(key)));
            }
        }
        return text.toString();
    }

    private static Map<String, Object> seg(String text, boolean bold, boolean italic, boolean code, String href) {
        Map<String, Object> segment = new HashMap<>();
        segment.put("text", text);
        segment.put("bold", bold);
        segment.put("italic", italic);
        segment.put("code", code);
        segment.put("href", href);
        return segment;
    }

    @Test
    void inlineStylesBecomeSegments() {
        List<?> segments = (List<?>) service.toBlocks("앞 **굵게** *기울임* `코드` [안](/posts/a-b) [밖](https://example.com) [위험](javascript:alert(1))")
                .get(0).get("segments");
        assertEquals(seg("굵게", true, false, false, null), segments.get(1));
        assertEquals(seg("기울임", false, true, false, null), segments.get(3));
        assertEquals(seg("코드", false, false, true, null), segments.get(5));
        assertEquals(seg("안", false, false, false, "/posts/a-b"), segments.get(7));
        assertEquals(seg("밖", false, false, false, "https://example.com"), segments.get(9));
        assertEquals(seg(" 위험", false, false, false, null), segments.get(10)); // 허용하지 않는 주소는 글자만
    }

    @Test
    void rawHtmlIsDropped() {
        assertTrue(service.toBlocks("<script>alert(1)</script>").isEmpty());
        assertTrue(service.toBlocks("<!-- 📷 스크린샷: 화면 -->").isEmpty()); // 스크린샷 자리 표시는 방문자에게 안 보임
        List<?> segments = (List<?>) service.toBlocks("글 <b>굵게</b> 끝").get(0).get("segments");
        assertEquals(1, segments.size());
        assertEquals("글 굵게 끝", ((Map<?, ?>) segments.get(0)).get("text"));
    }

    @Test
    void tablesListsAndImages() {
        Map<String, Object> table = service.toBlocks("| 이름 | 값 |\n|---|---|\n| `a` | 1 |\n| b | 2 |").get(0);
        assertEquals("table", table.get("type"));
        assertEquals(2, ((List<?>) table.get("head")).size());
        assertEquals(2, ((List<?>) table.get("rows")).size());

        assertEquals(true, service.toBlocks("1. 하나\n2. 둘").get(0).get("ordered"));
        assertEquals(false, service.toBlocks("- 하나\n- 둘").get(0).get("ordered"));

        Map<String, Object> image = service.toBlocks("![설정 화면](/uploads/20261016-ab.jpg)").get(0);
        assertEquals("image", image.get("type"));
        assertEquals("설정 화면", image.get("alt"));
        assertTrue(service.toBlocks("![밖](https://evil.example/a.png)").isEmpty()); // 밖의 이미지는 안 그린다

        assertNull(MarkdownBlocks.imageProblem("![설정 화면](/uploads/a.jpg)"));
        assertNotNull(MarkdownBlocks.imageProblem("![](/uploads/a.jpg)"));                       // 설명 없음
        assertNotNull(MarkdownBlocks.imageProblem("![이미지 설명을 써 주세요](/uploads/a.jpg)"));   // 자리 글자 그대로
        assertNotNull(MarkdownBlocks.imageProblem("![설명](https://evil.example/a.png)"));       // 밖의 주소
        assertNull(MarkdownBlocks.imageProblem("`<![CDATA[ a ]]>` 코드 글자는 이미지가 아님"));
    }

    @Test
    void invalidFrontMatterFails() {
        String badSlug = "---\ntitle: \"t\"\nslug: \"Bad Slug\"\ncategory: \"news\"\npublishedAt: \"2026-10-13\"\nsummary: \"s\"\n---\n본문";
        assertThrows(IllegalStateException.class, () -> service.parse("bad.md", badSlug));

        String unquotedDate = badSlug.replace("\"Bad Slug\"", "\"ok\"").replace("\"2026-10-13\"", "2026-10-13");
        assertThrows(IllegalStateException.class, () -> service.parse("date.md", unquotedDate));

        String good = badSlug.replace("\"Bad Slug\"", "\"ok\"");
        String unknownChapter = good.replace("summary: \"s\"", "summary: \"s\"\nseries: \"ledger\"\nchapter: \"없는장\"\nseriesOrder: 1");
        assertThrows(IllegalStateException.class, () -> service.parse("series.md", unknownChapter));
    }

    // 관리 화면 입력: 해시태그·출처 칸 해석과 검증
    @Test
    void editorInputIsParsedAndValidated() {
        assertEquals(List.of("IT뉴스", "AI", "DevGear"), PostServiceImpl.parseHashtagText("IT뉴스, #AI  DevGear,IT뉴스"));

        List<Map<String, Object>> sources = PostServiceImpl.parseSourceText("매체 | 제목 | https://a.example | 2026-10-12\n\n매체2 | 제목2");
        assertEquals(2, sources.size());
        assertNull(sources.get(1).get("url"));

        Map<String, Object> post = new HashMap<>();
        post.put("title", "제목");
        post.put("slug", "my-post");
        post.put("category", "news");
        post.put("publishedAt", "2026-10-13");
        post.put("summary", "요약");
        post.put("body", "본문");
        post.put("status", "DRAFT");
        post.put("hashtags", List.of());
        post.put("sources", sources);
        assertNull(PostServiceImpl.validate(post));

        post.put("sources", PostServiceImpl.parseSourceText("매체 | 제목 | http://insecure.example"));
        assertNotNull(PostServiceImpl.validate(post)); // https 만

        // 주제·상태를 비워 보내도 예외가 아니라 안내 문구 (Map.of·Set.of 는 null 조회에서 예외를 던진다)
        post.put("category", null);
        assertEquals("주제를 골라 주세요.", PostServiceImpl.validate(post));
        post.put("category", "news");
        post.put("status", null);
        assertEquals("상태가 올바르지 않습니다.", PostServiceImpl.validate(post));
        post.put("status", "DRAFT");

        post.put("sources", List.of());
        post.put("coverImage", "/uploads/a.jpg");
        assertNotNull(PostServiceImpl.validate(post)); // 이미지에는 대체 텍스트가 필요
        post.put("coverImage", "https://evil.example/a.jpg");
        post.put("coverAlt", "설명");
        assertNotNull(PostServiceImpl.validate(post)); // 외부 주소는 안 됨

        // 시리즈 칸: 셋 다 비거나, 정의에 있는 시리즈·장 + 1~999 순서
        assertNull(service.seriesProblem(post));
        post.put("series", "ledger");
        post.put("chapter", "auth");
        post.put("seriesOrder", 0);
        assertNotNull(service.seriesProblem(post));
        post.put("seriesOrder", 3);
        assertNull(service.seriesProblem(post));
    }

    @Test
    void imageTypeIsDetectedFromBytes() {
        assertEquals("png", PostServiceImpl.imageExtension(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertNull(PostServiceImpl.imageExtension("<svg xmlns=".getBytes()));
    }
}
