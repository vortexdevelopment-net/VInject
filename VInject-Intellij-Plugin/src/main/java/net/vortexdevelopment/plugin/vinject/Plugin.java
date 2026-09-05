package net.vortexdevelopment.plugin.vinject;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;
import net.vortexdevelopment.plugin.vinject.discord.DiscordHook;
import net.vortexdevelopment.plugin.vinject.discord.DiscordActivityManager;
import net.vortexdevelopment.plugin.vinject.discord.DiscordSettings;
import net.vortexdevelopment.plugin.vinject.syntax.AnnotationChangeListener;
import net.vortexdevelopment.plugin.vinject.templates.TemplateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Plugin implements ProjectActivity, Disposable {

    private static DiscordActivityManager globalDiscordActivityManager;
    private static boolean discordInitialized = false;
    private AnnotationChangeListener annotationChangeListener;
    private Project project;

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        this.project = project;

        // Schedule scanning and template reload after indexing completes to ensure modules and library roots are available
        DumbService.getInstance(project).runWhenSmart(() -> {
            // Process files in background with progress indication to prevent UI freezes
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Scanning VInject annotations", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    ApplicationManager.getApplication().runReadAction((Computable<List<VirtualFile>>) () -> {
                        // Ensure templates from dependency jars are loaded on startup
                        TemplateManager.getInstance().reloadTemplates(project);
                        ClassDataManager.registerRegistryAnnotations(project);

                        indicator.setText("Collecting source files...");
                        List<VirtualFile> roots = new ArrayList<>();
                        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();

                        projectFileIndex.iterateContent(file -> {
                            if (indicator.isCanceled()) {
                                return false; // Stop iteration if cancelled
                            }
                            if (projectFileIndex.isInSource(file) && !file.getPath().contains("resources")
                                    && !file.getPath().contains("target")) {
                                roots.add(file);
                            }
                            return true; // Continue iteration
                        });

                        if (indicator.isCanceled()) {
                            return roots;
                        }

                        // Process files with progress indication to prevent UI freezes
                        indicator.setText("Processing " + roots.size() + " files...");
                        int batchSize = 50; // Check cancellation every 50 files
                        
                        for (int i = 0; i < roots.size(); i++) {
                            if (indicator.isCanceled()) {
                                break;
                            }
                            
                            VirtualFile virtualFile = roots.get(i);
                            indicator.setText2("Processing: " + virtualFile.getName());
                            indicator.setFraction((double) i / roots.size());
                            
                            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                            if (psiFile != null) {
                                ClassDataManager.processFileChange(psiFile);
                            }
                            
                            // Check cancellation periodically to allow UI responsiveness
                            if (i % batchSize == 0) {
                                indicator.checkCanceled();
                            }
                        }
                        
                        return roots;
                    });
                }
            });
        });

        this.annotationChangeListener = new AnnotationChangeListener();
        PsiManager.getInstance(project).addPsiTreeChangeListener(annotationChangeListener, this);

        // Initialize Discord RPC globally (only once)
        initializeDiscordRPCGlobal(project);

        // As a final fallback ensure templates are reloaded once more on the EDT in case
        // module/classpath roots become available slightly after startup.
        ApplicationManager.getApplication().invokeLater(() -> TemplateManager.getInstance().reloadTemplates(project));

        System.out.println("Plugin initialized successfully for project: " + project.getName());
        return null;
    }

    /**
     * Public method to trigger a rescan of the project files for VInject annotations.
     * This can be called from actions or other UI components to force re-processing.
     * @param proj Project to rescan
     */
    public static void rescanProject(Project proj) {
        if (proj == null)
            return;
        // Schedule the rescan to run when indexing (smart mode) is ready.
        DumbService.getInstance(proj).runWhenSmart(() -> {
            // Process files in background with progress indication to prevent UI freezes
            ProgressManager.getInstance().run(new Task.Backgroundable(proj, "Rescanning VInject annotations", true) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    ApplicationManager.getApplication().runReadAction(() -> {
                        indicator.setText("Collecting source files...");
                        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(proj).getFileIndex();
                        List<VirtualFile> roots = new ArrayList<>();
                        projectFileIndex.iterateContent(file -> {
                            if (indicator.isCanceled()) {
                                return false; // Stop iteration if cancelled
                            }
                            if (projectFileIndex.isInSource(file) && !projectFileIndex.isInTestSourceContent(file)
                                    && !file.getPath().contains("resources") && !file.getPath().contains("target")) {
                                roots.add(file);
                            }
                            return true;
                        });
                        
                        if (indicator.isCanceled()) {
                            return;
                        }
                        
                        // Reload templates from dependencies before rescanning project files to ensure templates
                        // provided by library jars are available. This method avoids duplicate registration.
                        indicator.setText("Reloading templates...");
                        TemplateManager.getInstance().reloadTemplates(proj);
                        ClassDataManager.registerRegistryAnnotations(proj);

                        // Process files with progress indication to prevent UI freezes
                        indicator.setText("Processing " + roots.size() + " files...");
                        int batchSize = 50; // Check cancellation every 50 files
                        
                        for (int i = 0; i < roots.size(); i++) {
                            if (indicator.isCanceled()) {
                                break;
                            }
                            
                            VirtualFile virtualFile = roots.get(i);
                            indicator.setText2("Processing: " + virtualFile.getName());
                            indicator.setFraction((double) i / roots.size());
                            
                            PsiFile psiFile = PsiManager.getInstance(proj).findFile(virtualFile);
                            if (psiFile != null) {
                                ClassDataManager.processFileChange(psiFile);
                            }
                            
                            // Check cancellation periodically to allow UI responsiveness
                            if (i % batchSize == 0) {
                                indicator.checkCanceled();
                            }
                        }
                    });
                }
            });
        });
    }

    public static void runWriteAction(Runnable runnable) {
        Application application = ApplicationManager.getApplication();
        if (!application.isWriteAccessAllowed())
            return;
        application.runWriteAction(runnable);
    }

    public static void runReadAction(Runnable runnable) {
        Application application = ApplicationManager.getApplication();
        if (!application.isReadAccessAllowed())
            return;
        application.runReadAction(runnable);
    }

    private void initializeDiscordRPCGlobal(Project project) {
        // Only initialize once globally
        if (discordInitialized) {
            // Just switch the activity manager to the new project
            if (globalDiscordActivityManager != null) {
                globalDiscordActivityManager.switchToProject(project);
            }
            return;
        }

        System.out.println("Starting Discord RPC initialization globally...");

        try {
            // Check if Discord RPC is enabled in any project's settings
            if (!DiscordHook.isEnabled(project)) {
                System.out.println("Discord RPC is disabled in settings, skipping initialization");
                return;
            }

            System.out.println("🔧 Initializing Discord RPC globally...");
            // Initialize Discord RPC globally
            DiscordHook.init(project);

            System.out.println("🔗 Attempting to connect to Discord...");
            // Connect to Discord
            DiscordHook.connect().thenRun(() -> {
                System.out.println("Discord connection successful! Starting global activity monitoring...");
                try {
                    // Start global activity monitoring
                    DiscordSettings settings = DiscordSettings.getInstance(project);
                    globalDiscordActivityManager = new DiscordActivityManager(project, settings);
                    globalDiscordActivityManager.startMonitoring();
                    discordInitialized = true;
                    System.out.println("Discord global activity monitoring started successfully!");
                } catch (Exception e) {
                    System.err.println("Failed to start Discord activity monitoring: " + e.getMessage());
                    e.printStackTrace();
                }
            }).exceptionally(throwable -> {
                System.err.println("Discord connection failed: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
        } catch (Exception e) {
            // Discord RPC is optional, don't fail plugin startup if it fails
            System.err.println("Failed to initialize Discord RPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        // Clean up Discord RPC globally (only when last project closes)
        if (globalDiscordActivityManager != null) {
            globalDiscordActivityManager.stopMonitoring();
            globalDiscordActivityManager = null;
        }
        DiscordHook.shutdown();
        discordInitialized = false;

        PsiManager.getInstance(project).removePsiTreeChangeListener(annotationChangeListener);
    }
}
