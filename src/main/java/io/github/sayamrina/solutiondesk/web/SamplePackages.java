package io.github.sayamrina.solutiondesk.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Course packages shipped with the demo: valid ones and broken ones that reproduce real incidents. */
@Component
public class SamplePackages {

    public record SamplePackage(String name, boolean broken, byte[] bytes) {
        public int sizeKb() { return Math.max(1, bytes.length / 1024); }
    }

    private final List<SamplePackage> packages = new ArrayList<>();

    public SamplePackages() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            for (Resource r : resolver.getResources("classpath:samples/*.xml")) packages.add(load(r, false));
            for (Resource r : resolver.getResources("classpath:samples/broken/*.xml")) packages.add(load(r, true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        packages.sort(Comparator.comparing(SamplePackage::broken).thenComparing(SamplePackage::name));
    }

    private static SamplePackage load(Resource r, boolean broken) throws IOException {
        try (InputStream in = r.getInputStream()) {
            return new SamplePackage(r.getFilename(), broken, in.readAllBytes());
        }
    }

    public List<SamplePackage> all() { return packages; }

    public SamplePackage get(String name) {
        return packages.stream().filter(p -> p.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown sample " + name));
    }
}
