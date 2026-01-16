package net.vortexdevelopment.vinject.di;

import net.vortexdevelopment.vinject.annotation.component.Component;
import net.vortexdevelopment.vinject.annotation.component.Element;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.testing.TestApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the @Element collection feature.
 */
class ElementCollectionTest {

    @Test
    void collectElementsResolvesDependenciesCorrectly() {
        try (TestApplicationContext context = TestApplicationContext.builder()
                .withRootClass(ElementTestRoot.class)
                .build()) {
            
            DependencyRepository container = context.getContainer();
            
            // Collect elements of type ITestElement
            // Extra args: "Extra Message", 99
            Collection<ITestElement> elements = container.collectElements(ITestElement.class, "Extra Message", 99);

            assertThat(elements.size() == 3).isTrue();
            
            assertThat(elements).hasSize(3);
            
            // Verify all have the managed service
            for (ITestElement element : elements) {
                assertThat(element.service()).isNotNull();
            }
            
            // Verify TestElement1 (Only managed service)
            TestElement1 e1 = findElement(elements, TestElement1.class);
            assertThat(e1).isNotNull();
            
            // Verify TestElement2 (Managed service + String + int)
            TestElement2 e2 = findElement(elements, TestElement2.class);
            assertThat(e2.extraString()).isEqualTo("Extra Message");
            assertThat(e2.extraInt()).isEqualTo(99);
            
            // Verify TestElement3 (Managed service + String)
            TestElement3 e3 = findElement(elements, TestElement3.class);
            assertThat(e3.extraString()).isEqualTo("Extra Message");
        }
    }

    private <T> T findElement(Collection<?> elements, Class<T> clazz) {
        return elements.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    @Root(packageName = "net.vortexdevelopment.vinject.di", createInstance = false)
    public static class ElementTestRoot {
    }

    @Component
    public static class ManagedService {
    }

    public interface ITestElement {
        ManagedService service();
    }

    @Element
    public record TestElement1(ManagedService service) implements ITestElement {
    }

    @Element
    public record TestElement2(ManagedService service, String extraString, int extraInt) implements ITestElement {
    }

    @Element
    public record TestElement3(ManagedService service, String extraString) implements ITestElement {
    }
}
