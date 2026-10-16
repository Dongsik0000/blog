package blog.post.service.impl;

import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

// 본문 Markdown → JSP(postDetail.jsp)가 그리는 블록 목록. PostServiceImpl 이 쓴다.
// 지원: ## ### 소제목, 문단, 인용, 목록(번호 목록 포함), 코드 블록, 표, 본문 이미지, 굵게·기울임·코드 글자·링크.
// 원본 HTML 은 버린다(<!-- 📷 스크린샷: ... --> 같은 표시는 방문자에게 보이지 않는다).
// 링크·이미지 주소는 허용한 형태만 남긴다(JSP 가 HTML 로 내보내므로 javascript: 같은 주소를 막는다).
final class MarkdownBlocks {

    private static final Parser PARSER = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    // 본문 이미지: 이 블로그에 올린 파일만
    private static final Pattern IMAGE_SRC = Pattern.compile("/(uploads|resources/images)/[A-Za-z0-9._/-]+");
    // 블로그 안 링크: 글·시리즈·목록 주소만 (앞에 /blog 는 JSP 의 c:url 이 붙인다)
    private static final Pattern INTERNAL_HREF = Pattern.compile("/(posts|series)(/[a-z0-9-]+)?(\\?category=[a-z]+)?");
    static final String IMAGE_ALT_PLACEHOLDER = "이미지 설명을 써 주세요"; // 편집 화면이 넣는 자리 글자

    private MarkdownBlocks() {
    }

