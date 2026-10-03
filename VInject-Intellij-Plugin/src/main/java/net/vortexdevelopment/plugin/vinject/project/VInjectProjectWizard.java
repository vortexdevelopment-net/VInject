package net.vortexdevelopment.plugin.vinject.project;

import com.intellij.ide.wizard.GeneratorNewProjectWizard;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import net.vortexdevelopment.plugin.vinject.utils.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class VInjectProjectWizard implements GeneratorNewProjectWizard {

    @Override
    public @NotNull String getId() {
        return "vinject.project.generator";
    }

    @Override
    public @NotNull String getName() {
        return "VInject Project";
    }

    @Override
    public @NotNull String getDescription() {
        return "Create a new VInject project (Minecraft plugin with Paper default / Spigot -Pspigot, or standalone Java app)";
    }

    @Override
    public @NotNull Icon getIcon() {
        return PluginIcons.PLUGIN_ICON;
    }

    @Override
    public @NotNull NewProjectWizardStep createStep(@NotNull WizardContext context) {
        return new ProjectWizardStep(context);
    }
}
