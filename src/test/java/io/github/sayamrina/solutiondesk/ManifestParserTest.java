package io.github.sayamrina.solutiondesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import io.github.sayamrina.solutiondesk.learning.CoursePackage;
import io.github.sayamrina.solutiondesk.learning.HtmlSanitizer;
import io.github.sayamrina.solutiondesk.learning.ManifestParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ManifestParserTest {

    private final ManifestParser parser = new ManifestParser();

    private static byte[] sample(String path) throws IOException {
        return new ClassPathResource("samples/" + path).getInputStream().readAllBytes();
    }

    private static ErrorCode codeOf(Runnable r) {
        try {
            r.run();
        } catch (SolutionDeskException e) {
            return e.errorCode();
        }
        throw new AssertionError("expected a SolutionDeskException");
    }

    @Test
    void readsAValidPackage() throws IOException {
        CoursePackage p = parser.parse(sample("load-securing-basics.xml"));
        assertThat(p.code()).isEqualTo("VLG-LS-101");
        assertThat(p.targetGroup()).isEqualTo("Disposition");
        assertThat(p.lessons()).hasSize(3);
        assertThat(p.questions()).hasSize(5);
        assertThat(p.questions().get(0).correct()).isEqualTo(1);
    }

    @Test
    void descriptionIsOptional() throws IOException {
        assertThat(parser.parse(sample("data-protection-admin.xml")).description()).isNull();
    }

    @Test
    void eachBrokenSampleHasItsOwnErrorCode() throws IOException {
        byte[] ref = sample("broken/forklift-broken-reference.xml");
        byte[] enc = sample("broken/dangerous-goods-windows-1252.xml");
        byte[] wf = sample("broken/fire-safety-not-well-formed.xml");
        byte[] title = sample("broken/night-shift-missing-title.xml");
        byte[] doctype = sample("broken/supplier-doctype.xml");
        assertThat(codeOf(() -> parser.parse(ref))).isEqualTo(ErrorCode.IMPORT_BROKEN_REFERENCE);
        assertThat(codeOf(() -> parser.parse(enc))).isEqualTo(ErrorCode.IMPORT_ENCODING);
        assertThat(codeOf(() -> parser.parse(wf))).isEqualTo(ErrorCode.IMPORT_NOT_WELL_FORMED);
        assertThat(codeOf(() -> parser.parse(title))).isEqualTo(ErrorCode.IMPORT_MISSING_FIELD);
        assertThat(codeOf(() -> parser.parse(doctype))).isEqualTo(ErrorCode.IMPORT_DOCTYPE);
    }

    @Test
    void encodingErrorNamesTheLine() throws IOException {
        byte[] enc = sample("broken/dangerous-goods-windows-1252.xml");
        assertThatThrownBy(() -> parser.parse(enc)).hasMessageContaining("line 4");
    }

    @Test
    void sameUmlautsInRealUtf8AreFine() throws IOException {
        String xml = new String(sample("broken/dangerous-goods-windows-1252.xml"), "windows-1252");
        CoursePackage p = parser.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertThat(p.title()).isEqualTo("Gefahrgut: Grundlagen für Fahrer");
    }

    @Test
    void lessonHtmlIsSanitised() {
        assertThat(HtmlSanitizer.clean("<p onclick=\"x()\">Hi <script>alert(1)</script><img src=x onerror=y><strong>ok</strong></p>"))
                .isEqualTo("<p>Hi <strong>ok</strong></p>");
        assertThat(HtmlSanitizer.clean("<p title=\"a>b\" onclick=z>t</p>")).doesNotContain("onclick=z>").startsWith("<p>");
        assertThat(HtmlSanitizer.clean("1 < 2 <img src=x onerror=y")).doesNotContain("<img");
    }
}
