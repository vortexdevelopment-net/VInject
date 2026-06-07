package net.vortexdevelopment.plugin.vinject.templates;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import net.vortexdevelopment.plugin.vinject.Plugin;
import net.vortexdevelopment.plugin.vinject.container.ClassDataManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

public class TemplateManager {

    // Built-in templates
    private static final String[] DEFAULT_TEMPLATES = {
            "ComponentTemplate",
            "ServiceTemplate",
            "EntityTemplate",
            "RepositoryTemplate",
            "RegistryTemplate",
    };

    private static final TemplateManager instance = new TemplateManager();
    private final Map<String, UnregisteredTemplate> templates = new LinkedHashMap<>();
    private final Map<String, Set<String>> fileTemplates = new ConcurrentHashMap<>();

    public static TemplateManager getInstance() {
        return instance;
    }

    private TemplateManager() {
        for (String template : DEFAULT_TEMPLATES) {
            templates.put(template, new UnregisteredTemplate(template, getDefaultTemplateContent(template), "java"));
        }
    }

    private String getDefaultTemplateContent(String templateName) {
        //Read from /fileTemplates folder inside the jar. So resource path is "fileTemplates/ComponentTemplate.java.ft"
        try {
            return new String(Plugin.class.getResourceAsStream("/fileTemplates/" + templateName + ".java.ft").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not find template at: fileTemplates/" + templateName + ".java.ft");
            throw new RuntimeException(e);
        }
    }


    /**
     * Find a dependency in the project's libraries
     */
    private VirtualFile[] findDependencyInLibraries(Project project, String groupId, String artifactId, String version) {
        List<VirtualFile> results = new ArrayList<>();

        // Clean up quoted values if present
        groupId = groupId.replaceAll("\"", "");
        artifactId = artifactId.replaceAll("\"", "");
        version = version.replaceAll("\"", "");

        // Convert Maven coordinates to typical JAR name patterns
        String jarNamePattern1 = artifactId + "-" + version;
        String jarNamePattern2 = groupId + "." + artifactId;

        // Search in all project libraries
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            // Use getOrderEntries().getClassesRoots() instead of getFiles()
            VirtualFile[] roots = rootManager.orderEntries().getAllLibrariesAndSdkClassesRoots();

            for (VirtualFile libraryRoot : roots) {
                String path = libraryRoot.getPath();
                // Look for matches by filename
                if (path.contains(jarNamePattern1) || path.contains(jarNamePattern2)) {
                    results.add(libraryRoot);
                }
            }
        }

        return results.toArray(new VirtualFile[0]);
    }

