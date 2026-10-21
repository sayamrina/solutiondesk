package io.github.sayamrina.solutiondesk.learning;

import io.github.sayamrina.solutiondesk.common.ErrorCode;
import io.github.sayamrina.solutiondesk.common.SolutionDeskException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CourseImportService {

    public static final int MAX_PACKAGE_BYTES = 256 * 1024;
    private static final Logger log = LoggerFactory.getLogger("solutiondesk.import");

    private final ManifestParser parser;
    private final LearningRepository repository;
    private final Clock clock;

    public CourseImportService(ManifestParser parser, LearningRepository repository, Clock clock) {
        this.parser = parser;
        this.repository = repository;
        this.clock = clock;
    }

    public long importPackage(String fileName, byte[] xml) {
        CoursePackage pkg = validate(fileName, xml);
        long id = repository.insertCourse(pkg, clock.instant());
        log.info("course imported: {} ({}) with {} lessons and {} questions from {}", pkg.code(), pkg.title(),
                pkg.lessons().size(), pkg.questions().size(), fileName);
        return id;
    }

    /** Runs every import check without storing anything (dry run). */
    public CoursePackage validate(String fileName, byte[] xml) {
        if (xml.length > MAX_PACKAGE_BYTES) {
            throw new SolutionDeskException(ErrorCode.UPLOAD_TOO_LARGE,
                    "package '" + fileName + "' has " + xml.length / 1024 + " KB, limit is " + MAX_PACKAGE_BYTES / 1024 + " KB",
                    xml.length / 1024, MAX_PACKAGE_BYTES / 1024);
        }
        CoursePackage pkg;
        try {
            pkg = parser.parse(xml);
        } catch (SolutionDeskException e) {
            throw new SolutionDeskException(e.errorCode(), fileName + ": " + e.getMessage(), e.args());
        }
        if (repository.courseCodeExists(pkg.code())) {
            throw new SolutionDeskException(ErrorCode.IMPORT_DUPLICATE,
                    fileName + ": a course with identifier '" + pkg.code() + "' already exists", pkg.code());
        }
        return pkg;
    }
}