    static List<Map<String, Object>> toBlocks(String markdown) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Node node = PARSER.parse(markdown).getFirstChild(); node != null; node = node.getNext()) {
            Map<String, Object> block = toBlock(node);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    // 본문 이미지에 문제가 있으면 안내 문구, 없으면 null (저장·가져오기 전에 검사)
    static String imageProblem(String markdown) {
        List<Image> images = new ArrayList<>();
        findImages(PARSER.parse(markdown), images);
        for (Image image : images) {
            if (!IMAGE_SRC.matcher(image.getDestination()).matches() || image.getDestination().contains("..")) {
                return "본문 이미지는 이 블로그에 올린 이미지만 쓸 수 있습니다: " + image.getDestination();
            }
            String alt = plainText(image);
            if (alt.isBlank() || alt.equals(IMAGE_ALT_PLACEHOLDER)) {
                return "본문 이미지 설명을 써 주세요. ![여기에 설명](" + image.getDestination() + ") 의 대괄호 안입니다.";
            }
            if (alt.length() > 200) {
                return "본문 이미지 설명은 200자 이하로 써 주세요.";
            }
        }
        return null;
    }

    private static void findImages(Node node, List<Image> out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image image) {
                out.add(image);
            }
            findImages(child, out);
        }
    }

    // 최상위 노드 하나 → 블록. 화면에 내보내지 않는 노드(HtmlBlock, 구분선 등)는 null
    private static Map<String, Object> toBlock(Node node) {
        if (node instanceof Heading heading) {
            String text = plainText(heading);
            return text.isBlank() ? null : block(heading.getLevel() <= 2 ? "heading2" : "heading3", "text", text);
        }
        if (node instanceof Paragraph) {
            Image image = soleImage(node);
            if (image != null) {
                Map<String, Object> block = block("image", "src", image.getDestination());
                block.put("alt", plainText(image));
                return IMAGE_SRC.matcher(image.getDestination()).matches() ? block : null;
            }
            List<Map<String, Object>> segments = segments(node);
            return segments.isEmpty() ? null : block("paragraph", "segments", segments);
        }
        if (node instanceof BlockQuote) {
            List<Map<String, Object>> segments = segments(node);
            return segments.isEmpty() ? null : block("quote", "segments", segments);
        }
        if (node instanceof ListBlock) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                List<Map<String, Object>> segments = segments(item);
                if (!segments.isEmpty()) {
                    items.add(Map.of("segments", segments));
                }
            }
            if (items.isEmpty()) {
                return null;
            }
            Map<String, Object> block = block("list", "items", items);
            block.put("ordered", node instanceof OrderedList);
            return block;
        }
        if (node instanceof FencedCodeBlock code) {
            return block("code", "text", code.getLiteral().stripTrailing());
        }
        if (node instanceof IndentedCodeBlock code) {
            return block("code", "text", code.getLiteral().stripTrailing());
        }
        if (node instanceof TableBlock) {
            return table(node);
        }
        return null;
    }

    // 표 → {head: [칸 segments...], rows: [[칸 segments...], ...]}
    private static Map<String, Object> table(Node tableBlock) {
        List<List<Map<String, Object>>> head = new ArrayList<>();
        List<List<List<Map<String, Object>>>> rows = new ArrayList<>();
        for (Node section = tableBlock.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                List<List<Map<String, Object>>> cells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                    cells.add(segments(cell));
                }
                if (section instanceof TableHead) {
                    head = cells;
                } else if (section instanceof TableBody) {
                    rows.add(cells);
                }
            }
        }
        Map<String, Object> block = block("table", "head", head);
        block.put("rows", rows);
        return block;
    }

    // 문단이 이미지 하나뿐이면(앞뒤 공백 제외) 그 이미지
    private static Image soleImage(Node paragraph) {
        Image found = null;
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image image && found == null) {
                found = image;
            } else if (!(child instanceof Text text && text.getLiteral().isBlank())
                    && !(child instanceof SoftLineBreak) && !(child instanceof HardLineBreak)) {
                return null;
            }
        }
        return found;
    }

    private static Map<String, Object> block(String type, String key, Object value) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", type);
        block.put(key, value);
        return block;
    }

    // 노드 안의 글자를 모양별 구간으로 나눈다: [{text, bold, italic, code, href}, ...]
    private static List<Map<String, Object>> segments(Node node) {
        List<Map<String, Object>> segments = new ArrayList<>();
        collect(node, new Style(false, false, null), segments);
        return segments;
    }

    private record Style(boolean bold, boolean italic, String href) {
    }

    private static void collect(Node node, Style style, List<Map<String, Object>> out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text text) {
                append(out, text.getLiteral(), style, false);
            } else if (child instanceof Code code) {
                append(out, code.getLiteral(), style, true);
            } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                append(out, " ", style, false);
            } else if (child instanceof StrongEmphasis) {
                collect(child, new Style(true, style.italic(), style.href()), out);
            } else if (child instanceof Emphasis) {
                collect(child, new Style(style.bold(), true, style.href()), out);
            } else if (child instanceof Link link) {
                collect(child, new Style(style.bold(), style.italic(), safeHref(link.getDestination())), out);
            } else if (child instanceof HtmlInline || child instanceof Image) {
                // 원본 HTML 과 문장 속 이미지는 내보내지 않는다(이미지는 한 줄에 따로 쓴다)
            } else {
                if (child instanceof Block && !out.isEmpty()) {
                    append(out, " ", new Style(false, false, null), false); // 인용·목록 안의 문단 사이
                }
                collect(child, style, out);
            }
        }
    }

    // 링크로 남길 주소. 외부는 http(s), 안은 글·시리즈·목록 주소만. 나머지(#앵커, javascript: 등)는 글자만 남긴다
    static String safeHref(String destination) {
        if (destination == null) {
            return null;
        }
        if (destination.startsWith("https://") || destination.startsWith("http://")) {
            return destination;
        }
        return INTERNAL_HREF.matcher(destination).matches() ? destination : null;
    }

    // 앞 구간과 모양이 같으면 이어 붙인다
    private static void append(List<Map<String, Object>> out, String text, Style style, boolean code) {
        if (text.isEmpty()) {
            return;
        }
        if (!out.isEmpty()) {
            Map<String, Object> last = out.get(out.size() - 1);
            if (last.get("bold").equals(style.bold()) && last.get("italic").equals(style.italic())
                    && last.get("code").equals(code) && Objects.equals(last.get("href"), style.href())) {
                last.put("text", last.get("text") + text);
                return;
            }
        }
        Map<String, Object> segment = new HashMap<>();
        segment.put("text", text);
        segment.put("bold", style.bold());
        segment.put("italic", style.italic());
        segment.put("code", code);
        segment.put("href", style.href());
        out.add(segment);
    }

    static String plainText(Node node) {
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> segment : segments(node)) {
            text.append(segment.get("text"));
        }
        return text.toString().strip();
    }
}
