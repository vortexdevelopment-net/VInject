package net.vortexdevelopment.plugin.vinject.project;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.AbstractNewProjectWizardBuilder;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.openapi.roots.ModifiableRootModel;
import net.vortexdevelopment.plugin.vinject.utils.PluginIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SongodaModuleBuilder extends AbstractNewProjectWizardBuilder {

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
        return new ProjectWizardStep(context, ProjectMode.SONGODA);
    }

    @Override
    public @NotNull String getDescription() {
        return "Create a new Songoda plugin (powered by SongodaCore and VInject)";
    }

    @Override
    public @NotNull String getPresentableName() {
        return "Songoda Plugin";
    }

    @Override
    public @NotNull Icon getNodeIcon() {
        return PluginIcons.SONGODA_ICON;
    }
}
