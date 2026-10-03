package net.vortexdevelopment.plugin.vinject.templates;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateParseException;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class UnregisteredTemplate implements FileTemplate {

    private final String templateName;
    private final String content;
    private final String extension;

    public UnregisteredTemplate(String templateName, String content, String extension) {
        this.templateName = templateName;
        this.content = content;
        this.extension = extension;
    }

    @Override
    public @NotNull @NlsSafe String getName() {
        return templateName;
    }

    @Override
    public void setName(@NotNull String name) {

    }

    @Override
    public boolean isTemplateOfType(@NotNull FileType fType) {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public @NotNull @Nls String getDescription() {
        return "";
    }

    @Override
    public @NotNull String getText() {
        return content;
    }

    @Override
    public void setText(String text) {

    }

    @Override
    public @NotNull String getText(@NotNull Map attributes) throws IOException {
        return content;
    }

    @Override
    public @NotNull String getText(@NotNull Properties attributes) throws IOException {
        //Replace ${PLACEHOLDER} with the value from the properties
        String text = content;
        for (String key : attributes.stringPropertyNames()) {
            text = text.replace("${" + key + "}", attributes.getProperty(key));
        }
        return text;
    }

    @Override
    public @NotNull String getExtension() {
        return extension;
    }

    @Override
    public void setExtension(@NotNull String extension) {

    }

    @Override
    public boolean isReformatCode() {
        return false;
    }

    @Override
    public void setReformatCode(boolean reformat) {

    }

    @Override
    public boolean isLiveTemplateEnabled() {
        return false;
    }

    @Override
    public void setLiveTemplateEnabled(boolean value) {

    }

    @Override
    public @NotNull FileTemplate clone() {
        return new UnregisteredTemplate(templateName, content, extension);
    }

    @Override
    public String @NotNull [] getUnsetAttributes(@NotNull Properties properties, @NotNull Project project) throws FileTemplateParseException {
        return new String[0];
    }
}