    /**
     * Scan a directory for template files
     */
    private void scanTemplatesDirectory(Project project, VirtualFile directory) {
        // Process all children
        for (VirtualFile file : directory.getChildren()) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanTemplatesDirectory(project, file);
            } else if (file.getName().endsWith(".java.ft") || file.getName().endsWith(".ft")) {
                // Found a template file
                try {
                    String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
                    String templateName = file.getName();
                    System.out.println("Original filename: " + file.getName());


                    String extension = templateName.substring(templateName.indexOf('.'));

                    //if only .ft is preset set to txt
                    if (extension.equals(".ft")) {
                        extension = ".txt";
                    } else {
                        extension = extension.replace(".ft", "");
                    }


                    // Register the template
                    //FileTemplate template = FileTemplateManager.getInstance(project).addTemplate(templateName, extension);
//                    template.setText(content);
//                    templates.add(templateName);
//                    System.out.println("Registering template: " + template.getName());
                    UnregisteredTemplate template = new UnregisteredTemplate(templateName, content, extension);
                    templates.put(templateName, template);
                } catch (IOException e) {
                    // Failed to read template content
                }
            }
        }
    }

    /**
     * Register a template from a resource file
     *
     * @param resourcePath Path to the template file (relative to resource roots)
     * @param templateName Name to register the template as
     * @param annotationFqcn FQN of the annotation this template is for
     * @param project Current project
     */
    public void registerTemplateFromFile(String resourcePath, String templateName, String annotationFqcn, Project project) {
        if (project == null) return;

        // Clean up quoted strings from annotation values
        resourcePath = resourcePath.replaceAll("\"", "");
        templateName = templateName.replaceAll("\"", "");
        annotationFqcn = annotationFqcn.replaceAll("\"", "");

        // Track template registration
        fileTemplates.computeIfAbsent(resourcePath, k -> new HashSet<>()).add(annotationFqcn);

        // Find the template file across all resource roots
        VirtualFile templateFile = findResourceFile(project, resourcePath);

        if (templateFile != null) {
            try {
                // Read template content
                String content = new String(templateFile.contentsToByteArray(), StandardCharsets.UTF_8);

                // Determine file extension
                String fileName = templateFile.getName();
                String extension = "java";
                if (fileName.contains(".")) {
                    extension = fileName.substring(fileName.lastIndexOf('.') + 1);
                    if (extension.equals("ft")) {
                        // Special case for .ft files - use previous extension if available
                        String nameWithoutFt = fileName.substring(0, fileName.length() - 3);
                        if (nameWithoutFt.contains(".")) {
                            extension = nameWithoutFt.substring(nameWithoutFt.lastIndexOf('.') + 1);
                        } else {
                            extension = "txt";
                        }
                    }
                }

                // Register template
                UnregisteredTemplate template = new UnregisteredTemplate(templateName, content, extension);
                templates.put(templateName, template);

                // Register the annotation as a component annotation
                ClassDataManager.registerComponentAnnotation(annotationFqcn);

                System.out.println("Registered template " + templateName + " for annotation " + annotationFqcn);
            } catch (IOException e) {
                System.out.println("Error reading template file: " + e.getMessage());
            }
        } else {
            System.out.println("Template file not found: " + resourcePath);
        }
    }

    /**
     * Find a resource file in any resource directory across all modules
     *
     * @param project Current project
     * @param resourcePath Relative path to the resource file
     * @return VirtualFile if found, null otherwise
     */
    private VirtualFile findResourceFile(Project project, String resourcePath) {
        // Remove leading slash if present
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }

        // Search in all modules
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);

            // Look in resource roots (marked as resources in module settings)
            for (VirtualFile resourceRoot : rootManager.getSourceRoots(true)) {
                VirtualFile file = resourceRoot.findFileByRelativePath(resourcePath);
                if (file != null && file.exists()) {
                    return file;
                }
            }

            // Also try content roots
            for (VirtualFile contentRoot : rootManager.getContentRoots()) {
                VirtualFile file = contentRoot.findFileByRelativePath(resourcePath);
                if (file != null && file.exists()) {
                    return file;
                }
            }
        }

        // Try class roots (including dependencies)
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            for (VirtualFile classRoot : rootManager.orderEntries().getAllLibrariesAndSdkClassesRoots()) {
                VirtualFile file = classRoot.findFileByRelativePath(resourcePath);
                if (file != null && file.exists()) {
                    return file;
                }
            }
        }

        return null;
    }

    /**
     * Remove templates that were registered from a specific file
     *
     * @param filePath           Path of the file to check
     * @param currentAnnotations Current set of annotation FQNs still in the file
     * @param project            Current project
     */
    public void cleanupTemplates(String filePath, Set<String> currentAnnotations, Project project) {
        Set<String> previousTemplates = fileTemplates.getOrDefault(filePath, Collections.emptySet());

        // Find templates that were previously registered but are no longer present
        Set<String> toRemove = new HashSet<>(previousTemplates);
        toRemove.removeAll(currentAnnotations);

        // Remove each template that's no longer present
        for (String annotationFqcn : toRemove) {
            String templateName = annotationFqcn.substring(annotationFqcn.lastIndexOf('.') + 1) + "Template";
            templates.remove(templateName);

            // Remove from FileTemplateManager
            FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
            FileTemplate template = fileTemplateManager.getTemplate(templateName);
            if (template != null) {
                fileTemplateManager.removeTemplate(template);
            }

            // Unregister the component annotation
            ClassDataManager.unregisterComponentAnnotation(annotationFqcn);
        }

        // Update tracking map
        if (currentAnnotations.isEmpty()) {
            fileTemplates.remove(filePath);
        } else {
            fileTemplates.put(filePath, new HashSet<>(currentAnnotations));
        }
    }

    public String getAnnotationStringValue(PsiAnnotation annotation, String attributeName) {
        PsiElement value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression) {
            Object literalValue = ((PsiLiteralExpression) value).getValue();
            return literalValue instanceof String ? (String) literalValue : null;
        }
        return null;
    }

    public Set<String> getTemplates() {
        return templates.keySet();
    }

    public void registerTemplateFromJar(String absolutePath) {
        // Normalize possible URI or jar-root paths to a local filesystem path
        String jarPath = absolutePath;
        try {
            if (jarPath.startsWith("file:")) {
                try {
                    jarPath = new java.io.File(new java.net.URI(jarPath)).getAbsolutePath();
                } catch (Exception ignore) {
                    // Fallback: strip file: prefix
                    jarPath = jarPath.replaceFirst("file:", "");
                }
            }
            // If path still contains the jar internal marker, convert to regular jar path
            if (jarPath.contains(".jar!/")) {
                jarPath = jarPath.replace(".jar!/", ".jar");
            }

            try (JarFile jarFile = new JarFile(jarPath)) {
                jarFile.stream()
                        .forEach(entry -> {
                            if (entry.getName().startsWith("vinject/templates/") && entry.getName().endsWith(".ft")) {
                                try {
                                    String content = new String(jarFile.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);

                                    // Get the filename without path
                                    String fileName = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);

                                    // Remove the .ft extension
                                    fileName = fileName.substring(0, fileName.length() - 3);

                                    // Parse name and extension properly
                                    String templateName;
                                    String extension;

                                    int dotIndex = fileName.lastIndexOf('.');
                                    if (dotIndex > 0) {
                                        // Template has an extension like "SomeTemplate.java"
                                        templateName = fileName.substring(0, dotIndex);
                                        extension = fileName.substring(dotIndex + 1);
                                    } else {
                                        // No extension in the template name
                                        templateName = fileName;
                                        extension = "txt";
                                    }

                                    // Prevent duplicate registration: don't overwrite existing template
                                    if (!templates.containsKey(templateName)) {
                                        UnregisteredTemplate template = new UnregisteredTemplate(templateName, content, extension);
                                        templates.put(templateName, template);
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
            }
        } catch (IOException e) {
            // Failed to open jar; ignore silently to avoid breaking startup
        }
    }

    /**
     * Reload templates from all dependencies currently present on the project's classpath.
     * This will register templates found under vinject/templates/* inside dependency jars
     * but will avoid duplicating templates already registered.
     *
     * @param project Current project to inspect
     */
    public void reloadTemplates(Project project) {
        if (project == null) return;

        // Iterate over modules and their class roots to find dependency jars
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            for (VirtualFile classRoot : rootManager.orderEntries().getAllLibrariesAndSdkClassesRoots()) {
                String path = classRoot.getPath();
                try {
                    String jarPath = path;
                    if (path.contains(".jar!")) {
                        jarPath = path.replace(".jar!/", ".jar");
                    }
                    registerTemplateFromJar(jarPath);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public UnregisteredTemplate getTemplate(String templateName) {
        return templates.get(templateName);
    }
}