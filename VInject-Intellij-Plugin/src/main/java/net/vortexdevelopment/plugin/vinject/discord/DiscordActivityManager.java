package net.vortexdevelopment.plugin.vinject.discord;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBusConnection;
import net.vortexdevelopment.plugin.vinject.discord.DiscordSettings;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordActivityManager implements FileEditorManagerListener, EditorFactoryListener {
    
    private static final Logger LOG = Logger.getInstance(DiscordActivityManager.class);
    private Project currentProject;
    private DiscordSettings settings;
    private final ScheduledExecutorService scheduler;
    private MessageBusConnection messageBusConnection;
    private String currentFileName = "";
    private String currentFileType = "";
    private boolean isActive = true;
    private long sessionStartTime;

    public DiscordActivityManager(Project project, DiscordSettings settings) {
        this.currentProject = project;
        this.settings = settings;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Discord-Activity-Manager");
            thread.setDaemon(true);
            return thread;
        });
        this.sessionStartTime = System.currentTimeMillis();
    }
    
    // Method to switch to a different project
    public void switchToProject(Project newProject) {
        if (this.currentProject != newProject) {
            System.out.println("🔄 Switching Discord RPC to project: " + newProject.getName());
            
            // Disconnect from old project
            if (messageBusConnection != null) {
                messageBusConnection.disconnect();
            }
            
            // Switch to new project
            this.currentProject = newProject;
            this.settings = DiscordSettings.getInstance(newProject);
            
            // Reconnect to new project
            setupProjectListeners();
            
            // Check for currently open files in the new project
            checkCurrentlyOpenFiles();
        }
    }

    public void startMonitoring() {
        if (!settings.isDiscordRpcEnabled()) {
            return;
        }

        setupProjectListeners();
        
        // Check for already opened files when starting monitoring
        checkCurrentlyOpenFiles();

        // Schedule periodic presence updates
        scheduler.scheduleAtFixedRate(this::updatePresence, 0, 15, TimeUnit.SECONDS);
        
        LOG.info("Discord activity monitoring started for project: " + currentProject.getName());
    }
    
    private void setupProjectListeners() {
        // Listen to file editor changes
        messageBusConnection = currentProject.getMessageBus().connect();
        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        
        // Listen to editor factory events
        EditorFactory.getInstance().addEditorFactoryListener(this, currentProject);
    }
    
    private void checkCurrentlyOpenFiles() {
        try {
            FileEditorManager editorManager = FileEditorManager.getInstance(currentProject);
            VirtualFile[] openFiles = editorManager.getOpenFiles();
            
            if (openFiles.length > 0) {
                // Get the selected/active file
                VirtualFile selectedFile = editorManager.getSelectedFiles().length > 0 
                    ? editorManager.getSelectedFiles()[0] 
                    : openFiles[0]; // Fallback to first open file
                
                LOG.info("Found currently open file: " + selectedFile.getName());
                updateCurrentFile(selectedFile);
            } else {
                LOG.info("No files currently open in project: " + currentProject.getName());
                // Update presence with no file info
                updatePresence();
            }
        } catch (Exception e) {
            LOG.warn("Failed to check currently open files", e);
            // Update presence anyway to show project info
            updatePresence();
        }
    }

    public void stopMonitoring() {
        if (messageBusConnection != null) {
            messageBusConnection.disconnect();
        }
        
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        if (file != null) {
            updateCurrentFile(file);
        }
    }

    @Override
    public void editorCreated(EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (file != null) {
            updateCurrentFile(file);
        }
    }

    @Override
    public void editorReleased(EditorFactoryEvent event) {
        // Handle editor closing if needed
    }

    private void updateCurrentFile(VirtualFile file) {
        String newFileName = file.getName();
        String newFileType = getFileType(file);
        
        if (!newFileName.equals(currentFileName) || !newFileType.equals(currentFileType)) {
            currentFileName = newFileName;
            currentFileType = newFileType;
            isActive = true;
            
            // Immediate presence update for file changes
            updatePresence();
        }
    }

    private void updatePresence() {
        if (!DiscordHook.isConnected() || !settings.isDiscordRpcEnabled()) {
            return;
        }

        try {
            DiscordPresenceBuilder presence = buildPresence();
            DiscordHook.updatePresence(presence);
        } catch (Exception e) {
            LOG.warn("Failed to update Discord presence", e);
        }
    }

    private DiscordPresenceBuilder buildPresence() {
        DiscordPresenceBuilder builder = new DiscordPresenceBuilder();

        // Set start timestamp if showing elapsed time
        if (settings.isShowElapsedTime()) {
            builder.setStartTimestamp(sessionStartTime);
        }

        // Build status lines
        String line1 = buildPrimaryStatus();
        String line2 = buildSecondaryStatus();

        builder.setLine1(line1);
        if (line2 != null && !line2.isEmpty()) {
            builder.setLine2(line2);
        }

        // Set images
        setImages(builder);

        return builder;
    }

    private String buildPrimaryStatus() {
        String action = isActive ? "Editing" : "Viewing";
        String projectName = currentProject.getName();
        
        // Check for custom line1 format with placeholders
        if (!settings.getCustomLine1Format().isEmpty()) {
            return settings.replacePlaceholders(settings.getCustomLine1Format(), 
                projectName, currentFileName, currentFileType, action);
        }
        
        // Check for custom details text (legacy)
        if (!settings.getCustomDetailsText().isEmpty()) {
            return settings.getCustomDetailsText();
        }
        
        // Check for legacy custom status text
        if (!settings.getCustomStatusText().isEmpty()) {
            return settings.getCustomStatusText();
        }

        // Default behavior
        if (currentFileName.isEmpty()) {
            return "Browsing code";
        }
        
        if (settings.isShowFileName()) {
            return action + " " + currentFileName;
        } else {
            return action + " a " + (currentFileType.isEmpty() ? "file" : currentFileType + " file");
        }
    }

    private String buildSecondaryStatus() {
        String action = isActive ? "Editing" : "Viewing";
        String projectName = currentProject.getName();
        
        // Check for custom line2 format with placeholders
        if (!settings.getCustomLine2Format().isEmpty()) {
            return settings.replacePlaceholders(settings.getCustomLine2Format(), 
                projectName, currentFileName, currentFileType, action);
        }
        
        // Check for custom state text (legacy)
        if (!settings.getCustomStateText().isEmpty()) {
            return settings.getCustomStateText();
        }
        
        // Default behavior
        StringBuilder status = new StringBuilder();

        if (settings.isShowProjectName()) {
            status.append("Project: ").append(projectName);
        }

        if (settings.isShowFileType() && !currentFileType.isEmpty()) {
            if (status.length() > 0) {
                status.append(" • ");
            }
            status.append(currentFileType.toUpperCase());
        }

        if (settings.isShowLineCount() && !currentFileName.isEmpty()) {
            // TODO: Add line count functionality
            // This would require tracking the current editor and document
        }

        return status.toString();
    }

    private void setImages(DiscordPresenceBuilder builder) {
        // Big image (IDE logo by default)
        String bigImage = settings.getCustomBigImage();
        if (bigImage.isEmpty()) {
            bigImage = "intellij-logo";
        }
        builder.setBigImage(bigImage);
        
        // Use custom big image text if available
        String bigImageText = settings.getCustomBigImageText();
        if (bigImageText.isEmpty()) {
            bigImageText = "IntelliJ IDEA";
        }
        builder.setBigImageText(bigImageText);

        // Small image (file type or activity)
        String smallImage = settings.getCustomSmallImage();
        if (smallImage.isEmpty()) {
            smallImage = getFileTypeImage(currentFileType);
        }
        builder.setSmallImage(smallImage);
        
        // Use custom small image text if available
        String smallImageText = settings.getCustomSmallImageText();
        if (smallImageText.isEmpty()) {
            smallImageText = getSmallImageText();
        }
        builder.setSmallImageText(smallImageText);
    }

    private String getFileTypeImage(String fileType) {
        if (currentFileName.isEmpty()) {
            return settings.getDefaultIdleImageKey();
        }
        return settings.getImageKeyForFileType(fileType);
    }

    private String getSmallImageText() {
        if (!currentFileType.isEmpty()) {
            return currentFileType.toUpperCase() + " file";
        }
        return currentFileName.isEmpty() ? "Idle" : "Editing";
    }

    private String getFileType(VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null) {
            return "";
        }

        return switch (extension.toLowerCase()) {
            case "java" -> "Java";
            case "kt" -> "Kotlin";
            case "xml" -> "XML";
            case "json" -> "JSON";
            case "yaml", "yml" -> "YAML";
            case "properties" -> "Properties";
            case "gradle" -> "Gradle";
            case "pom" -> "Maven";
            default -> extension.toUpperCase();
        };
    }

    public void setInactive() {
        this.isActive = false;
        updatePresence();
    }

    public void setActive() {
        this.isActive = true;
        updatePresence();
    }
} 