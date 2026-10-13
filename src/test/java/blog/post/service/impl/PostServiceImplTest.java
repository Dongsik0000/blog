package blog.post.service.impl;

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

    // content/posts(가져올 글)와 content/samples(샘플)의 모든 글이 해석되어야 한다
    @Test
    void allPostsParse() throws IOException {
        for (String dir : List.of("content/posts", "content/samples")) {
            try (Stream<Path> files = Files.list(Path.of(dir))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".md")).toList()) {
                    Map<String, Object> post = service.parse(file.getFileName().toString(), Files.readString(file));
                    List<Map<String, Object>> blocks = service.toBlocks((String) post.get("body"));
                    assertTrue(!blocks.isEmpty(), file + " 본문이 비어 있습니다");
                    // 굵게가 적용되지 않고 ** 가 글자로 남은 곳. 예: **이름(설명)**은 → **이름**(설명)은 으로 고친다
                    assertTrue(!blocks.toString().contains("**"), file + " 에 굵게가 적용되지 않은 ** 가 있습니다");
                }
            }
        }
    }

    @Test
    void boldAndItalicBecomeSegments() {
        List<Map<String, Object>> blocks = service.toBlocks("앞 **굵게** *기울임*");
        assertEquals("paragraph", blocks.get(0).get("type"));
        List<?> segments = (List<?>) blocks.get(0).get("segments");
        assertEquals(Map.of("text", "굵게", "bold", true, "italic", false), segments.get(1));
        assertEquals(Map.of("text", "기울임", "bold", false, "italic", true), segments.get(3));
    }

    @Test
    void rawHtmlIsDropped() {
        assertTrue(service.toBlocks("<script>alert(1)</script>").isEmpty());
        List<?> segments = (List<?>) service.toBlocks("글 <b>굵게</b> 끝").get(0).get("segments");
        assertEquals(1, segments.size());
        assertEquals("글 굵게 끝", ((Map<?, ?>) segments.get(0)).get("text"));
    }

    @Test
    void invalidFrontMatterFails() {
        String badSlug = "---\ntitle: \"t\"\nslug: \"Bad Slug\"\ncategory: \"news\"\npublishedAt: \"2026-10-13\"\nsummary: \"s\"\n---\n본문";
        assertThrows(IllegalStateException.class, () -> service.parse("bad.md", badSlug));

        String unquotedDate = badSlug.replace("\"Bad Slug\"", "\"ok\"").replace("\"2026-10-13\"", "2026-10-13");
        assertThrows(IllegalStateException.class, () -> service.parse("date.md", unquotedDate));
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
    }

    @Test
    void imageTypeIsDetectedFromBytes() {
        assertEquals("png", PostServiceImpl.imageExtension(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertNull(PostServiceImpl.imageExtension("<svg xmlns=".getBytes()));
    }
}
