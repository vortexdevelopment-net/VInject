package $PACKAGE$;

import net.vortexdevelopment.vinject.VInjectApplication;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.template.TemplateDependency;

@Root(
        packageName = "$PACKAGE$",
        createInstance = true,
        templateDependencies = {
                @TemplateDependency(groupId = "net.vortexdevelopment", artifactId = "VInject-Core", version = "latest")$HTTP_TEMPLATE_DEPENDENCY$
        }
)
public class $CLASS_NAME$ {

    public static void main(String[] args) {
        VInjectApplication.run($CLASS_NAME$.class, args);
    }
}
