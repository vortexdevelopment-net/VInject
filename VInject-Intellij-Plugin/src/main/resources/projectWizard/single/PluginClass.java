package $PACKAGE$;

import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.template.TemplateDependency;
import net.vortexdevelopment.vortexcore.PluginVerificationException;
import net.vortexdevelopment.vortexcore.VortexPlugin;
import net.vortexdevelopment.vortexcore.compatibility.KnownServerVersions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Root(
        packageName = "$PACKAGE$",
        createInstance = false,
        templateDependencies = {
                @TemplateDependency(groupId = "net.vortexdevelopment", artifactId = "VortexCore", version = "latest")
        },
        loadProperties = false
)
public final class $CLASS_NAME$ extends VortexPlugin {

    @Override
    protected void verifyLicense() throws PluginVerificationException {

    }

    @Override
    protected @NotNull KnownServerVersions getMinimumServerVersion() {
        return KnownServerVersions.V1_18_2;
    }

    @Override
    public void onPreComponentLoad() {

    }

    @Override
    public void onPluginLoad() {

    }

    @Override
    protected void onPluginEnable() {
    }

    @Override
    protected void onPluginDisable() {

    }

    @Override
    protected @Nullable Integer getBstatsPluginId() {
        return null;
    }
}
