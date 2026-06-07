package net.vortexdevelopment.plugin.vinject.project;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.NewProjectWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.observable.properties.GraphProperty;
import com.intellij.openapi.observable.properties.PropertyGraph;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextField;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.dsl.builder.AlignX;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.ui.dsl.builder.Row;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.vortexdevelopment.plugin.vinject.Plugin;
import net.vortexdevelopment.plugin.vinject.version.MavenVersionResolver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class ProjectWizardStep implements NewProjectWizardStep {
    public static final Key<String> GROUP_ID_KEY = Key.create("GROUP_ID");
    public static final Key<String> ARTIFACT_ID_KEY = Key.create("ARTIFACT_ID");
    public static final Key<String> PROJECT_TYPE_KEY = Key.create("PROJECT_TYPE");
    public static final Key<Boolean> INCLUDE_HTTP_KEY = Key.create("INCLUDE_HTTP");
    public static final Key<Boolean> INCLUDE_SECURITY_KEY = Key.create("INCLUDE_SECURITY");
    public static final Key<Boolean> INCLUDE_CONTEXT_KEY = Key.create("INCLUDE_CONTEXT");

    // Properties for the fields
    private final GraphProperty<String> locationProperty;
    private final GraphProperty<String> groupIdProperty;
    private final GraphProperty<String> artifactIdProperty;

    private JdkComboBox jdkComboBox;
    private final ProjectSdksModel sdksModel = new ProjectSdksModel();
    private WizardContext context;
    JBTextField locationField = new JBTextField("", 25);

    private final Logger logger = LoggerFactory.getLogger(ProjectWizardStep.class);

    public ProjectWizardStep(@NotNull WizardContext context) {
        this.context = context;
        // Initialize properties
        groupIdProperty = getPropertyGraph().property("com.example");
        artifactIdProperty = getPropertyGraph().property("untitled");
        locationProperty = getPropertyGraph().property(System.getProperty("user.home"));

        // Add Default Project Type
        context.putUserData(PROJECT_TYPE_KEY, "Minecraft Plugin (Multi-Module)");
        context.putUserData(INCLUDE_HTTP_KEY, false);
        context.putUserData(INCLUDE_SECURITY_KEY, false);
        context.putUserData(INCLUDE_CONTEXT_KEY, false);

        try {
            sdksModel.reset(context.getProject());
        } catch (Exception e) {
            logger.error("Error initializing SDK model", e);
        }

        jdkComboBox = new JdkComboBox(
                context.getProject(),
                sdksModel,
                sdkTypeId -> sdkTypeId.getName().contains("Java"), // Lambda instead of method reference
                null,
                null,
                null
        );


        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String lastPath = propertiesComponent.getValue("vinject.last.project.location", System.getProperty("user.home"));
        locationProperty.set(lastPath);
        context.setProjectName(artifactIdProperty.get());

        // Sync properties with context
        updateContext();
    }

    @Override
    public void setupUI(@NotNull Panel builder) {
        builder.group("Project", true, new Function1<Panel, Unit>() {

            @Override
            public Unit invoke(Panel panel) {

                panel.row("Group ID:", new Function1<Row, Unit>() {
                    @Override
                    public Unit invoke(Row row) {
                        JBTextField textField = new JBTextField("com.example", 20);
                        row.cell(textField).onChanged(jbTextField -> {
                            // Validate Group ID
                            String groupId = jbTextField.getText();
                            if (!isValidGroupId(groupId)) {
                                // Handle invalid Group ID (you could show a message or disable a button)
                                // Example: set a red border on the text field (or show a message)
                                jbTextField.setBackground(JBColor.RED);
                            } else {
                                jbTextField.setBackground(JBColor.WHITE); // reset to default
                            }
                            groupIdProperty.set(groupId);
                            context.putUserData(GROUP_ID_KEY, groupId);
                            return Unit.INSTANCE;
                        });
                        return Unit.INSTANCE;
                    }
                });

                panel.row("Artifact ID:", new Function1<Row, Unit>() {
                    @Override
                    public Unit invoke(Row row) {
                        JBTextField textField = new JBTextField("untitled", 20);
                        row.cell(textField).onChanged(jbTextField -> {
                            // Validate Artifact ID
                            String artifactId = jbTextField.getText();
                            if (!isValidArtifactId(artifactId)) {
                                // Handle invalid Artifact ID
                                jbTextField.setBackground(JBColor.RED);
                            } else {
                                jbTextField.setBackground(JBColor.WHITE); // reset to default
                            }
                            artifactIdProperty.set(artifactId);
                            context.putUserData(ARTIFACT_ID_KEY, artifactId);
                            updateContext();
                            return Unit.INSTANCE;
                        });
                        return Unit.INSTANCE;
                    }
                });

                // Add this combo box to your setupUI method:
                panel.row("Project Type:", (Row row) -> {
                    com.intellij.openapi.ui.ComboBox<String> typeComboBox = new com.intellij.openapi.ui.ComboBox<>(new String[]{
                            "Minecraft Plugin (Single Module)",
                            "Minecraft Plugin (Multi-Module)",
                            "Standalone Application"
                    });
                    typeComboBox.setSelectedItem("Minecraft Plugin (Multi-Module)");
                    row.cell(typeComboBox)
                            .onChanged(selected -> {
                                context.putUserData(PROJECT_TYPE_KEY, (String) selected.getSelectedItem());
                                return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                });
                panel.row("Include HTTP Support:", (Row row) -> {
                    JCheckBox checkBox = new JCheckBox();
                    checkBox.setSelected(false);
                    row.cell(checkBox)
                            .onChanged(selected -> {
                                context.putUserData(INCLUDE_HTTP_KEY, selected.isSelected());
                                return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                });

                panel.row("Include Security (CORS/CSRF):", (Row rowSecurity) -> {
                    JCheckBox securityCheckBox = new JCheckBox();
                    securityCheckBox.setSelected(false);
                    rowSecurity.cell(securityCheckBox)
                            .onChanged(selected -> {
                                context.putUserData(INCLUDE_SECURITY_KEY, selected.isSelected());
                                return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                });

                panel.row("Include Request Context (Scoped Values):", (Row rowContext) -> {
                    JCheckBox contextCheckBox = new JCheckBox();
                    contextCheckBox.setSelected(false);
                    rowContext.cell(contextCheckBox)
                            .onChanged(selected -> {
                                context.putUserData(INCLUDE_CONTEXT_KEY, selected.isSelected());
                                return Unit.INSTANCE;
                            });
                    return Unit.INSTANCE;
                });

                //Location
                panel.row("Location:", new Function1<Row, Unit>() {
                    @Override
                    public Unit invoke(Row row) {
                        addLocationField(panel);
                        return Unit.INSTANCE;
                    }
                });

                panel.row("JDK:", row -> {
                    // Use a proper SDK filter function instead of method reference
                    row.cell(jdkComboBox);
                    return Unit.INSTANCE;
                });

                return Unit.INSTANCE;
            }
        });
    }

    private void addLocationField(Panel panel) {
        panel.row("Location:", row -> {
            // Create descriptor for single folder selection
            FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("Select Project Location");
            descriptor.setDescription("Choose directory for your new project");

            PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
            String lastPath = propertiesComponent.getValue("vinject.last.project.location", System.getProperty("user.home"));
            locationProperty.set(lastPath);

            // Create text field with browse button
            TextFieldWithBrowseButton locationChooser = new TextFieldWithBrowseButton(locationField);
            locationChooser.addBrowseFolderListener(
                    context.getProject(),
                    descriptor,
                    TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
            );

            // Update property when text field changes
            locationField.getDocument().addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    String path = locationField.getText();
                    // Update UI immediately (EDT-safe)
                    locationField.setText(path);

                    // Update model in write-safe context with proper modality
                    ApplicationManager.getApplication().invokeLater(() -> {
                        ApplicationManager.getApplication().runWriteAction(() -> {
                            locationProperty.set(path);
                            propertiesComponent.setValue("vinject.last.project.location", path);
                            updateContext();
                        });
                    }, ModalityState.defaultModalityState());
                }
            });

            // Initial value
            locationField.setText(locationProperty.get());

            // Add to row and configure
            row.cell(locationChooser)
                    .align(AlignX.FILL);

            return Unit.INSTANCE;
        });
    }

    private boolean isValidGroupId(String groupId) {
        // Define your validation logic for Group ID (e.g., regex or length check)
        return groupId != null && groupId.matches("[a-z]+\\.[a-z]+");
    }

    private boolean isValidArtifactId(String artifactId) {
        // Define your validation logic for Artifact ID
        return artifactId != null && !artifactId.trim().isEmpty();
    }

    private boolean isSuitableSdkType(SdkTypeId sdkTypeId) {
        return sdkTypeId.getName().contains("Java");
    }

    private void updateContext() {
        context.putUserData(GROUP_ID_KEY, groupIdProperty.get());
        context.putUserData(ARTIFACT_ID_KEY, artifactIdProperty.get());

        context.setProjectName(artifactIdProperty.get());
        context.setProjectFileDirectory(new File(locationProperty.get(), artifactIdProperty.get()).toPath(), true);

        Sdk selectedSdk = jdkComboBox.getSelectedJdk();
        if (selectedSdk != null) {
            context.setProjectJdk(selectedSdk);
        }
    }

    @Override
    public void setupProject(@NotNull Project project) {

        String groupId = context.getUserData(ProjectWizardStep.GROUP_ID_KEY);
        String artifactId = context.getUserData(ProjectWizardStep.ARTIFACT_ID_KEY);
        String groupIdLower = groupId.toLowerCase(Locale.ENGLISH);
        String projectType = context.getUserData(ProjectWizardStep.PROJECT_TYPE_KEY);
        boolean includeHttp = Boolean.TRUE.equals(context.getUserData(ProjectWizardStep.INCLUDE_HTTP_KEY));
        boolean includeSecurity = Boolean.TRUE.equals(context.getUserData(ProjectWizardStep.INCLUDE_SECURITY_KEY));
        boolean includeContext = Boolean.TRUE.equals(context.getUserData(ProjectWizardStep.INCLUDE_CONTEXT_KEY));
        if (projectType == null) {
            projectType = "Minecraft Plugin (Multi-Module)";
        }
        File baseDir = new File(locationProperty.get(), artifactId);


        //Create base directory if it doesn't exist
        if (!baseDir.exists()) {
            logger.info("Creating directory: " + baseDir);
            baseDir.mkdirs();
        }
        logger.info("Creating project: " + artifactId + " with groupId: " + groupId + " at location: " + baseDir);

        try {
            if ("Minecraft Plugin (Multi-Module)".equals(projectType)) {
                // Generate project with API module

                String pluginYml = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/plugin.yml").readAllBytes(), StandardCharsets.UTF_8);
                pluginYml = pluginYml.replace("$MAIN$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH) + "." + artifactId);

                //Load templates from resources/projectWizard/multi/ - api.pom.xml, main.pom.xml, parent.pom.xml
                String parentPom = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/parent.pom.xml").readAllBytes(), StandardCharsets.UTF_8);
                String apiPom = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/api.pom.xml").readAllBytes(), StandardCharsets.UTF_8);
                String mainPom = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/main.pom.xml").readAllBytes(), StandardCharsets.UTF_8);

                // Resolve "latest" version placeholders before other replacements
                parentPom = resolveLatestVersions(parentPom);
                apiPom = resolveLatestVersions(apiPom);
                mainPom = resolveLatestVersions(mainPom);

                //Replace placeholders in the templates
                parentPom = parentPom.replace("$GROUP_ID$", groupId).replace("$ARTIFACT_ID$", artifactId);
                apiPom = apiPom.replace("$GROUP_ID$", groupId).replace("$ARTIFACT_ID$", artifactId);
                mainPom = mainPom.replace("$GROUP_ID$", groupId)
                        .replace("$ARTIFACT_ID$", artifactId)
                        .replace("$GROUP_ID_SLASHES$", groupIdLower.replace(".", "/"))
                        .replace("$ARTIFACT_ID_LOWER$", artifactId.toLowerCase(Locale.ENGLISH));

                String apiClass = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/ApiClass.java").readAllBytes(), StandardCharsets.UTF_8);
                apiClass = apiClass.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH) + ".api")

                        .replace("$CLASS_NAME$", artifactId + "Api");

                String pluginClass = new String(Plugin.class.getResourceAsStream("/projectWizard/multi/PluginClass.java").readAllBytes(), StandardCharsets.UTF_8);
                // Resolve "latest" version in PluginClass.java annotation
                pluginClass = resolveLatestVersions(pluginClass);
                pluginClass = pluginClass.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH))
                        .replace("$CLASS_NAME$", artifactId);




                //Create directories for the project
                File base = new File(baseDir.getPath());

                //Write parent pom.xml
                File parentPomFile = new File(base, "pom.xml");
                parentPomFile.createNewFile();
                Files.write(parentPomFile.toPath(), parentPom.getBytes(StandardCharsets.UTF_8));

                //Create Artifactid-API directory
                //Create Artifactid-Plugin directory
                File apiDir = new File(base, artifactId + "-API");
                File pluginDir = new File(base, artifactId + "-Plugin");

                //Create directories
                apiDir.mkdirs();
                pluginDir.mkdirs();

                //Create API module files
                File apiPomFile = new File(apiDir, "pom.xml");
                apiPomFile.createNewFile();
                Files.write(apiPomFile.toPath(), apiPom.getBytes(StandardCharsets.UTF_8));

                //Create src/main/java directory for api
                File apiSrcMainJava = new File(apiDir, "src/main/java");
                apiSrcMainJava.mkdirs();

                //Create groupIdLower.artifactIdLower.api package
                File apiPackage = new File(apiSrcMainJava, groupIdLower.replace(".", "/") + "/" + artifactId.toLowerCase(Locale.ENGLISH) + "/api");
                apiPackage.mkdirs();

                //Create java api file with ArtifactIdApi.java
                File apiFile = new File(apiPackage, artifactId + "Api.java");
                apiFile.createNewFile();
                Files.write(apiFile.toPath(), apiClass.getBytes(StandardCharsets.UTF_8));
                //Api done ---

                //Create Plugin module files
                File mainPomFile = new File(pluginDir, "pom.xml");
                mainPomFile.createNewFile();
                Files.write(mainPomFile.toPath(), mainPom.getBytes(StandardCharsets.UTF_8));

                //Create src/main/java directory for plugin
                File pluginSrcMainJava = new File(pluginDir, "src/main/java");
                pluginSrcMainJava.mkdirs();

                //Create groupIdLower.artifactIdLower package
                File pluginPackage = new File(pluginSrcMainJava, groupIdLower.replace(".", "/") + "/" + artifactId.toLowerCase(Locale.ENGLISH));
                pluginPackage.mkdirs();

                //Create java main file with Main.java
                File pluginFile = new File(pluginPackage, artifactId + ".java");
                pluginFile.createNewFile();
                //Write plugin class to file
                Files.write(pluginFile.toPath(), pluginClass.getBytes(StandardCharsets.UTF_8));

                //Create resources directory
                File resources = new File(pluginDir, "src/main/resources");
                resources.mkdirs();

                //Copy plugin.yml
                File pluginYmlFile = new File(resources, "plugin.yml");
                pluginYmlFile.createNewFile();
                Files.write(pluginYmlFile.toPath(), pluginYml.getBytes(StandardCharsets.UTF_8));

            } else if ("Minecraft Plugin (Single Module)".equals(projectType)) {
                String pluginYml = new String(Plugin.class.getResourceAsStream("/projectWizard/single/plugin.yml").readAllBytes(), StandardCharsets.UTF_8);
                pluginYml = pluginYml.replace("$MAIN$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH) + "." + artifactId);

                // Generate project without API module
                //Load templates from resources/projectWizard/single/ - pom.xml, main.java
                String pom = new String(Plugin.class.getResourceAsStream("/projectWizard/single/main.pom.xml").readAllBytes(), StandardCharsets.UTF_8);
                // Resolve "latest" version placeholders
                pom = resolveLatestVersions(pom);
                pom = pom.replace("$GROUP_ID$", groupId)
                        .replace("$ARTIFACT_ID$", artifactId)
                        .replace("$GROUP_ID_SLASHES$", groupIdLower.replace(".", "/"))
                        .replace("$ARTIFACT_ID_LOWER$", artifactId.toLowerCase(Locale.ENGLISH));

                //Create directories for the project
                File base = new File(baseDir.getPath());

                //write pom.xml
                File pomFile = new File(base, "pom.xml");
                pomFile.createNewFile();
                Files.write(pomFile.toPath(), pom.getBytes(StandardCharsets.UTF_8));

                //Create src/main/java directory
                File srcMainJava = new File(base, "src/main/java");
                srcMainJava.mkdirs();

                //Create groupIdLower.artifactIdLower package
                File pluginPackage = new File(srcMainJava, groupIdLower + "/" + artifactId.toLowerCase(Locale.ENGLISH));
                pluginPackage.mkdirs();

                //Load main.java template
                String pluginClass = new String(Plugin.class.getResourceAsStream("/projectWizard/single/PluginClass.java").readAllBytes(), StandardCharsets.UTF_8);
                // Resolve "latest" version in PluginClass.java annotation
                pluginClass = resolveLatestVersions(pluginClass);
                pluginClass = pluginClass.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH))
                        .replace("$CLASS_NAME$", artifactId);

                //Create java main file with pluginClass
                File pluginFile = new File(pluginPackage, artifactId + ".java");
                pluginFile.createNewFile();
                //Write plugin class to file
                Files.write(pluginFile.toPath(), pluginClass.getBytes(StandardCharsets.UTF_8));

                //Create resources directory
                File resources = new File(base, "src/main/resources");
                resources.mkdirs();

                //Copy plugin.yml
                File pluginYmlFile = new File(resources, "plugin.yml");
                pluginYmlFile.createNewFile();
                Files.write(pluginYmlFile.toPath(), pluginYml.getBytes(StandardCharsets.UTF_8));

            } else if ("Standalone Application".equals(projectType)) {
                // Generate standalone application
                String className = artifactId.substring(0, 1).toUpperCase(Locale.ENGLISH) + artifactId.substring(1) + "Application";
                
                String pom = new String(Plugin.class.getResourceAsStream("/projectWizard/standalone/main.pom.xml").readAllBytes(), StandardCharsets.UTF_8);
                
                String httpDependency = "";
                if (includeHttp) {
                    httpDependency = "<dependency>\n" +
                            "            <groupId>net.vortexdevelopment</groupId>\n" +
                            "            <artifactId>VInject-HTTP</artifactId>\n" +
                            "            <version>latest</version>\n" +
                            "            <scope>compile</scope>\n" +
                            "        </dependency>";
                }
                pom = pom.replace("$HTTP_DEPENDENCY$", httpDependency);
                
                pom = resolveLatestVersions(pom);
                pom = pom.replace("$GROUP_ID$", groupId)
                        .replace("$ARTIFACT_ID$", artifactId)
                        .replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH))
                        .replace("$CLASS_NAME$", className);

                File base = new File(baseDir.getPath());

                File pomFile = new File(base, "pom.xml");
                pomFile.createNewFile();
                Files.write(pomFile.toPath(), pom.getBytes(StandardCharsets.UTF_8));

                File srcMainJava = new File(base, "src/main/java");
                srcMainJava.mkdirs();

                File pluginPackage = new File(srcMainJava, groupIdLower + "/" + artifactId.toLowerCase(Locale.ENGLISH));
                pluginPackage.mkdirs();

                String mainClass = new String(Plugin.class.getResourceAsStream("/projectWizard/standalone/MainClass.java").readAllBytes(), StandardCharsets.UTF_8);
                
                String httpTemplateDep = "";
                if (includeHttp) {
                    httpTemplateDep = ",\n                @TemplateDependency(groupId = \"net.vortexdevelopment\", artifactId = \"VInject-HTTP\", version = \"latest\")";
                }
                mainClass = mainClass.replace("$HTTP_TEMPLATE_DEPENDENCY$", httpTemplateDep);
                
                mainClass = resolveLatestVersions(mainClass);
                mainClass = mainClass.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH))
                        .replace("$CLASS_NAME$", className);

                File mainFile = new File(pluginPackage, className + ".java");
                mainFile.createNewFile();
                Files.write(mainFile.toPath(), mainClass.getBytes(StandardCharsets.UTF_8));

                if (includeHttp && (includeSecurity || includeContext)) {
                    File securityDir = new File(pluginPackage, "security");
                    securityDir.mkdirs();

                    if (includeSecurity) {
                        String config = new String(Plugin.class.getResourceAsStream("/projectWizard/standalone/security/WebSecurityConfig.java").readAllBytes(), StandardCharsets.UTF_8);
                        config = config.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH));
                        File configFile = new File(securityDir, "WebSecurityConfig.java");
                        configFile.createNewFile();
                        Files.write(configFile.toPath(), config.getBytes(StandardCharsets.UTF_8));
                    }

                    if (includeContext || includeSecurity) {
                        // Both security filter and context are often used together
                        String filter = new String(Plugin.class.getResourceAsStream("/projectWizard/standalone/security/SecurityFilter.java").readAllBytes(), StandardCharsets.UTF_8);
                        filter = filter.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH));
                        File filterFile = new File(securityDir, "SecurityFilter.java");
                        filterFile.createNewFile();
                        Files.write(filterFile.toPath(), filter.getBytes(StandardCharsets.UTF_8));

                        String userCtx = new String(Plugin.class.getResourceAsStream("/projectWizard/standalone/security/UserContext.java").readAllBytes(), StandardCharsets.UTF_8);
                        userCtx = userCtx.replace("$PACKAGE$", groupIdLower + "." + artifactId.toLowerCase(Locale.ENGLISH));
                        File userCtxFile = new File(securityDir, "UserContext.java");
                        userCtxFile.createNewFile();
                        Files.write(userCtxFile.toPath(), userCtx.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }

            // Copy gitignore
            String gitignore = new String(Plugin.class.getResourceAsStream("/projectWizard/gitignore").readAllBytes(), StandardCharsets.UTF_8);
            File gitignoreFile = new File(baseDir, ".gitignore");
            gitignoreFile.createNewFile();
            Files.write(gitignoreFile.toPath(), gitignore.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error creating project", e);
            e.printStackTrace();
        }
    }

    @Override
    public @NotNull WizardContext getContext() {
        return context;
    }

    @Override
    public @NotNull PropertyGraph getPropertyGraph() {
        return new PropertyGraph();
    }

    @Override
    public @NotNull Keywords getKeywords() {
        return new Keywords();
    }

    @Override
    public @NotNull UserDataHolder getData() {
        return context;
    }

    /**
     * Resolves "latest" version placeholders in template content.
     * Replaces "latest" with actual resolved versions from the Maven repository.
     * 
     * @param templateContent The template content that may contain "latest" placeholders
     * @return Template content with "latest" replaced by resolved versions
     */
    private String resolveLatestVersions(String templateContent) {
        if (templateContent == null || !templateContent.contains("latest")) {
            return templateContent;
        }

        MavenVersionResolver resolver = MavenVersionResolver.getInstance();
        String result = templateContent;

        // Resolve VortexCore version
        if (result.contains("VortexCore") && result.contains("latest")) {
            String vortexCoreVersion = resolver.resolveVersion("net.vortexdevelopment", "VortexCore");

            result = result.replaceAll(
                    "(<vortexcore\\.version>)latest(</vortexcore\\.version>)",
                    "$1" + vortexCoreVersion + "$2"
            );

            // Replace in POM format with flexible whitespace: <version>latest</version>
            result = result.replaceAll(
                    "(<groupId>net\\.vortexdevelopment</groupId>\\s*<artifactId>VortexCore</artifactId>\\s*<version>)latest(</version>)",
                    "$1" + vortexCoreVersion + "$2"
            );

            // Replace in annotation format: version = "latest"
            result = result.replaceAll(
                    "(artifactId\\s*=\\s*\"VortexCore\",\\s*version\\s*=\\s*\")latest(\")",
                    "$1" + vortexCoreVersion + "$2"
            );
        }

        // Resolve VortexCore-Paper version (generated Minecraft templates / dual Paper+Spigot profiles)
        if (result.contains("VortexCore-Paper") && result.contains("latest")) {
            String vortexCorePaperVersion = resolver.resolveVersion("net.vortexdevelopment", "VortexCore-Paper");
            result = result.replaceAll(
                    "(<groupId>net\\.vortexdevelopment</groupId>\\s*<artifactId>VortexCore-Paper</artifactId>\\s*<version>)latest(</version>)",
                    "$1" + vortexCorePaperVersion + "$2"
            );
            result = result.replaceAll(
                    "(artifactId\\s*=\\s*\"VortexCore-Paper\",\\s*version\\s*=\\s*\")latest(\")",
                    "$1" + vortexCorePaperVersion + "$2"
            );
        }

        // Resolve VInject-Core version
        if (result.contains("VInject-Core") && result.contains("latest")) {
            String vinjectFrameworkVersion = resolver.resolveVersion("net.vortexdevelopment", "VInject-Core");

            // Replace in POM format: <version>latest</version>
            result = result.replaceAll(
                    "(<groupId>net\\.vortexdevelopment</groupId>\\s*<artifactId>VInject-Core</artifactId>\\s*<version>)latest(</version>)",
                    "$1" + vinjectFrameworkVersion + "$2"
            );

            // Replace in annotation format: version = "latest"
            result = result.replaceAll(
                    "(artifactId\\s*=\\s*\"VInject-Core\",\\s*version\\s*=\\s*\")latest(\")",
                    "$1" + vinjectFrameworkVersion + "$2"
            );
        }

        // Resolve VInject-HTTP version
        if (result.contains("VInject-HTTP") && result.contains("latest")) {
            String vinjectHttpVersion = resolver.resolveVersion("net.vortexdevelopment", "VInject-HTTP");

            // Replace in POM format: <version>latest</version>
            result = result.replaceAll(
                    "(<groupId>net\\.vortexdevelopment</groupId>\\s*<artifactId>VInject-HTTP</artifactId>\\s*<version>)latest(</version>)",
                    "$1" + vinjectHttpVersion + "$2"
            );

            // Replace in annotation format: version = "latest"
            result = result.replaceAll(
                    "(artifactId\\s*=\\s*\"VInject-HTTP\",\\s*version\\s*=\\s*\")latest(\")",
                    "$1" + vinjectHttpVersion + "$2"
            );
        }

        // Resolve vinject-maven-plugin version
        if (result.contains("vinject-maven-plugin") && result.contains("latest")) {
            String transformerVersion = resolver.resolveVersion("net.vortexdevelopment", "vinject-maven-plugin");
            
            // Replace in POM format with flexible whitespace: <version>latest</version>
            result = result.replaceAll(
                    "(<groupId>net\\.vortexdevelopment</groupId>\\s*<artifactId>vinject-maven-plugin</artifactId>\\s*<version>)latest(</version>)",
                    "$1" + transformerVersion + "$2"
            );
        }

        return result;
    }
}
