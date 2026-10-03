package net.vortexdevelopment.plugin.vinject.discord;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(
    name = "DiscordRPCSettings",
    storages = @Storage("discordRPC.xml")
)
public final class DiscordSettings implements PersistentStateComponent<DiscordSettings> {
    
    // Discord RPC Settings
    public boolean discordRpcEnabled = true;
    public String discordClientId = "1387043651288432781"; // Default client ID
    public boolean showFileName = true;
    public boolean showProjectName = true;
    public boolean showFileType = true;
    public boolean showLineCount = false;
    public boolean showElapsedTime = true;
    public String customBigImage = "";
    public String customSmallImage = "";
    public String customStatusText = "";
    public String customBigImageText = "VInject Plugin for IntelliJ IDEA";
    public String customSmallImageText = "Coding with VInject";
    public String customDetailsText = "";
    public String customStateText = "";
    public String customApplicationName = "Working on Projects";
    
    // Placeholder support for dynamic text
    public String customLine1Format = "{action} {filename}";
    public String customLine2Format = "Project: {project} • {filetype}";
    
    // Image mappings for file types
    public String javaImageKey = "java-icon";
    public String kotlinImageKey = "kotlin-icon";
    public String xmlImageKey = "xml-icon";
    public String jsonImageKey = "json-icon";
    public String yamlImageKey = "yaml-icon";
    public String propertiesImageKey = "properties-icon";
    public String gradleImageKey = "gradle-icon";
    public String mavenImageKey = "maven-icon";
    public String defaultEditingImageKey = "editing-file-icon";
    public String defaultIdleImageKey = "idle-icon";
    public String intellijLogoImageKey = "intellij-logo";

    public static DiscordSettings getInstance(@NotNull Project project) {
        return project.getService(DiscordSettings.class);
    }

    @Override
    public @Nullable DiscordSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DiscordSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and setters for all fields
    public boolean isDiscordRpcEnabled() { return discordRpcEnabled; }
    public void setDiscordRpcEnabled(boolean discordRpcEnabled) { this.discordRpcEnabled = discordRpcEnabled; }

    public String getDiscordClientId() { return discordClientId; }
    public void setDiscordClientId(String discordClientId) { this.discordClientId = discordClientId; }

    public boolean isShowFileName() { return showFileName; }
    public void setShowFileName(boolean showFileName) { this.showFileName = showFileName; }

    public boolean isShowProjectName() { return showProjectName; }
    public void setShowProjectName(boolean showProjectName) { this.showProjectName = showProjectName; }

    public boolean isShowFileType() { return showFileType; }
    public void setShowFileType(boolean showFileType) { this.showFileType = showFileType; }

    public boolean isShowLineCount() { return showLineCount; }
    public void setShowLineCount(boolean showLineCount) { this.showLineCount = showLineCount; }

    public boolean isShowElapsedTime() { return showElapsedTime; }
    public void setShowElapsedTime(boolean showElapsedTime) { this.showElapsedTime = showElapsedTime; }

    public String getCustomBigImage() { return customBigImage; }
    public void setCustomBigImage(String customBigImage) { this.customBigImage = customBigImage; }

    public String getCustomSmallImage() { return customSmallImage; }
    public void setCustomSmallImage(String customSmallImage) { this.customSmallImage = customSmallImage; }

    public String getCustomStatusText() { return customStatusText; }
    public void setCustomStatusText(String customStatusText) { this.customStatusText = customStatusText; }

    public String getCustomBigImageText() { return customBigImageText; }
    public void setCustomBigImageText(String customBigImageText) { this.customBigImageText = customBigImageText; }

    public String getCustomSmallImageText() { return customSmallImageText; }
    public void setCustomSmallImageText(String customSmallImageText) { this.customSmallImageText = customSmallImageText; }

    public String getCustomDetailsText() { return customDetailsText; }
    public void setCustomDetailsText(String customDetailsText) { this.customDetailsText = customDetailsText; }

    public String getCustomStateText() { return customStateText; }
    public void setCustomStateText(String customStateText) { this.customStateText = customStateText; }

    public String getCustomApplicationName() { return customApplicationName; }
    public void setCustomApplicationName(String customApplicationName) { this.customApplicationName = customApplicationName; }

    public String getCustomLine1Format() { return customLine1Format; }
    public void setCustomLine1Format(String customLine1Format) { this.customLine1Format = customLine1Format; }

    public String getCustomLine2Format() { return customLine2Format; }
    public void setCustomLine2Format(String customLine2Format) { this.customLine2Format = customLine2Format; }

    // Image key getters and setters
    public String getJavaImageKey() { return javaImageKey; }
    public void setJavaImageKey(String javaImageKey) { this.javaImageKey = javaImageKey; }

    public String getKotlinImageKey() { return kotlinImageKey; }
    public void setKotlinImageKey(String kotlinImageKey) { this.kotlinImageKey = kotlinImageKey; }

    public String getXmlImageKey() { return xmlImageKey; }
    public void setXmlImageKey(String xmlImageKey) { this.xmlImageKey = xmlImageKey; }

    public String getJsonImageKey() { return jsonImageKey; }
    public void setJsonImageKey(String jsonImageKey) { this.jsonImageKey = jsonImageKey; }

    public String getYamlImageKey() { return yamlImageKey; }
    public void setYamlImageKey(String yamlImageKey) { this.yamlImageKey = yamlImageKey; }

    public String getPropertiesImageKey() { return propertiesImageKey; }
    public void setPropertiesImageKey(String propertiesImageKey) { this.propertiesImageKey = propertiesImageKey; }

    public String getGradleImageKey() { return gradleImageKey; }
    public void setGradleImageKey(String gradleImageKey) { this.gradleImageKey = gradleImageKey; }

    public String getMavenImageKey() { return mavenImageKey; }
    public void setMavenImageKey(String mavenImageKey) { this.mavenImageKey = mavenImageKey; }

    public String getDefaultEditingImageKey() { return defaultEditingImageKey; }
    public void setDefaultEditingImageKey(String defaultEditingImageKey) { this.defaultEditingImageKey = defaultEditingImageKey; }

    public String getDefaultIdleImageKey() { return defaultIdleImageKey; }
    public void setDefaultIdleImageKey(String defaultIdleImageKey) { this.defaultIdleImageKey = defaultIdleImageKey; }

    public String getIntellijLogoImageKey() { return intellijLogoImageKey; }
    public void setIntellijLogoImageKey(String intellijLogoImageKey) { this.intellijLogoImageKey = intellijLogoImageKey; }

    // Utility method to get image key for file type
    public String getImageKeyForFileType(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "java" -> javaImageKey;
            case "kotlin" -> kotlinImageKey;
            case "xml" -> xmlImageKey;
            case "json" -> jsonImageKey;
            case "yaml", "yml" -> yamlImageKey;
            case "properties" -> propertiesImageKey;
            case "gradle" -> gradleImageKey;
            case "maven" -> mavenImageKey;
            default -> defaultEditingImageKey;
        };
    }
    
    // Utility method to replace placeholders in text
    public String replacePlaceholders(String template, String projectName, String fileName, String fileType, String action) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        
        return template
            .replace("{project}", projectName != null ? projectName : "")
            .replace("{filename}", fileName != null ? fileName : "")
            .replace("{filetype}", fileType != null ? fileType : "")
            .replace("{action}", action != null ? action : "")
            .replace("{time}", java.time.LocalTime.now().toString().substring(0, 5)); // HH:mm format
    }
} 