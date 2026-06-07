package net.vortexdevelopment.vinject.di.scan;

import net.vortexdevelopment.vinject.annotation.yaml.YamlSerializer;
import net.vortexdevelopment.vinject.di.scan.fixtures.included.IncludedYamlSerializer;
import net.vortexdevelopment.vinject.di.scan.fixtures.root.IncludedScannerRoot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathScannerTest {

    @Test
    void scanAndFilterFindsYamlSerializersInIncludedPackages() {
        ClasspathScanner scanner = new ClasspathScanner(
                IncludedScannerRoot.class.getAnnotation(net.vortexdevelopment.vinject.annotation.component.Root.class),
                IncludedScannerRoot.class
        );

        assertThat(scanner.scanAndFilter(YamlSerializer.class, ignored -> true))
                .contains(IncludedYamlSerializer.class);
    }
}
