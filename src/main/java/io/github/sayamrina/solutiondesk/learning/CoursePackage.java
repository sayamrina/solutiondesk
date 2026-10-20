package io.github.sayamrina.solutiondesk.learning;

import java.util.List;

/** A course as read from an XML package, before it is stored. */
public record CoursePackage(String code, String version, String title, String description, String status,
                            String targetGroup, List<Lesson> lessons, List<Question> questions) {

    public record Lesson(String title, String href, String body) { }

    public record Question(String text, List<String> options, int correct) { }
}
