package io.github.sayamrina.solutiondesk;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sayamrina.solutiondesk.logging.LogAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/** The sample traffic at startup produces real log events; the analyzer must group and trace them. */
@SpringBootTest
@DirtiesContext
class LogAnalyzerTest {

    @Autowired LogAnalyzer analyzer;

    @Test
    void groupsSampleTrafficByErrorCode() {
        var groups = analyzer.groups();
        assertThat(groups).extracting(LogAnalyzer.ErrorGroup::errorCode)
                .contains("SD-QZ-002", "SD-CRS-001", "SD-IMP-001", "SD-UPL-001", "SD-IMP-005");
        var expired = groups.stream().filter(g -> g.errorCode().equals("SD-QZ-002")).findFirst().orElseThrow();
        assertThat(expired.count()).isGreaterThanOrEqualTo(3);
        assertThat(expired.team()).isEqualTo("SUPPORT");
    }

    @Test
    void trailContainsTheErrorAndTheAccessLine() {
        String id = analyzer.groups().stream().filter(g -> g.errorCode().equals("SD-UPL-001")).findFirst().orElseThrow()
                .requestIds().get(0);
        var trail = analyzer.trail(id);
        assertThat(trail).hasSizeGreaterThanOrEqualTo(2);
        assertThat(trail).anyMatch(e -> e.field("errorCode").equals("SD-UPL-001") && e.field("team").equals("HOSTING"));
        assertThat(trail).anyMatch(e -> e.field("status").equals("413"));
    }
}
