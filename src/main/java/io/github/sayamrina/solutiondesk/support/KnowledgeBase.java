package io.github.sayamrina.solutiondesk.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/** Solution articles, one HTML file per error code, loaded from classpath:kb/. */
@Service
public class KnowledgeBase {

    public record Article(String slug, String code, String title, String summary, String html) { }

    private static final Pattern META = Pattern.compile("<article data-code=\"([^\"]+)\" data-title=\"([^\"]+)\" data-summary=\"([^\"]+)\">");
    private final List<Article> articles = new ArrayList<>();

    public KnowledgeBase() throws IOException {
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:kb/*.html")) {
            try (InputStream in = r.getInputStream()) {
                String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = META.matcher(html);
                if (!m.find()) throw new IllegalStateException("kb article without metadata: " + r.getFilename());
                String slug = r.getFilename().replace(".html", "");
                articles.add(new Article(slug, m.group(1), m.group(2), m.group(3), html));
            }
        }
        articles.sort(Comparator.comparing(Article::code));
    }

    public List<Article> all() { return articles; }

    public Optional<Article> bySlug(String slug) {
        return articles.stream().filter(a -> a.slug().equals(slug)).findFirst();
    }

    public List<Article> search(String query) {
        if (query == null || query.isBlank()) return articles;
        String q = query.toLowerCase();
        return articles.stream()
                .filter(a -> (a.code() + " " + a.title() + " " + a.html().replaceAll("<[^>]+>", " ")).toLowerCase().contains(q))
                .toList();
    }
}
