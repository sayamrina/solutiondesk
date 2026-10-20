package io.github.sayamrina.solutiondesk.learning;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lesson content comes from customer uploads and is shown as HTML, so only a few formatting tags survive,
 * always without attributes. Everything else (script, img, event handlers, links) is removed.
 */
public final class HtmlSanitizer {

    private static final Set<String> ALLOWED = Set.of("p", "strong", "em", "b", "i", "ul", "ol", "li", "br", "code");
    private static final Pattern TAG = Pattern.compile("<\\s*(/?)\\s*([a-zA-Z0-9]+)[^>]*>");
    private static final Pattern DANGEROUS_BLOCK = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1\\s*>");

    private HtmlSanitizer() { }

    public static String clean(String html) {
        if (html == null) return null;
        String withoutBlocks = DANGEROUS_BLOCK.matcher(html).replaceAll("");
        Matcher m = TAG.matcher(withoutBlocks);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(2).toLowerCase();
            m.appendReplacement(out, ALLOWED.contains(name) ? "<" + m.group(1) + name + ">" : "");
        }
        m.appendTail(out);
        // escape every remaining angle bracket, then restore only the normalised allowed tags
        String escaped = out.toString().replace("<", "&lt;").replace(">", "&gt;");
        return escaped.replaceAll("&lt;(/?)(" + String.join("|", ALLOWED) + ")&gt;", "<$1$2>");
    }
}
