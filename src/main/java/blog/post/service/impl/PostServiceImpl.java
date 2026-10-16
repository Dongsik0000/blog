package blog.post.service.impl;

import blog.cmmn.util.Constants;
import blog.cmmn.util.Response;
import blog.comment.dao.CommentDAO;
import blog.post.dao.PostDAO;
import blog.post.service.PostService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 글 조회·저장·삭제, 시리즈, 대표 이미지 업로드, Markdown 가져오기. 본문 변환은 MarkdownBlocks, 시리즈 정의는 SeriesCatalog
@Service("postService")
public class PostServiceImpl implements PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostServiceImpl.class);

    private static final String POSTS_DIR = "/WEB-INF/posts/";
    private static final String SERIES_FILE = "/WEB-INF/series.yml";
    private static final int PAGE_SIZE = 10;
    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\n(.*?)\\n---(?:\\n|\\z)(.*)\\z", Pattern.DOTALL);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "development", "개발 기록",
            "news", "IT 뉴스",
            "gear", "IT 장비");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private ServletContext servletContext;

    @Resource(name = "postDAO")
    private PostDAO postDAO;

    @Resource(name = "commentDAO")
    private CommentDAO commentDAO;

    @Value("${Globals.Blog.UploadDir}")
    private String uploadDir;

    private SeriesCatalog seriesCatalog = SeriesCatalog.empty();

    // 시리즈 정의를 읽는다. 파일이 없으면 시리즈 없이 동작하고, 형식이 틀리면 앱 시작을 막는다
    @PostConstruct
    void loadSeries() throws IOException {
        try (InputStream in = servletContext.getResourceAsStream(SERIES_FILE)) {
            if (in == null) {
                logger.warn("{} 가 없어 시리즈 없이 시작합니다.", SERIES_FILE);
                return;
            }
            loadSeries(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // 테스트에서도 부른다
    void loadSeries(String yaml) {
        seriesCatalog = SeriesCatalog.parse(yaml);
    }

    // ───────────────────────── 공개 화면 ─────────────────────────

    @Override
    public List<Map<String, Object>> selectRecentPosts(int limit) {
        return addCategoryLabels(postDAO.selectPublishedList(listParam(null, limit, 0)));
    }

    @Override
    public Map<String, Object> selectPostPage(String category, int page) {
        String validCategory = isCategory(category) ? category : null;
        int total = postDAO.selectPublishedCount(listParam(validCategory, 0, 0));
        int totalPages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 1), totalPages);

        Map<String, Object> result = new HashMap<>();
        result.put("posts", addCategoryLabels(postDAO.selectPublishedList(listParam(validCategory, PAGE_SIZE, (current - 1) * PAGE_SIZE))));
        result.put("currentPage", current);
        result.put("totalPages", totalPages);
        result.put("selectedCategory", validCategory == null ? "" : validCategory);
        if ("development".equals(validCategory)) {
            result.put("seriesCards", selectSeriesCards()); // 개발 기록 위에 시리즈 묶음을 보인다
        }
        return result;
    }

    @Override
    public Map<String, Object> selectPost(String slug, boolean includeDraft) {
        Map<String, Object> post = selectRow(slug, includeDraft);
        if (post == null) {
            return null;
        }
        post.put("categoryLabel", CATEGORY_LABELS.get(post.get("category")));
        post.put("hashtags", splitHashtags((String) post.get("hashtags")));
        post.put("sources", readSources((String) post.get("sources")));
        post.put("blocks", toBlocks((String) post.get("body")));
        addSeriesNavigation(post, includeDraft);
        return post;
    }

    @Override
    public List<Map<String, Object>> selectSeriesList() {
        return seriesCatalog.list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> selectSeries(String slug, boolean includeDraft) {
        Map<String, Object> meta = slug == null ? null : seriesCatalog.find(slug);
        if (meta == null) {
            return null;
        }
        List<Map<String, Object>> posts = seriesPosts(slug, includeDraft);
        List<Map<String, Object>> chapters = new ArrayList<>();
        for (Map<String, Object> chapter : (List<Map<String, Object>>) meta.get("chapters")) {
            List<Map<String, Object>> inChapter = posts.stream().filter(p -> chapter.get("id").equals(p.get("chapter"))).toList();
            if (!inChapter.isEmpty()) {
                chapters.add(Map.of("id", chapter.get("id"), "title", chapter.get("title"), "posts", inChapter));
            }
        }
        Map<String, Object> series = new HashMap<>(meta);
        series.put("chapters", chapters);
        series.put("postCount", posts.size());
        series.put("firstPost", posts.isEmpty() ? null : posts.get(0));
        return series;
    }

    // 시리즈의 글을 읽는 순서로: 장 순서(series.yml) → 장 안 순서 → id
    private List<Map<String, Object>> seriesPosts(String slug, boolean includeDraft) {
        Map<String, Object> param = new HashMap<>();
        param.put("series", slug);
        param.put("includeDraft", includeDraft);
        List<Map<String, Object>> posts = new ArrayList<>(postDAO.selectSeriesPosts(param));
        posts.sort(Comparator.<Map<String, Object>>comparingInt(p -> {
                    int index = seriesCatalog.chapterIndex(slug, (String) p.get("chapter"));
                    return index < 0 ? Integer.MAX_VALUE : index;
                })
                .thenComparingInt(p -> ((Number) p.get("seriesOrder")).intValue())
                .thenComparingLong(p -> ((Number) p.get("id")).longValue()));
        return posts;
    }

    // 글 화면: 시리즈 이름·장 이름, 이 장의 글 목록, 시리즈 안의 이전/다음 글
    private void addSeriesNavigation(Map<String, Object> post, boolean includeDraft) {
        String slug = (String) post.get("series");
        Map<String, Object> meta = slug == null ? null : seriesCatalog.find(slug);
        if (meta == null) {
            return;
        }
        String chapter = (String) post.get("chapter");
        List<Map<String, Object>> ordered = seriesPosts(slug, includeDraft);
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).get("id").equals(post.get("id"))) {
                index = i;
            }
        }
        post.put("seriesTitle", meta.get("title"));
        post.put("chapterTitle", seriesCatalog.chapterTitle(slug, chapter));
        post.put("chapterPosts", ordered.stream().filter(p -> chapter != null && chapter.equals(p.get("chapter"))).toList());
        post.put("seriesPrev", index > 0 ? ordered.get(index - 1) : null);
        post.put("seriesNext", index >= 0 && index < ordered.size() - 1 ? ordered.get(index + 1) : null);
    }

    // 개발 기록 목록 위의 시리즈 카드: 정의 + 발행된 글 수
    private List<Map<String, Object>> selectSeriesCards() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : postDAO.selectSeriesCounts()) {
            counts.put((String) row.get("series"), ((Number) row.get("postCount")).intValue());
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map<String, Object> meta : seriesCatalog.list()) {
            int count = counts.getOrDefault((String) meta.get("slug"), 0);
            if (count > 0) {
                Map<String, Object> card = new HashMap<>(meta);
                card.put("postCount", count);
                cards.add(card);
            }
        }
        return cards;
    }

    @Override
    public Long selectPostId(String slug, boolean includeDraft) {
        Map<String, Object> post = selectRow(slug, includeDraft);
        return post == null ? null : ((Number) post.get("id")).longValue();
    }

    private Map<String, Object> selectRow(String slug, boolean includeDraft) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        Map<String, Object> param = new HashMap<>();
        param.put("slug", slug);
        param.put("includeDraft", includeDraft);
        return postDAO.selectPostBySlug(param);
    }

    // ───────────────────────── 관리 화면 ─────────────────────────

    @Override
    public Response selectAdminPostList() throws Exception {
        return Response.of(Constants.SUCCESS, addCategoryLabels(postDAO.selectAdminPostList()));
    }

    @Override
    public Response selectAdminPost(Map<String, Object> param) throws Exception {
        Long id = id(param.get("id"));
        Map<String, Object> post = id == null ? null : postDAO.selectPostById(Map.of("id", id));
        if (post == null) {
            return Response.of(Constants.NOT_FOUND, "글을 찾을 수 없습니다. 이미 삭제됐을 수 있습니다.", null);
        }
        // 편집 칸에 그대로 넣을 글자로 바꾼다
        post.put("hashtags", String.join(", ", splitHashtags((String) post.get("hashtags"))));
        post.put("sources", sourcesToText(readSources((String) post.get("sources"))));
        return Response.of(Constants.SUCCESS, post);
    }

    @Override
    public Response savePost(Map<String, Object> param) throws Exception {
        Long id = id(param.get("id")); // 없으면 새 글

        Map<String, Object> post = new HashMap<>();
        for (String key : List.of("title", "slug", "category", "publishedAt", "summary", "status",
                "coverImage", "coverAlt", "coverCaption", "coverPrompt")) {
            post.put(key, optional(param.get(key)));
        }
        post.put("body", optional(param.get("body")));
        post.put("hashtags", parseHashtagText(optional(param.get("hashtags"))));
        post.put("sources", parseSourceText(optional(param.get("sources"))));
        post.put("series", optional(param.get("series")));
        post.put("chapter", post.get("series") == null ? null : optional(param.get("chapter")));
        post.put("seriesOrder", post.get("series") == null ? null : seriesOrder(param.get("seriesOrder")));
        if (post.get("slug") == null) {
            post.put("slug", generateSlug((String) post.get("publishedAt")));
        }

        String error = validate(post);
        if (error == null) {
            error = seriesProblem(post);
        }
        if (error != null) {
            return Response.invalid(error);
        }
        Map<String, Object> slugParam = new HashMap<>();
        slugParam.put("slug", post.get("slug"));
        slugParam.put("excludeId", id);
        if (postDAO.selectSlugCount(slugParam) > 0) {
            return Response.of(Constants.DUPLICATE, "이미 쓰인 주소(slug)입니다. 지운 글의 주소도 다시 쓸 수 없습니다.", null);
        }

        Map<String, Object> row = toRow(post);
        if (id == null) {
            postDAO.insertPost(row);
            id = ((Number) row.get("id")).longValue();
        } else {
            row.put("id", id);
            if (postDAO.updatePost(row) == 0) {
                return Response.of(Constants.NOT_FOUND, "글을 찾을 수 없습니다. 이미 삭제됐을 수 있습니다.", null);
            }
        }
        registerPromptComment(id, (String) post.get("coverPrompt"));

        Map<String, Object> saved = new HashMap<>();
        saved.put("id", id);
        saved.put("slug", post.get("slug"));
        saved.put("status", post.get("status"));
        return Response.of(Constants.SUCCESS, saved);
    }

    @Override
    public Response deletePost(Map<String, Object> param) throws Exception {
        Long id = id(param.get("id"));
        if (id == null || postDAO.deletePost(Map.of("id", id)) == 0) {
            return Response.of(Constants.NOT_FOUND, "이미 삭제된 글입니다.", null);
        }
        return Response.of(Constants.SUCCESS);
    }

    // 대표 이미지 저장. 형식은 파일 앞부분(매직 바이트)으로 확인한다 - 확장자·Content-Type 은 보내는 쪽이 마음대로 정할 수 있다.
    // SVG 는 받지 않는다(스크립트를 담을 수 있다). 크기 제한(5MB)은 web.xml multipart-config.
    @Override
    public Response uploadImage(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return Response.invalid("이미지 파일을 골라 주세요.");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(12);
        }
        String extension = imageExtension(head);
        if (extension == null) {
            return Response.invalid("JPG, PNG, WEBP, GIF 이미지만 올릴 수 있습니다.");
        }

        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);
        String name = LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + randomHex(8) + "." + extension;
        file.transferTo(dir.resolve(name));
        return Response.of(Constants.SUCCESS, Map.of("url", "/uploads/" + name));
    }

    static String imageExtension(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return "png";
        }
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') {
            return "gif";
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }

    // ───────────────────────── Markdown 가져오기 ─────────────────────────

    // WAR 안의 WEB-INF/posts/*.md (content/posts) 중 DB 에 slug 가 없는 글만 넣는다.
    // 한 번 들어간 글은 관리 화면에서 고치므로, 이후 파일을 바꿔도 반영하지 않는다. 지운 글도 slug 가 남아 다시 들어오지 않는다.
    // 형식이 틀린 파일은 건너뛰고 로그를 남긴다(배포 전 mvn test 가 먼저 막는다).
    @EventListener(ContextRefreshedEvent.class)
    public void importMarkdownPosts() {
        Set<String> paths = new TreeSet<>();
        collectMarkdown(POSTS_DIR, paths);
        int imported = 0;
        for (String path : paths) {
            Map<String, Object> post;
            try (InputStream in = servletContext.getResourceAsStream(path)) {
                post = parse(path.substring(POSTS_DIR.length()), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                logger.error("글 파일을 가져오지 못했습니다: {}", path, e);
                continue;
            }
            dropUnreadableCover(post, path);
            try {
                Map<String, Object> row = toRow(post);
                if (postDAO.insertImportedPost(row) > 0) {
                    imported++;
                    registerPromptComment(((Number) row.get("id")).longValue(), (String) post.get("coverPrompt"));
                }
            } catch (DataAccessException dae) {
                logger.error("글을 DB 에 넣지 못했습니다. DB 연결을 확인하세요. ({})", path, dae);
                return; // DB 문제면 나머지 파일도 같은 이유로 실패한다
            }
        }
        logger.info("Markdown 글 {}개를 새로 가져왔습니다.", imported);
    }

    // 폴더 안의 .md 를 하위 폴더까지 모은다 (시리즈 글은 content/posts/series/{시리즈}/{장}/ 에 있다)
    private void collectMarkdown(String dir, Set<String> out) {
        Set<String> paths = servletContext.getResourcePaths(dir);
        if (paths == null) {
            return;
        }
        for (String path : paths) {
            if (path.endsWith("/")) {
                collectMarkdown(path, out);
            } else if (path.endsWith(".md")) {
                out.add(path);
            }
        }
    }

    // AI 가 커밋한 표지 이미지가 WAR 에 없거나 이미지가 아니면(예: 바이너리 대신 base64 글자가 저장됨) 표지 없이 가져온다.
    // 프롬프트 댓글은 그대로 달리므로 관리 화면에서 이미지를 올리면 된다.
    private void dropUnreadableCover(Map<String, Object> post, String path) {
        String cover = (String) post.get("coverImage");
        if (cover == null || !cover.startsWith("/resources/images/")) {
            return;
        }
        boolean readable;
        try (InputStream in = servletContext.getResourceAsStream(cover)) {
            readable = in != null && imageExtension(in.readNBytes(12)) != null;
        } catch (IOException e) {
            readable = false;
        }
        if (!readable) {
            logger.warn("대표 이미지 파일이 없거나 이미지가 아니어서 표지 없이 가져옵니다: {} ({})", cover, path);
            post.put("coverImage", null);
            post.put("coverAlt", null);
            post.put("coverCaption", null);
        }
    }

    // 원문 하나 → 글 Map(body 는 Markdown 그대로). 형식이 틀리면 IllegalStateException(파일명: 이유)
    Map<String, Object> parse(String fileName, String source) {
        String text = source.replace("\r\n", "\n");
        if (text.startsWith("﻿")) {
            text = text.substring(1); // BOM
        }
        Matcher matcher = FRONT_MATTER.matcher(text);
        if (!matcher.matches()) {
            throw invalid(fileName, "--- 로 감싼 front matter 로 시작해야 합니다");
        }
        // SafeConstructor: YAML 태그로 임의의 Java 객체를 만들지 못하게 한다
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(matcher.group(1));
        if (!(loaded instanceof Map<?, ?> meta)) {
            throw invalid(fileName, "front matter 가 비어 있거나 형식이 틀렸습니다");
        }

        Map<String, Object> post = new HashMap<>();
        for (String key : List.of("title", "slug", "category", "publishedAt", "summary",
                "coverImage", "coverAlt", "coverCaption", "coverPrompt")) {
            post.put(key, frontMatterText(meta, key, fileName));
        }
        if (post.get("slug") == null) {
            throw invalid(fileName, "slug 가 필요합니다");
        }
        post.put("status", Boolean.TRUE.equals(meta.get("draft")) ? "DRAFT" : "PUBLISHED");
        post.put("hashtags", hashtags(meta, fileName));
        post.put("sources", sources(meta, fileName));
        post.put("body", matcher.group(2).strip());
        post.put("series", frontMatterText(meta, "series", fileName));
        post.put("chapter", frontMatterText(meta, "chapter", fileName));
        Object order = meta.get("seriesOrder");
        if (order != null && !(order instanceof Integer)) {
            throw invalid(fileName, "seriesOrder 는 따옴표 없는 숫자여야 합니다");
        }
        post.put("seriesOrder", order);

        String error = validate(post);
        if (error == null) {
            error = seriesProblem(post);
        }
        if (error != null) {
            throw invalid(fileName, error);
        }
        return post;
    }

    // ───────────────────────── 검증 (관리 화면 저장과 Markdown 가져오기가 함께 쓴다) ─────────────────────────

    // 틀린 곳의 안내 문구, 맞으면 null
    @SuppressWarnings("unchecked")
    static String validate(Map<String, Object> post) {
        String title = (String) post.get("title");
        if (title == null) {
            return "제목을 입력해 주세요.";
        }
        if (title.length() > 200) {
            return "제목은 200자 이하로 입력해 주세요.";
        }
        String slug = (String) post.get("slug");
        if (slug == null || slug.length() > 120 || !SLUG.matcher(slug).matches()) {
            return "주소(slug)는 영문 소문자·숫자·하이픈으로 120자 이하로 입력해 주세요. 예: my-first-post";
        }
        if (!isCategory(post.get("category"))) {
            return "주제를 골라 주세요.";
        }
        if (!isDate((String) post.get("publishedAt"))) {
            return "발행일을 yyyy-MM-dd 형식으로 입력해 주세요.";
        }
        String summary = (String) post.get("summary");
        if (summary == null || summary.length() > 300) {
            return "요약을 300자 이하로 입력해 주세요.";
        }
        if (post.get("body") == null) {
            return "본문을 입력해 주세요.";
        }
        String imageProblem = MarkdownBlocks.imageProblem((String) post.get("body"));
        if (imageProblem != null) {
            return imageProblem;
        }
        if (post.get("status") == null || !STATUSES.contains(post.get("status"))) {
            return "상태가 올바르지 않습니다.";
        }

        String coverImage = (String) post.get("coverImage");
        if (coverImage != null) {
            boolean allowedPath = coverImage.startsWith("/resources/images/") || coverImage.startsWith("/uploads/");
            if (!allowedPath || coverImage.contains("..") || coverImage.length() > 300) {
                return "대표 이미지 경로가 올바르지 않습니다.";
            }
            if (post.get("coverAlt") == null) {
                return "대표 이미지 설명(대체 텍스트)을 입력해 주세요. 화면을 읽어 주는 프로그램이 이 설명을 읽습니다.";
            }
        }
        if (length(post.get("coverAlt")) > 200 || length(post.get("coverCaption")) > 200) {
            return "대표 이미지 설명과 캡션은 200자 이하로 입력해 주세요.";
        }
        if (length(post.get("coverPrompt")) > 2000) {
            return "이미지 프롬프트는 2000자 이하로 입력해 주세요.";
        }
        if (String.join(",", (List<String>) post.get("hashtags")).length() > 500) {
            return "해시태그가 너무 많습니다.";
        }

        List<Map<String, Object>> sources = (List<Map<String, Object>>) post.get("sources");
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> source = sources.get(i);
            String line = "출처 " + (i + 1) + "번째: ";
            if (source.get("publisher") == null || source.get("title") == null) {
                return line + "매체명과 기사 제목이 필요합니다. (매체명 | 기사 제목 | URL | 보도일)";
            }
            String url = (String) source.get("url");
            if (url != null && !url.startsWith("https://")) {
                return line + "URL 은 https:// 로 시작해야 합니다.";
            }
            String publishedAt = (String) source.get("publishedAt");
            if (publishedAt != null && !isDate(publishedAt)) {
                return line + "보도일은 yyyy-MM-dd 형식이어야 합니다.";
            }
        }
        return null;
    }

    // 시리즈 칸: 셋 다 비었거나, 시리즈·장이 series.yml 에 있고 순서가 1~999. 틀린 곳의 안내 문구, 맞으면 null
    String seriesProblem(Map<String, Object> post) {
        String series = (String) post.get("series");
        String chapter = (String) post.get("chapter");
        Object order = post.get("seriesOrder");
        if (series == null && chapter == null && order == null) {
            return null;
        }
        if (series == null || seriesCatalog.find(series) == null) {
            return "시리즈를 골라 주세요.";
        }
        if (chapter == null || seriesCatalog.chapterIndex(series, chapter) < 0) {
            return "시리즈의 장을 골라 주세요.";
        }
        if (!(order instanceof Integer number) || number < 1 || number > 999) {
            return "장 안의 순서를 1~999 사이 숫자로 입력해 주세요.";
        }
        return null;
    }

    // 편집 칸의 순서 글자 → 숫자. 비었으면 null, 숫자가 아니면 -1(검증에서 안내)
    private static Integer seriesOrder(Object value) {
        String text = optional(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Map.of 로 만든 맵은 containsKey(null) 에서 예외를 던지므로 null 을 먼저 거른다
    private static boolean isCategory(Object category) {
        return category != null && CATEGORY_LABELS.containsKey(category);
    }

    private static boolean isDate(String value) {
        if (value == null) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static int length(Object value) {
        return value == null ? 0 : ((String) value).length();
    }

    // ───────────────────────── 저장 형식 변환 ─────────────────────────

    // 글 Map → DB 행: 해시태그는 쉼표로 잇고, 출처는 JSON 으로
    private static Map<String, Object> toRow(Map<String, Object> post) {
        Map<String, Object> row = new HashMap<>(post);
        @SuppressWarnings("unchecked")
        List<String> hashtags = (List<String>) post.get("hashtags");
        row.put("hashtags", hashtags.isEmpty() ? null : String.join(",", hashtags));
        List<?> sources = (List<?>) post.get("sources");
        try {
            row.put("sources", sources.isEmpty() ? null : JSON.writeValueAsString(sources));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("출처를 JSON 으로 바꾸지 못했습니다.", e);
        }
        return row;
    }

    private static List<String> splitHashtags(String stored) {
        List<String> tags = new ArrayList<>();
        if (stored != null) {
            for (String tag : stored.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag.strip());
                }
            }
        }
        return tags;
    }

    private static List<Map<String, Object>> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 출처 JSON 을 읽지 못했습니다.", e);
        }
    }

    // 편집 칸 "IT뉴스, #AI  DevGear" → [IT뉴스, AI, DevGear] (쉼표·공백 구분, 앞의 # 제거, 중복 제거)
    static List<String> parseHashtagText(String text) {
        Set<String> tags = new LinkedHashSet<>();
        if (text != null) {
            for (String tag : text.split("[,\\s]+")) {
                String cleaned = tag.replaceFirst("^#+", "").strip();
                if (!cleaned.isEmpty()) {
                    tags.add(cleaned);
                }
            }
        }
        return new ArrayList<>(tags);
    }

    // 편집 칸: 한 줄에 "매체명 | 기사 제목 | URL | 보도일" (URL·보도일은 생략 가능)
    static List<Map<String, Object>> parseSourceText(String text) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (text == null) {
            return sources;
        }
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("publisher", part(parts, 0));
            source.put("title", part(parts, 1));
            source.put("url", part(parts, 2));
            source.put("publishedAt", part(parts, 3));
            sources.add(source);
        }
        return sources;
    }

    private static String part(String[] parts, int index) {
        return index < parts.length && !parts[index].isBlank() ? parts[index].strip() : null;
    }

    private static String sourcesToText(List<Map<String, Object>> sources) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            List<String> parts = new ArrayList<>();
            for (String key : List.of("publisher", "title", "url", "publishedAt")) {
                parts.add(source.get(key) == null ? "" : String.valueOf(source.get(key)));
            }
            while (!parts.isEmpty() && parts.get(parts.size() - 1).isEmpty()) {
                parts.remove(parts.size() - 1);
            }
            lines.add(String.join(" | ", parts));
        }
        return String.join("\n", lines);
    }

    // 주소를 비워 두면: 발행일-무작위 6자리 (예: 2026-10-13-3f9a1c)
    private static String generateSlug(String publishedAt) {
        String date = isDate(publishedAt) ? publishedAt : LocalDate.now(SEOUL).toString();
        return date + "-" + randomHex(3);
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }

    // coverPrompt 를 DevMonster 댓글로 등록. 글마다 한 번만(SQL 의 ON CONFLICT)
    private void registerPromptComment(Long postId, String prompt) {
        if (prompt == null) {
            return;
        }
        Map<String, Object> comment = new HashMap<>();
        comment.put("postId", postId);
        comment.put("authorName", Constants.AUTHOR_NAME);
        comment.put("content", "대표 이미지 프롬프트\n" + prompt);
        commentDAO.insertPromptComment(comment);
    }

    private List<Map<String, Object>> addCategoryLabels(List<Map<String, Object>> posts) {
        for (Map<String, Object> post : posts) {
            post.put("categoryLabel", CATEGORY_LABELS.get(post.get("category")));
            Map<String, Object> series = post.get("series") == null ? null : seriesCatalog.find((String) post.get("series"));
            post.put("seriesTitle", series == null ? null : series.get("title"));
        }
        return posts;
    }

    private static Map<String, Object> listParam(String category, int limit, int offset) {
        Map<String, Object> param = new HashMap<>();
        param.put("category", category);
        param.put("limit", limit);
        param.put("offset", offset);
        return param;
    }

    // 앞뒤 공백을 뗀 글자. 없거나 비면 null
    private static String optional(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    // JSON 숫자·글자 모두 받는다. 숫자가 아니면 null
    private static Long id(Object value) {
        try {
            return value == null || String.valueOf(value).isBlank() ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ───────────────────────── front matter 읽기 ─────────────────────────

    // 없거나 빈 글자면 null. 글자가 아니면(따옴표 없는 날짜 등) 예외
    private static String frontMatterText(Map<?, ?> meta, String key, String fileName) {
        Object value = meta.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw invalid(fileName, key + " 는 따옴표로 감싼 글자여야 합니다");
        }
        return text.isBlank() ? null : text.strip();
    }

    // hashtags: ["DevGear", "#IT뉴스"] → 앞의 # 를 뗀 글자 목록
    private static List<String> hashtags(Map<?, ?> meta, String fileName) {
        Object value = meta.get("hashtags");
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof List<?> list)) {
            throw invalid(fileName, "hashtags 는 목록이어야 합니다");
        }
        Set<String> tags = new LinkedHashSet<>();
        for (Object tag : list) {
            if (!(tag instanceof String s) || s.isBlank()) {
                throw invalid(fileName, "hashtags 의 항목은 글자여야 합니다");
            }
            tags.add(s.strip().replaceFirst("^#+", ""));
        }
        return new ArrayList<>(tags);
    }

    // sources: [{publisher, title, url, publishedAt}, ...]
    private static List<Map<String, Object>> sources(Map<?, ?> meta, String fileName) {
        Object value = meta.get("sources");
        if (value == null) {
            return new ArrayList<>();
        }
        if (!(value instanceof List<?> list)) {
            throw invalid(fileName, "sources 는 목록이어야 합니다");
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> source)) {
                throw invalid(fileName, "sources 의 항목은 publisher·title 을 가진 객체여야 합니다");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            for (String key : List.of("publisher", "title", "url", "publishedAt")) {
                entry.put(key, frontMatterText(source, key, fileName));
            }
            sources.add(entry);
        }
        return sources;
    }

    private static IllegalStateException invalid(String fileName, String reason) {
        return new IllegalStateException(fileName + ": " + reason);
    }

    // ───────────────────────── 본문 Markdown → 화면 블록 ─────────────────────────

    List<Map<String, Object>> toBlocks(String markdown) {
        return MarkdownBlocks.toBlocks(markdown);
    }
}
