package com.campus.qa.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campus.qa.dto.CrawlStartRequest;
import com.campus.qa.dto.CrawlStartResponse;
import com.campus.qa.entity.CrawlTask;
import com.campus.qa.entity.Question;
import com.campus.qa.mapper.CrawlTaskMapper;
import com.campus.qa.mapper.QuestionMapper;
import com.campus.qa.service.CrawlService;
import com.campus.qa.service.QuestionEmbeddingService;
import com.campus.qa.service.stats.CacheStatsService;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrawlServiceImpl implements CrawlService {

    private static final int DEFAULT_MAX_PAGES = 10;
    private static final int MAX_BATCH_PAGES = 30;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MIN_VALID_CONTENT_LENGTH = 40;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final List<String> MAIN_SELECTORS = List.of(
            "article",
            ".article",
            ".article-content",
            ".content",
            ".content-box",
            ".main-content",
            ".detail",
            ".detail-content",
            ".news_content",
            ".v_news_content",
            ".wp_articlecontent",
            ".Article_Content",
            ".TRS_Editor",
            "#vsb_content"
    );
    private static final List<String> NOISE_MARKERS = List.of(
            "首页", "联系我们", "新闻动态", "通知公告", "查看更多", "上一篇", "下一篇",
            "版权", "版权所有", "ICP备", "附件下载", "网站首页", "返回顶部"
    );
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern BROKEN_CHAR_PATTERN = Pattern.compile("[\\uFFFD]");

    private final CrawlTaskMapper crawlTaskMapper;
    private final QuestionMapper questionMapper;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final CacheStatsService cacheStatsService;
    private final HttpClient httpClient;

    public CrawlServiceImpl(CrawlTaskMapper crawlTaskMapper,
                            QuestionMapper questionMapper,
                            QuestionEmbeddingService questionEmbeddingService,
                            CacheStatsService cacheStatsService) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.questionMapper = questionMapper;
        this.questionEmbeddingService = questionEmbeddingService;
        this.cacheStatsService = cacheStatsService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public CrawlStartResponse startCrawl(CrawlStartRequest request) {
        CrawlTask task = new CrawlTask();
        task.setTaskName(request.getTaskName().trim());
        task.setTargetUrl(request.getTargetUrl().trim());
        task.setStatus("running");
        task.setTotalFound(0);
        task.setTotalInserted(0);
        task.setStartTime(LocalDateTime.now());
        crawlTaskMapper.insert(task);

        try {
            CrawlSummary summary = isBatchMode(request)
                    ? crawlListPage(task.getTargetUrl(), request.getCategory(), resolveMaxPages(request))
                    : crawlSinglePage(task.getTargetUrl(), request.getCategory());

            task.setStatus("success");
            task.setTotalFound(summary.totalFound());
            task.setTotalInserted(summary.totalInserted());
            task.setEndTime(LocalDateTime.now());
            crawlTaskMapper.updateById(task);
            if (summary.totalInserted() > 0) {
                cacheStatsService.clearSearchCache();
            }

            return CrawlStartResponse.builder()
                    .taskId(task.getId())
                    .status(task.getStatus())
                    .totalFound(summary.totalFound())
                    .totalInserted(summary.totalInserted())
                    .message(summary.message())
                    .build();
        } catch (Exception ex) {
            task.setStatus("failed");
            task.setEndTime(LocalDateTime.now());
            task.setErrorMessage(shortMsg(ex.getMessage()));
            crawlTaskMapper.updateById(task);

            return CrawlStartResponse.builder()
                    .taskId(task.getId())
                    .status(task.getStatus())
                    .totalFound(0)
                    .totalInserted(0)
                    .message("crawl failed: " + shortMsg(ex.getMessage()))
                    .build();
        }
    }

    @Override
    public List<CrawlTask> listTasks() {
        QueryWrapper<CrawlTask> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        wrapper.last("LIMIT 50");
        return crawlTaskMapper.selectList(wrapper);
    }

    private CrawlSummary crawlSinglePage(String url, String category) throws Exception {
        UpsertResult result = upsertPage(url, normalizeCategory(category));
        String message = result.insertedOrUpdated() > 0
                ? "single page crawl completed" + buildFallbackNote(result.fallbackUsed())
                : "page skipped because content was invalid" + buildFallbackNote(result.fallbackUsed());
        return new CrawlSummary(1, result.insertedOrUpdated(), message);
    }

    private CrawlSummary crawlListPage(String listUrl, String category, int maxPages) throws Exception {
        FetchResult listResult = fetchDocument(listUrl);
        Document listDoc = listResult.document();
        List<String> links = collectArticleLinks(listDoc, listUrl, maxPages);
        int affected = 0;
        int fallbackCount = listResult.fallbackUsed() ? 1 : 0;
        int invalidCount = 0;
        for (String link : links) {
            try {
                UpsertResult result = upsertPage(link, normalizeCategory(category));
                affected += result.insertedOrUpdated();
                if (result.fallbackUsed()) {
                    fallbackCount++;
                }
                if (!result.validContent()) {
                    invalidCount++;
                }
            } catch (Exception ignore) {
                // Skip bad pages and continue batch crawl.
            }
        }
        String message = "batch crawl completed, scanned " + links.size() + " pages"
                + (fallbackCount > 0 ? ", http fallback used " + fallbackCount + " times" : "")
                + (invalidCount > 0 ? ", skipped " + invalidCount + " low-quality pages" : "");
        return new CrawlSummary(links.size(), affected, message);
    }

    private UpsertResult upsertPage(String url, String category) throws Exception {
        FetchResult fetchResult = fetchDocument(url);
        Document doc = fetchResult.document();
        String title = safeTrim(doc.title());
        String content = extractMainText(doc);
        if (!StringUtils.hasText(title) || !isValidContent(content)) {
            return new UpsertResult(0, fetchResult.fallbackUsed(), false);
        }

        QueryWrapper<Question> wrapper = new QueryWrapper<>();
        wrapper.eq("question", title);
        Question existing = questionMapper.selectOne(wrapper);
        if (existing != null) {
            if (!shouldReplaceExisting(existing.getAnswer(), content)) {
                return new UpsertResult(0, fetchResult.fallbackUsed(), true);
            }
            existing.setAnswer(content);
            existing.setCategory(category);
            existing.setSource("crawl");
            questionMapper.updateById(existing);
            questionEmbeddingService.upsertEmbedding(existing);
            return new UpsertResult(1, fetchResult.fallbackUsed(), true);
        }

        Question question = new Question();
        question.setQuestion(title);
        question.setAnswer(content);
        question.setCategory(category);
        question.setSource("crawl");
        question.setHitCount(0L);
        question.setCreateTime(LocalDateTime.now());
        questionMapper.insert(question);
        questionEmbeddingService.upsertEmbedding(question);
        return new UpsertResult(1, fetchResult.fallbackUsed(), true);
    }

    private static boolean shouldReplaceExisting(String oldContent, String newContent) {
        if (!isValidContent(newContent)) {
            return false;
        }
        if (!isValidContent(oldContent)) {
            return true;
        }
        String oldClean = normalizeText(oldContent);
        String newClean = normalizeText(newContent);
        return newClean.length() > oldClean.length() + 20;
    }

    private static boolean isValidContent(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String clean = normalizeText(content);
        if (clean.length() < MIN_VALID_CONTENT_LENGTH) {
            return false;
        }
        int shortSegmentCount = 0;
        for (String item : clean.split("[。！？；;\\n\\r]+")) {
            String part = item == null ? "" : item.trim();
            if (!part.isEmpty() && part.length() <= 8) {
                shortSegmentCount++;
            }
        }
        return shortSegmentCount < 8;
    }

    private static List<String> collectArticleLinks(Document doc, String baseUrl, int maxPages) {
        URI baseUri = URI.create(baseUrl);
        String host = baseUri.getHost();
        Set<String> links = new LinkedHashSet<>();
        for (Element element : doc.select("a[href]")) {
            String link = safeTrim(element.absUrl("href"));
            String text = safeTrim(element.text());
            if (!StringUtils.hasText(link) || !StringUtils.hasText(text)) {
                continue;
            }
            if (!isCrawlableArticleLink(link, host, baseUrl, text)) {
                continue;
            }
            links.add(link);
            if (links.size() >= maxPages) {
                break;
            }
        }
        return List.copyOf(links);
    }

    private static boolean isCrawlableArticleLink(String link, String host, String baseUrl, String text) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return false;
        }
        if (link.equals(baseUrl)) {
            return false;
        }
        if (link.startsWith("javascript:") || link.startsWith("mailto:")) {
            return false;
        }
        if (text.length() < 2 || text.length() > 30) {
            return false;
        }
        URI uri = URI.create(link);
        if (host != null && !host.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        if (path.endsWith("/")) {
            return false;
        }
        return path.endsWith(".htm")
                || path.endsWith(".html")
                || path.contains("/content/")
                || path.contains("/info/");
    }

    private FetchResult fetchDocument(String url) throws Exception {
        try {
            return fetchDocumentInternal(url);
        } catch (SSLException ex) {
            String fallbackUrl = tryDowngradeToHttp(url);
            if (fallbackUrl == null) {
                throw new IOException("https handshake failed and no http fallback is available: " + shortMsg(ex.getMessage()), ex);
            }
            try {
                FetchResult fallback = fetchDocumentInternal(fallbackUrl);
                return new FetchResult(fallback.document(), fallback.actualUrl(), true);
            } catch (Exception fallbackEx) {
                throw new IOException("https handshake failed; http fallback also failed: " + shortMsg(fallbackEx.getMessage()), fallbackEx);
            }
        }
    }

    private FetchResult fetchDocumentInternal(String url) throws IOException, InterruptedException, GeneralSecurityException {
        String currentUrl = url;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int code = response.statusCode();
            if (isRedirect(code)) {
                String location = response.headers().firstValue("Location").orElse("");
                if (!StringUtils.hasText(location)) {
                    throw new IOException("http status " + code + " for " + currentUrl + " without redirect location");
                }
                currentUrl = URI.create(currentUrl).resolve(location).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                throw new IOException("http status " + code + " for " + currentUrl);
            }

            byte[] body = response.body();
            String finalUrl = response.uri() == null ? currentUrl : response.uri().toString();
            String charset = resolveCharset(response);
            try (InputStream inputStream = new ByteArrayInputStream(body)) {
                return new FetchResult(Jsoup.parse(inputStream, charset, finalUrl), finalUrl, false);
            }
        }
        throw new IOException("too many redirects for " + url);
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String resolveCharset(HttpResponse<byte[]> response) {
        Optional<String> header = response.headers().firstValue("Content-Type");
        if (header.isEmpty()) {
            return null;
        }
        String contentType = header.get();
        int index = contentType.toLowerCase().indexOf("charset=");
        if (index < 0) {
            return null;
        }
        String charset = contentType.substring(index + 8).trim();
        int semicolon = charset.indexOf(';');
        if (semicolon >= 0) {
            charset = charset.substring(0, semicolon).trim();
        }
        return charset.replace("\"", "");
    }

    private static String tryDowngradeToHttp(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("https://")) {
            return null;
        }
        return "http://" + url.substring("https://".length());
    }

    private static String extractMainText(Document doc) {
        if (doc.body() == null) {
            return "";
        }

        Document cloned = doc.clone();
        cloned.select("script,style,nav,header,footer,noscript,iframe,form").remove();
        cloned.select(".nav,.header,.footer,.breadcrumb,.menu,.search,.copyright,.share,.pagination").remove();

        String best = extractFromSelectors(cloned);
        if (!StringUtils.hasText(best) || best.length() < 80) {
            best = normalizeText(cloned.body().text());
        }
        if (!isValidContent(best)) {
            return "";
        }
        if (best.length() > MAX_CONTENT_LENGTH) {
            return best.substring(0, MAX_CONTENT_LENGTH);
        }
        return best;
    }

    private static String extractFromSelectors(Document doc) {
        List<String> candidates = new ArrayList<>();
        for (String selector : MAIN_SELECTORS) {
            for (Element element : doc.select(selector)) {
                String text = normalizeText(element.text());
                if (isValidContent(text)) {
                    candidates.add(text);
                }
            }
        }
        candidates.sort((a, b) -> Integer.compare(scoreCandidate(b), scoreCandidate(a)));
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    private static int scoreCandidate(String text) {
        int score = text.length();
        for (String marker : NOISE_MARKERS) {
            if (text.contains(marker)) {
                score -= 120;
            }
        }
        int digitCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                digitCount++;
            }
        }
        if (digitCount > 30) {
            score -= 40;
        }
        return score;
    }

    private static String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = BROKEN_CHAR_PATTERN.matcher(raw).replaceAll("");
        value = SPACE_PATTERN.matcher(value).replaceAll(" ");
        value = value.replace('\u00A0', ' ');
        value = value.trim();

        List<String> kept = new ArrayList<>();
        for (String sentence : value.split("[。！？；;\\n\\r]+")) {
            String item = sentence == null ? "" : sentence.trim();
            if (item.length() < 4 || looksLikeNoise(item)) {
                continue;
            }
            kept.add(item);
        }
        return String.join("。", kept);
    }

    private static boolean looksLikeNoise(String sentence) {
        for (String marker : NOISE_MARKERS) {
            if (sentence.contains(marker)) {
                return true;
            }
        }
        int digitCount = 0;
        for (int i = 0; i < sentence.length(); i++) {
            if (Character.isDigit(sentence.charAt(i))) {
                digitCount++;
            }
        }
        return sentence.length() > 100 && digitCount > 18;
    }

    private static boolean isBatchMode(CrawlStartRequest request) {
        return Boolean.TRUE.equals(request.getBatchMode());
    }

    private static int resolveMaxPages(CrawlStartRequest request) {
        Integer value = request.getMaxPages();
        if (value == null || value <= 0) {
            return DEFAULT_MAX_PAGES;
        }
        return Math.min(value, MAX_BATCH_PAGES);
    }

    private static String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : "网站信息";
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String shortMsg(String msg) {
        String m = msg == null ? "unknown" : msg;
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    private static String buildFallbackNote(boolean fallbackUsed) {
        return fallbackUsed ? ", http fallback used" : "";
    }

    private record CrawlSummary(int totalFound, int totalInserted, String message) {
    }

    private record FetchResult(Document document, String actualUrl, boolean fallbackUsed) {
    }

    private record UpsertResult(int insertedOrUpdated, boolean fallbackUsed, boolean validContent) {
    }
}
