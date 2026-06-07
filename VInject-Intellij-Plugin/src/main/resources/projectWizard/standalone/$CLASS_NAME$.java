package $PACKAGE$;

import net.vortexdevelopment.vinject.VInject;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.template.TemplateDependency;

@Root(
        packageName = "$PACKAGE$",
        createInstance = true,
        templateDependencies = {
                @TemplateDependency(groupId = "net.vortexdevelopment", artifactId = "VInject-Core", version = "latest")
        }
)
public class $CLASS_NAME$ {

    public static void main(String[] args) {
        VInject.launch($CLASS_NAME$.class, args);
    }
}
