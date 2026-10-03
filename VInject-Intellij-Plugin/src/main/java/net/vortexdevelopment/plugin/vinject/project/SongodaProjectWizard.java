package net.vortexdevelopment.plugin.vinject.project;

import com.intellij.ide.wizard.GeneratorNewProjectWizard;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import net.vortexdevelopment.plugin.vinject.utils.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SongodaProjectWizard implements GeneratorNewProjectWizard {

    @Override
    public @NotNull String getId() {
        return "songoda.plugin.generator";
    }

    @Override
    public @NotNull String getName() {
        return "Songoda Plugin";
    }

    @Override
    public @NotNull String getDescription() {
        return "Create a new Songoda plugin (powered by SongodaCore and VInject)";
    }

    @Override
    public @NotNull Icon getIcon() {
        return PluginIcons.SONGODA_ICON;
    }

    @Override
    public @NotNull NewProjectWizardStep createStep(@NotNull WizardContext context) {
        return new ProjectWizardStep(context, ProjectMode.SONGODA);
    }
}
