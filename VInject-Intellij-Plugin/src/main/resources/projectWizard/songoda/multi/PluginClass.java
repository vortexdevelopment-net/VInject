package $PACKAGE$;

import com.songoda.core.SongodaPlugin;
import lombok.Getter;
import net.vortexdevelopment.vinject.annotation.component.Root;
import net.vortexdevelopment.vinject.annotation.template.TemplateDependency;
import org.jetbrains.annotations.Nullable;

@Getter
@Root(
        packageName = "$PACKAGE$",
        createInstance = false,
        templateDependencies = {
                @TemplateDependency(
                        groupId = "com.songoda",
                        artifactId = "SongodaCore",
                        version = "latest"
                )
        }
)
public final class $CLASS_NAME$ extends SongodaPlugin {

    @Override
    protected void verifyLicense() {

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
