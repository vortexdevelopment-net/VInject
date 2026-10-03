package net.vortexdevelopment.plugin.vinject.project;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.openapi.roots.ModifiableRootModel;
import net.vortexdevelopment.plugin.vinject.utils.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class VInjectModuleBuilder extends AbstractNewProjectWizardBuilder {

    private WizardContext context;

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) {
        if (getModuleJdk() != null) {
            modifiableRootModel.setSdk(getModuleJdk());
        } else {
            modifiableRootModel.inheritSdk();
        }
    }

    @Override
    protected @NotNull NewProjectWizardStep createStep(@NotNull WizardContext wizardContext) {
        this.context = wizardContext;
        return new ProjectWizardStep(context);
    }

    @Override
    public @NotNull String getDescription() {
        return "Create a new VInject project (Minecraft plugin with Paper default / Spigot -Pspigot, or standalone Java app)";
    }

    @Override
    public @NotNull String getPresentableName() {
        return "VInject Project";
    }

    @Override
    public @NotNull Icon getNodeIcon() {
        return PluginIcons.PLUGIN_ICON;
    }
}
