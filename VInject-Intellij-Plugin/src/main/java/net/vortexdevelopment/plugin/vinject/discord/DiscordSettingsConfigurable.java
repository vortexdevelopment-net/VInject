package net.vortexdevelopment.plugin.vinject.discord;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DiscordSettingsConfigurable implements Configurable {
    private final Project project;
    private DiscordSettings settings;
    
    // UI Components
    private JPanel mainPanel;
    private JCheckBox enabledCheckBox;
    private JTextField discordClientIdField;
    private JCheckBox showFileNameCheckBox;
    private JCheckBox showProjectNameCheckBox;
    private JCheckBox showFileTypeCheckBox;
    private JCheckBox showLineCountCheckBox;
    private JCheckBox showElapsedTimeCheckBox;
    private JTextField customBigImageField;
    private JTextField customSmallImageField;
    private JTextField customStatusTextField;
    private JTextField customLine1FormatField;
    private JTextField customLine2FormatField;
    private JButton testConnectionButton;
    private JLabel connectionStatusLabel;
    
    // Image key fields
    private JTextField javaImageField;
    private JTextField kotlinImageField;
    private JTextField xmlImageField;
    private JTextField jsonImageField;
    private JTextField yamlImageField;
    private JTextField propertiesImageField;
    private JTextField gradleImageField;
    private JTextField mavenImageField;
    private JTextField editingImageField;
    private JTextField idleImageField;
    private JTextField intellijLogoField;

    public DiscordSettingsConfigurable(Project project) {
        this.project = project;
        this.settings = DiscordSettings.getInstance(project);
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Discord Rich Presence";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        if (mainPanel == null) {
            createUI();
        }
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        if (settings == null) return false;

        return enabledCheckBox.isSelected() != settings.isDiscordRpcEnabled() ||
               !discordClientIdField.getText().trim().equals(settings.getDiscordClientId()) ||
               showFileNameCheckBox.isSelected() != settings.isShowFileName() ||
               showProjectNameCheckBox.isSelected() != settings.isShowProjectName() ||
               showFileTypeCheckBox.isSelected() != settings.isShowFileType() ||
               showLineCountCheckBox.isSelected() != settings.isShowLineCount() ||
               showElapsedTimeCheckBox.isSelected() != settings.isShowElapsedTime() ||
               !customBigImageField.getText().trim().equals(settings.getCustomBigImage()) ||
               !customSmallImageField.getText().trim().equals(settings.getCustomSmallImage()) ||
               !customStatusTextField.getText().trim().equals(settings.getCustomStatusText()) ||
               !customLine1FormatField.getText().trim().equals(settings.getCustomLine1Format()) ||
               !customLine2FormatField.getText().trim().equals(settings.getCustomLine2Format()) ||
               !javaImageField.getText().trim().equals(settings.getJavaImageKey()) ||
               !kotlinImageField.getText().trim().equals(settings.getKotlinImageKey()) ||
               !xmlImageField.getText().trim().equals(settings.getXmlImageKey()) ||
               !jsonImageField.getText().trim().equals(settings.getJsonImageKey()) ||
               !yamlImageField.getText().trim().equals(settings.getYamlImageKey()) ||
               !propertiesImageField.getText().trim().equals(settings.getPropertiesImageKey()) ||
               !gradleImageField.getText().trim().equals(settings.getGradleImageKey()) ||
               !mavenImageField.getText().trim().equals(settings.getMavenImageKey()) ||
               !editingImageField.getText().trim().equals(settings.getDefaultEditingImageKey()) ||
               !idleImageField.getText().trim().equals(settings.getDefaultIdleImageKey()) ||
               !intellijLogoField.getText().trim().equals(settings.getIntellijLogoImageKey());
    }

    @Override
    public void apply() {
        if (settings == null) return;

        settings.setDiscordRpcEnabled(enabledCheckBox.isSelected());
        settings.setDiscordClientId(discordClientIdField.getText().trim());
        settings.setShowFileName(showFileNameCheckBox.isSelected());
        settings.setShowProjectName(showProjectNameCheckBox.isSelected());
        settings.setShowFileType(showFileTypeCheckBox.isSelected());
        settings.setShowLineCount(showLineCountCheckBox.isSelected());
        settings.setShowElapsedTime(showElapsedTimeCheckBox.isSelected());
        settings.setCustomBigImage(customBigImageField.getText().trim());
        settings.setCustomSmallImage(customSmallImageField.getText().trim());
        settings.setCustomStatusText(customStatusTextField.getText().trim());
        settings.setCustomLine1Format(customLine1FormatField.getText().trim());
        settings.setCustomLine2Format(customLine2FormatField.getText().trim());
        
        // Image keys
        settings.setJavaImageKey(javaImageField.getText().trim());
        settings.setKotlinImageKey(kotlinImageField.getText().trim());
        settings.setXmlImageKey(xmlImageField.getText().trim());
        settings.setJsonImageKey(jsonImageField.getText().trim());
        settings.setYamlImageKey(yamlImageField.getText().trim());
        settings.setPropertiesImageKey(propertiesImageField.getText().trim());
        settings.setGradleImageKey(gradleImageField.getText().trim());
        settings.setMavenImageKey(mavenImageField.getText().trim());
        settings.setDefaultEditingImageKey(editingImageField.getText().trim());
        settings.setDefaultIdleImageKey(idleImageField.getText().trim());
        settings.setIntellijLogoImageKey(intellijLogoField.getText().trim());
    }

    @Override
    public void reset() {
        if (settings == null) return;

        enabledCheckBox.setSelected(settings.isDiscordRpcEnabled());
        discordClientIdField.setText(settings.getDiscordClientId());
        showFileNameCheckBox.setSelected(settings.isShowFileName());
        showProjectNameCheckBox.setSelected(settings.isShowProjectName());
        showFileTypeCheckBox.setSelected(settings.isShowFileType());
        showLineCountCheckBox.setSelected(settings.isShowLineCount());
        showElapsedTimeCheckBox.setSelected(settings.isShowElapsedTime());
        customBigImageField.setText(settings.getCustomBigImage());
        customSmallImageField.setText(settings.getCustomSmallImage());
        customStatusTextField.setText(settings.getCustomStatusText());
        customLine1FormatField.setText(settings.getCustomLine1Format());
        customLine2FormatField.setText(settings.getCustomLine2Format());
        
        // Image keys
        javaImageField.setText(settings.getJavaImageKey());
        kotlinImageField.setText(settings.getKotlinImageKey());
        xmlImageField.setText(settings.getXmlImageKey());
        jsonImageField.setText(settings.getJsonImageKey());
        yamlImageField.setText(settings.getYamlImageKey());
        propertiesImageField.setText(settings.getPropertiesImageKey());
        gradleImageField.setText(settings.getGradleImageKey());
        mavenImageField.setText(settings.getMavenImageKey());
        editingImageField.setText(settings.getDefaultEditingImageKey());
        idleImageField.setText(settings.getDefaultIdleImageKey());
        intellijLogoField.setText(settings.getIntellijLogoImageKey());
        
        updateComponentStates();
    }

    private void createUI() {
        mainPanel = new JPanel(new BorderLayout());
        
        // Create tabbed pane for better organization
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // General settings tab
        tabbedPane.addTab("General", createGeneralPanel());
        
        // Image settings tab
        tabbedPane.addTab("Images", createImagePanel());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        initGeneralComponents();

        // Enable Discord RPC
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(enabledCheckBox, gbc);

        // Discord Client ID
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Discord Client ID:"), gbc);
        gbc.gridx = 1;
        panel.add(discordClientIdField, gbc);

        // Privacy settings
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        panel.add(new JLabel("Privacy Settings:"), gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        panel.add(showFileNameCheckBox, gbc);
        gbc.gridx = 1;
        panel.add(showProjectNameCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        panel.add(showFileTypeCheckBox, gbc);
        gbc.gridx = 1;
        panel.add(showLineCountCheckBox, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        panel.add(showElapsedTimeCheckBox, gbc);

        // Custom status
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1;
        panel.add(new JLabel("Custom Status:"), gbc);
        gbc.gridx = 1;
        panel.add(customStatusTextField, gbc);

        // Line formats with placeholders
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2;
        panel.add(new JLabel("Display Format (Placeholders: {project}, {filename}, {filetype}, {action}, {time}):"), gbc);

        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 1;
        panel.add(new JLabel("Line 1 Format:"), gbc);
        gbc.gridx = 1;
        panel.add(customLine1FormatField, gbc);

        gbc.gridx = 0; gbc.gridy = 9;
        panel.add(new JLabel("Line 2 Format:"), gbc);
        gbc.gridx = 1;
        panel.add(customLine2FormatField, gbc);

        // Custom images
        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 2;
        panel.add(new JLabel("Custom Images (optional):"), gbc);

        gbc.gridx = 0; gbc.gridy = 11; gbc.gridwidth = 1;
        panel.add(new JLabel("Custom Big Image:"), gbc);
        gbc.gridx = 1;
        panel.add(customBigImageField, gbc);

        gbc.gridx = 0; gbc.gridy = 12;
        panel.add(new JLabel("Custom Small Image:"), gbc);
        gbc.gridx = 1;
        panel.add(customSmallImageField, gbc);

        // Test connection
        gbc.gridx = 0; gbc.gridy = 13; gbc.gridwidth = 2;
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.add(testConnectionButton);
        testPanel.add(connectionStatusLabel);
        panel.add(testPanel, gbc);

        return panel;
    }

    private JPanel createImagePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        initImageComponents();

        int row = 0;
        
        // Add description
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        panel.add(new JLabel("Configure image keys for different file types (upload these to your Discord application):"), gbc);

        // File type images
        gbc.gridwidth = 1;
        addImageField(panel, gbc, row++, "Java files:", javaImageField);
        addImageField(panel, gbc, row++, "Kotlin files:", kotlinImageField);
        addImageField(panel, gbc, row++, "XML files:", xmlImageField);
        addImageField(panel, gbc, row++, "JSON files:", jsonImageField);
        addImageField(panel, gbc, row++, "YAML files:", yamlImageField);
        addImageField(panel, gbc, row++, "Properties files:", propertiesImageField);
        addImageField(panel, gbc, row++, "Gradle files:", gradleImageField);
        addImageField(panel, gbc, row++, "Maven files:", mavenImageField);
        
        // State images
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        panel.add(new JLabel("State Images:"), gbc);
        gbc.gridwidth = 1;
        
        addImageField(panel, gbc, row++, "Editing files:", editingImageField);
        addImageField(panel, gbc, row++, "Idle state:", idleImageField);
        addImageField(panel, gbc, row++, "IntelliJ logo:", intellijLogoField);

        return panel;
    }

    private void addImageField(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        panel.add(field, gbc);
    }

    private void initGeneralComponents() {
        enabledCheckBox = new JCheckBox("Enable Discord Rich Presence");
        discordClientIdField = new JTextField(20);
        showFileNameCheckBox = new JCheckBox("Show file name");
        showProjectNameCheckBox = new JCheckBox("Show project name");
        showFileTypeCheckBox = new JCheckBox("Show file type");
        showLineCountCheckBox = new JCheckBox("Show line count");
        showElapsedTimeCheckBox = new JCheckBox("Show elapsed time");
        customBigImageField = new JTextField(20);
        customSmallImageField = new JTextField(20);
        customStatusTextField = new JTextField(20);
        customLine1FormatField = new JTextField(30);
        customLine2FormatField = new JTextField(30);
        testConnectionButton = new JButton("Test Connection");
        connectionStatusLabel = new JLabel(" ");

        enabledCheckBox.addActionListener(e -> updateComponentStates());
        testConnectionButton.addActionListener(new TestConnectionListener());
    }

    private void initImageComponents() {
        javaImageField = new JTextField(20);
        kotlinImageField = new JTextField(20);
        xmlImageField = new JTextField(20);
        jsonImageField = new JTextField(20);
        yamlImageField = new JTextField(20);
        propertiesImageField = new JTextField(20);
        gradleImageField = new JTextField(20);
        mavenImageField = new JTextField(20);
        editingImageField = new JTextField(20);
        idleImageField = new JTextField(20);
        intellijLogoField = new JTextField(20);
    }

    private void updateComponentStates() {
        boolean enabled = enabledCheckBox.isSelected();
        discordClientIdField.setEnabled(enabled);
        showFileNameCheckBox.setEnabled(enabled);
        showProjectNameCheckBox.setEnabled(enabled);
        showFileTypeCheckBox.setEnabled(enabled);
        showLineCountCheckBox.setEnabled(enabled);
        showElapsedTimeCheckBox.setEnabled(enabled);
        customBigImageField.setEnabled(enabled);
        customSmallImageField.setEnabled(enabled);
        customStatusTextField.setEnabled(enabled);
        customLine1FormatField.setEnabled(enabled);
        customLine2FormatField.setEnabled(enabled);
        testConnectionButton.setEnabled(enabled);
        
        // Image fields
        javaImageField.setEnabled(enabled);
        kotlinImageField.setEnabled(enabled);
        xmlImageField.setEnabled(enabled);
        jsonImageField.setEnabled(enabled);
        yamlImageField.setEnabled(enabled);
        propertiesImageField.setEnabled(enabled);
        gradleImageField.setEnabled(enabled);
        mavenImageField.setEnabled(enabled);
        editingImageField.setEnabled(enabled);
        idleImageField.setEnabled(enabled);
        intellijLogoField.setEnabled(enabled);
    }

    private class TestConnectionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            testConnectionButton.setEnabled(false);
            connectionStatusLabel.setForeground(Color.BLACK);
            connectionStatusLabel.setText("Testing connection...");

            // Apply current UI values to settings (so test uses what's on screen)
            try {
                if (settings != null) {
                    settings.setDiscordRpcEnabled(enabledCheckBox.isSelected());
                    settings.setDiscordClientId(discordClientIdField.getText().trim());
                }
            } catch (Exception ignored) { }

            // Run the actual connection test
            SwingUtilities.invokeLater(() -> {
                try {
                    DiscordHook.testConnection(project);

                    // Poll for result a short time since connect/presence are async
                    new Thread(() -> {
                        try { Thread.sleep(6000); } catch (InterruptedException ignored) {}
                        SwingUtilities.invokeLater(() -> {
                            if (DiscordHook.isConnected()) {
                                connectionStatusLabel.setText("✓ Connected to Discord");
                                connectionStatusLabel.setForeground(Color.GREEN);
                            } else {
                                connectionStatusLabel.setText("✗ Connection failed");
                                connectionStatusLabel.setForeground(Color.RED);
                            }
                            testConnectionButton.setEnabled(true);
                        });
                    }, "Discord-Test-Poll").start();
                } catch (Exception ex) {
                    connectionStatusLabel.setText("✗ Connection failed: " + ex.getMessage());
                    connectionStatusLabel.setForeground(Color.RED);
                    testConnectionButton.setEnabled(true);
                }
            });
        }
    }
} 