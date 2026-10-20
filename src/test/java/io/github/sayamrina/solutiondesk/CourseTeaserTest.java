package io.github.sayamrina.solutiondesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.sayamrina.solutiondesk.learning.Model.Course;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** Regression tests for SD-106: the catalog crashed for learners with a course without description. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class CourseTeaserTest {

    @Autowired MockMvc mvc;

    private static Course course(String description) {
        return new Course(1, "X-1", "Title", description, "published", "G", "1.2", Instant.now());
    }

    @Test
    void missingDescriptionGivesEmptyTeaser() {
        assertThat(course(null).teaser()).isEmpty();
        assertThat(course("   ").teaser()).isEmpty();
    }

    @Test
    void longDescriptionIsShortened() {
        String teaser = course("a".repeat(200)).teaser();
        assertThat(teaser).hasSize(140).endsWith("...");
    }

    @Test
    void catalogOfAdministrationStaffRenders() throws Exception {
        // learner 4 has KWM-DP-202 in the catalog, whose manifest has no description
        mvc.perform(get("/learn?learner=4")).andExpect(status().isOk());
    }
}
