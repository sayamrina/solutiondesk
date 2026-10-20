package io.github.sayamrina.solutiondesk.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${solutiondesk.timezone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
