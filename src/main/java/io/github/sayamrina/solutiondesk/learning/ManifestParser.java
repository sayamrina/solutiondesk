package io.github.sayamrina.solutiondesk.learning;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXParseException;

/**
 * Reads a course package manifest (a simplified, SCORM-like XML format) and checks it the way
 * support would: encoding, well-formedness, required fields and references between items and resources.
 * DOCTYPE declarations are rejected so that external entities (XXE) can never be resolved.
 */
@Component
public class ManifestParser {

    private static final Pattern DECLARED_ENCODING = Pattern.compile("<\\?xml[^>]*encoding=[\"']([^\"']+)[\"']");

    public CoursePackage parse(byte[] xml) {
        checkEncoding(xml);
        Document doc = read(xml);
        Element root = doc.getDocumentElement();
        if (!"manifest".equals(root.getTagName())) {
            throw missing("root element <manifest>");
        }
        String code = required(root.getAttribute("identifier"), "attribute identifier on <manifest>");
        String version = root.getAttribute("version");

        Element metadata = child(root, "metadata");
        if (metadata == null) throw missing("element <metadata>");
        String title = required(text(metadata, "title"), "element <metadata>/<title>");
        String description = text(metadata, "description");
        String targetGroup = required(text(metadata, "targetGroup"), "element <metadata>/<targetGroup>");
        String status = text(metadata, "status").isEmpty() ? "draft" : text(metadata, "status");

        Map<String, Element> resources = new HashMap<>();
        Element resourcesEl = child(root, "resources");
        if (resourcesEl == null) throw missing("element <resources>");
        for (Element r : children(resourcesEl, "resource")) {
            String id = required(r.getAttribute("identifier"), "attribute identifier on <resource>");
            resources.put(id, r);
        }

        Element organization = child(root, "organization");
        if (organization == null) throw missing("element <organization>");
        List<CoursePackage.Lesson> lessons = new ArrayList<>();
        for (Element item : children(organization, "item")) {
            String ref = item.getAttribute("identifierref");
            Element resource = resources.get(ref);
            if (resource == null) {
                throw new SolutionDeskException(ErrorCode.IMPORT_BROKEN_REFERENCE,
                        "item '" + item.getAttribute("title") + "' points to resource '" + ref + "', which does not exist", ref);
            }
            String href = resource.getAttribute("href");
            if (href.isBlank()) {
                throw new SolutionDeskException(ErrorCode.IMPORT_BROKEN_REFERENCE,
                        "resource '" + ref + "' has no href", ref);
            }
            lessons.add(new CoursePackage.Lesson(required(item.getAttribute("title"), "attribute title on <item>"),
                    href, HtmlSanitizer.clean(text(resource, "content"))));
        }
        if (lessons.isEmpty()) throw missing("at least one <item> in <organization>");

        List<CoursePackage.Question> questions = new ArrayList<>();
        Element assessment = child(root, "assessment");
        if (assessment != null) {
            for (Element q : children(assessment, "question")) {
                List<String> options = children(q, "option").stream().map(o -> o.getTextContent().trim()).toList();
                int correct = Integer.parseInt(required(q.getAttribute("correct"), "attribute correct on <question>"));
                if (correct < 0 || correct >= options.size()) {
                    throw missing("a valid correct option index on question '" + text(q, "text") + "'");
                }
                questions.add(new CoursePackage.Question(required(text(q, "text"), "element <question>/<text>"), options, correct));
            }
        }
        return new CoursePackage(code, version, title, description.isEmpty() ? null : description, status, targetGroup,
                lessons, questions);
    }

    /** A file saved as Windows-1252 but declared as UTF-8 is the classic cause of broken umlauts. */
    private void checkEncoding(byte[] xml) {
        String head = new String(xml, 0, Math.min(xml.length, 200), StandardCharsets.US_ASCII);
        Matcher m = DECLARED_ENCODING.matcher(head);
        String declared = m.find() ? m.group(1).toUpperCase() : "UTF-8";
        if (!declared.equals("UTF-8")) return;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(xml));
        } catch (CharacterCodingException e) {
            int line = 1;
            int offset = firstInvalidUtf8(xml);
            for (int i = 0; i < offset; i++) if (xml[i] == '\n') line++;
            throw new SolutionDeskException(ErrorCode.IMPORT_ENCODING,
                    "file is declared as UTF-8 but contains invalid UTF-8 bytes in line " + line
                            + " (probably saved as Windows-1252/ISO-8859-1)", line);
        }
    }

    private static int firstInvalidUtf8(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            int c = b[i] & 0xFF;
            int need = c < 0x80 ? 0 : (c >> 5) == 0x6 ? 1 : (c >> 4) == 0xE ? 2 : (c >> 3) == 0x1E ? 3 : -1;
            if (need < 0) return i;
            for (int k = 1; k <= need; k++) {
                if (i + k >= b.length || (b[i + k] & 0xC0) != 0x80) return i;
            }
            i += need;
        }
        return b.length;
    }

    private Document read(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder builder = f.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXParseException e) {
            if (e.getMessage() != null && e.getMessage().contains("DOCTYPE")) {
                throw new SolutionDeskException(ErrorCode.IMPORT_DOCTYPE, "DOCTYPE declarations are not allowed in course packages");
            }
            throw new SolutionDeskException(ErrorCode.IMPORT_NOT_WELL_FORMED,
                    "XML is not well-formed in line " + e.getLineNumber() + ", column " + e.getColumnNumber() + ": " + e.getMessage(),
                    e.getLineNumber(), e.getColumnNumber());
        } catch (ParserConfigurationException | java.io.IOException | org.xml.sax.SAXException e) {
            throw new SolutionDeskException(ErrorCode.IMPORT_NOT_WELL_FORMED, "XML could not be read: " + e.getMessage(), 0, 0);
        }
    }

    private static SolutionDeskException missing(String what) {
        return new SolutionDeskException(ErrorCode.IMPORT_MISSING_FIELD, "missing " + what, what);
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) throw missing(what);
        return value.trim();
    }

    private static Element child(Element parent, String name) {
        List<Element> list = children(parent, name);
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals(name)) out.add(e);
        }
        return out;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? "" : e.getTextContent().trim();
    }
}
