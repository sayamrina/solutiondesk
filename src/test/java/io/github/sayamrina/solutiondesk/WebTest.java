package io.github.sayamrina.solutiondesk;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class WebTest {

    @Autowired MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"/", "/learn?learner=1", "/admin/import", "/support/tickets", "/support/tickets?view=all",
            "/support/tickets/SD-108", "/support/tickets/SD-105", "/support/tickets/new", "/support/logs", "/support/kb",
            "/support/kb/kb-qz-002", "/support/diagnostics", "/support/diagnostics?check=course-contents",
            "/support/diagnostics?check=course-visibility&email=s.richter@wendmoor.example&course_code=KWM-HY-201"})
    void pagesRender(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void diagnosticExplainsTheHiddenCourse() throws Exception {
        mvc.perform(get("/support/diagnostics?check=course-visibility&email=s.richter@wendmoor.example&course_code=KWM-HY-201"))
                // check the result cell, not the SQL text (which also contains the words)
                .andExpect(content().string(containsString("<td class=\"verdict-bad\">group mismatch</td>")))
                .andExpect(content().string(containsString("<td>Verwaltung</td>")));
    }

    @Test
    void errorPageShowsCodeAndRequestId() throws Exception {
        mvc.perform(get("/learn/4/courses/2"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("SD-CRS-001")))
                .andExpect(content().string(containsString("refid")));
    }

    @Test
    void expiredEnrollmentIsAConflict() throws Exception {
        mvc.perform(post("/learn/2/courses/4/quiz").param("q1", "1")).andExpect(status().isConflict())
                .andExpect(content().string(containsString("SD-QZ-002")));
    }

    @Test
    void importRejectsBrokenSampleAndOversizedUpload() throws Exception {
        mvc.perform(post("/admin/import/sample").param("name", "forklift-broken-reference.xml"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("SD-IMP-003")));
        mvc.perform(multipart("/admin/import/upload").file(new MockMultipartFile("file", "big.xml", "text/xml", new byte[300 * 1024])))
                .andExpect(content().string(containsString("SD-UPL-001")));
    }

    @Test
    void jiraExportIsCsv() throws Exception {
        mvc.perform(get("/support/tickets/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Issue Type,Summary,Description,Priority")))
                .andExpect(content().string(containsString("\"SD-108\"")));
    }

    @Test
    void everyPageShowsThePortfolioBanner() throws Exception {
        for (String url : new String[] {"/", "/support/tickets", "/learn?learner=1", "/learn/4/courses/2"}) {
            mvc.perform(get(url)).andExpect(content().string(containsString("developed by Amrina as part of a portfolio")))
                    .andExpect(content().string(containsString("target=\"_blank\"")));
        }
    }

    @Test
    void dashboardShowsWhatNeedsAttention() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString("Needs attention")))
                .andExpect(content().string(containsString("SD-108")))
                .andExpect(content().string(containsString("Response due")));
    }

    @Test
    void replyToCustomerCountsAsFirstResponse() throws Exception {
        mvc.perform(get("/support/tickets/SD-108")).andExpect(content().string(containsString("Response due")));
        mvc.perform(post("/support/tickets/SD-108/comment").param("visibility", "customer").param("text", "We are on it."))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/support/tickets/SD-108")).andExpect(content().string(containsString("Responded in time")))
                .andExpect(content().string(containsString("Reply to customer")));
    }

    @Test
    void internalNoteDoesNotCountAsResponse() throws Exception {
        mvc.perform(post("/support/tickets/SD-104/comment").param("text", "checking the group"));
        mvc.perform(get("/support/tickets/SD-104")).andExpect(content().string(containsString("Response due")));
    }

    @Test
    void validateOnlyDoesNotImport() throws Exception {
        String xml = new String(new org.springframework.core.io.ClassPathResource("samples/load-securing-basics.xml")
                .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("VLG-LS-101", "VLG-LS-999");
        mvc.perform(multipart("/admin/import/upload").file(new MockMultipartFile("file", "new.xml", "text/xml", xml.getBytes()))
                .param("mode", "validate")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/import")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("VLG-LS-999"))));
    }

    @Test
    void ticketFilters() throws Exception {
        mvc.perform(get("/support/tickets?view=all&q=SD-IMP-001")).andExpect(content().string(containsString("SD-108")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(">SD-103<"))));
    }

    @Test
    void unknownPagesAre404() throws Exception {
        mvc.perform(get("/support/tickets/SD-999")).andExpect(status().isNotFound());
    }
}
