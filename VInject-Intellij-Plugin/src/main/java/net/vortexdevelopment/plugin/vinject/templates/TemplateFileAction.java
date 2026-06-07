package net.vortexdevelopment.plugin.vinject.templates;

import com.intellij.ide.actions.CreateFileFromTemplateAction;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import net.vortexdevelopment.plugin.vinject.Plugin;
import net.vortexdevelopment.plugin.vinject.utils.PluginIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class TemplateFileAction extends CreateFileFromTemplateAction {

    public TemplateFileAction() {
        super("VInject Component", "Create a new VInject component", PluginIcons.PLUGIN_ICON);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);

        Project project = e.getProject();

        // Check if the action is being invoked from our custom place
        String place = e.getPlace();
        boolean isCorrectPlace = "VInjectActionGroup".equals(place) ||
                "ProjectViewPopup".equals(place);

        // Only show if both conditions are met
        e.getPresentation().setVisible(isCorrectPlace);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle("New VInject Component");

        // Add all templates from our TemplateManager
        TemplateManager templateManager = TemplateManager.getInstance();

        // Convert to list and sort alphabetically
        List<String> sortedTemplates = new ArrayList<>(templateManager.getTemplates());
        Collections.sort(sortedTemplates);

        // Add sorted templates to builder
        for (String templateName : sortedTemplates) {
            String kindName = templateName.replace("Template", "");
            builder.addKind(kindName, PluginIcons.PLUGIN_ICON, templateName);
        }
    }

    @Override
    protected PsiFile createFile(String name, String templateName, PsiDirectory dir) {
        System.out.println("Creating file with name: " + name + " and template: " + templateName);

        UnregisteredTemplate unregisteredTemplate = TemplateManager.getInstance().getTemplate(templateName);
        if (unregisteredTemplate == null) {
            return null;
        }

        // Remove .java extension if user added it
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }

        // Set up properties for template replacement
        Properties props = FileTemplateManager.getInstance(dir.getProject()).getDefaultProperties();
        props.setProperty("NAME", name);

        // Get package name from directory
        String packageName = "";
        try {
            packageName = com.intellij.psi.JavaDirectoryService.getInstance()
                    .getPackage(dir).getQualifiedName();
        } catch (Exception e) {
            // Fallback if package determination fails
        }
        props.setProperty("PACKAGE_NAME", packageName);

        try {
            // Get the template content with variables replaced
            String content = unregisteredTemplate.getText(props);
            // Normalize line separators to match the platform's line separator
            content = content.replaceAll("\\r\\n|\\r", "\n");

            // Create the file with proper name and content
            String fileName = name + ".java";
            PsiFile file = dir.createFile(fileName);
            String finalContent = content;
            Plugin.runWriteAction(() -> {
                com.intellij.openapi.editor.Document document =
                        com.intellij.psi.PsiDocumentManager.getInstance(dir.getProject())
                                .getDocument(file);
                if (document != null) {
                    document.setText(finalContent);
                    com.intellij.psi.PsiDocumentManager.getInstance(dir.getProject())
                            .commitDocument(document);
                }
            });

            // Open the file in editor
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(dir.getProject())
                    .openFile(file.getVirtualFile(), true);

            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected @NlsContexts.Command String getActionName(PsiDirectory directory, @NonNls @NotNull String newName, @NonNls String templateName) {
        return "Creating VInject " + templateName.replace("Template", "") + " " + newName;
    }
}