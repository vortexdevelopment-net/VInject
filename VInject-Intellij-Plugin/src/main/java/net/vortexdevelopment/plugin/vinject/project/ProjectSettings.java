package net.vortexdevelopment.plugin.vinject.project;

public class ProjectSettings {
    private String groupId = "com.example";
    private String artifactId = "myproject";
    private boolean includeApiModule = false;
    
    // Discord RPC Settings
    private boolean discordRpcEnabled = true;
    private String discordClientId = "1387043651288432781"; // Default client ID
    private boolean showFileName = true;
    private boolean showProjectName = true;
    private boolean showFileType = true;
    private boolean showLineCount = false;
    private boolean showElapsedTime = true;
    private String customBigImage = "";
    private String customSmallImage = "";
    private String customStatusText = "";

    // Basic project settings
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public boolean isIncludeApiModule() { return includeApiModule; }
    public void setIncludeApiModule(boolean includeApiModule) { this.includeApiModule = includeApiModule; }

    // Discord RPC settings
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
}

