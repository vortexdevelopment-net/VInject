package net.vortexdevelopment.plugin.vinject.discord;

import net.vortexdevelopment.plugin.vinject.project.ProjectSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DiscordSettingsPanel extends JPanel {
    private final ProjectSettings settings;

    private JCheckBox enabledCheckBox;
    private JTextField clientIdField;
    private JCheckBox showFileNameCheckBox;
    private JCheckBox showProjectNameCheckBox;
    private JCheckBox showFileTypeCheckBox;
    private JCheckBox showLineCountCheckBox;
    private JCheckBox showElapsedTimeCheckBox;
    private JTextField customBigImageField;
    private JTextField customSmallImageField;
    private JTextField customStatusTextField;
    private JButton testConnectionButton;
    private JLabel connectionStatusLabel;

    public DiscordSettingsPanel(ProjectSettings settings) {
        this.settings = settings;
        initComponents();
        layoutComponents();
        loadSettings();
    }

    private void initComponents() {
        enabledCheckBox = new JCheckBox("Enable Discord Rich Presence");
        clientIdField = new JTextField(20);
        showFileNameCheckBox = new JCheckBox("Show file name");
        showProjectNameCheckBox = new JCheckBox("Show project name");
        showFileTypeCheckBox = new JCheckBox("Show file type");
        showLineCountCheckBox = new JCheckBox("Show line count");
        showElapsedTimeCheckBox = new JCheckBox("Show elapsed time");
        customBigImageField = new JTextField(20);
        customSmallImageField = new JTextField(20);
        customStatusTextField = new JTextField(20);
        testConnectionButton = new JButton("Test Connection");
        connectionStatusLabel = new JLabel(" ");

        // Add action listeners
        enabledCheckBox.addActionListener(e -> updateComponentStates());
        testConnectionButton.addActionListener(new TestConnectionListener());
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Enable Discord RPC
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(enabledCheckBox, gbc);

        // Client ID
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Discord Client ID:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(clientIdField, gbc);

        // Privacy settings
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        mainPanel.add(new JLabel("Privacy Settings:"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        mainPanel.add(showFileNameCheckBox, gbc);
        gbc.gridx = 1;
        mainPanel.add(showProjectNameCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        mainPanel.add(showFileTypeCheckBox, gbc);
        gbc.gridx = 1;
        mainPanel.add(showLineCountCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        mainPanel.add(showElapsedTimeCheckBox, gbc);

        // Custom images
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        mainPanel.add(new JLabel("Custom Images (optional):"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.gridwidth = 1;
        mainPanel.add(new JLabel("Big Image Key:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(customBigImageField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        mainPanel.add(new JLabel("Small Image Key:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(customSmallImageField, gbc);

        // Custom status
        gbc.gridx = 0;
        gbc.gridy = 9;
        mainPanel.add(new JLabel("Custom Status:"), gbc);
        gbc.gridx = 1;
        mainPanel.add(customStatusTextField, gbc);

        // Test connection
        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.add(testConnectionButton);
        testPanel.add(connectionStatusLabel);
        mainPanel.add(testPanel, gbc);

        add(mainPanel, BorderLayout.NORTH);

        updateComponentStates();
    }

    private void updateComponentStates() {
        boolean enabled = enabledCheckBox.isSelected();
        clientIdField.setEnabled(enabled);
        showFileNameCheckBox.setEnabled(enabled);
        showProjectNameCheckBox.setEnabled(enabled);
        showFileTypeCheckBox.setEnabled(enabled);
        showLineCountCheckBox.setEnabled(enabled);
        showElapsedTimeCheckBox.setEnabled(enabled);
        customBigImageField.setEnabled(enabled);
        customSmallImageField.setEnabled(enabled);
        customStatusTextField.setEnabled(enabled);
        testConnectionButton.setEnabled(enabled);
    }

    private void loadSettings() {
        enabledCheckBox.setSelected(settings.isDiscordRpcEnabled());
        clientIdField.setText(settings.getDiscordClientId());
        showFileNameCheckBox.setSelected(settings.isShowFileName());
        showProjectNameCheckBox.setSelected(settings.isShowProjectName());
        showFileTypeCheckBox.setSelected(settings.isShowFileType());
        showLineCountCheckBox.setSelected(settings.isShowLineCount());
        showElapsedTimeCheckBox.setSelected(settings.isShowElapsedTime());
        customBigImageField.setText(settings.getCustomBigImage());
        customSmallImageField.setText(settings.getCustomSmallImage());
        customStatusTextField.setText(settings.getCustomStatusText());
        updateComponentStates();
    }

    public void saveSettings() {
        settings.setDiscordRpcEnabled(enabledCheckBox.isSelected());
        settings.setDiscordClientId(clientIdField.getText().trim());
        settings.setShowFileName(showFileNameCheckBox.isSelected());
        settings.setShowProjectName(showProjectNameCheckBox.isSelected());
        settings.setShowFileType(showFileTypeCheckBox.isSelected());
        settings.setShowLineCount(showLineCountCheckBox.isSelected());
        settings.setShowElapsedTime(showElapsedTimeCheckBox.isSelected());
        settings.setCustomBigImage(customBigImageField.getText().trim());
        settings.setCustomSmallImage(customSmallImageField.getText().trim());
        settings.setCustomStatusText(customStatusTextField.getText().trim());
    }

    public boolean isModified() {
        return enabledCheckBox.isSelected() != settings.isDiscordRpcEnabled() ||
                !clientIdField.getText().trim().equals(settings.getDiscordClientId()) ||
                showFileNameCheckBox.isSelected() != settings.isShowFileName() ||
                showProjectNameCheckBox.isSelected() != settings.isShowProjectName() ||
                showFileTypeCheckBox.isSelected() != settings.isShowFileType() ||
                showLineCountCheckBox.isSelected() != settings.isShowLineCount() ||
                showElapsedTimeCheckBox.isSelected() != settings.isShowElapsedTime() ||
                !customBigImageField.getText().trim().equals(settings.getCustomBigImage()) ||
                !customSmallImageField.getText().trim().equals(settings.getCustomSmallImage()) ||
                !customStatusTextField.getText().trim().equals(settings.getCustomStatusText());
    }

    private class TestConnectionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            testConnectionButton.setEnabled(false);
            connectionStatusLabel.setText("Testing connection...");

            // Test Discord connection in background
            SwingUtilities.invokeLater(() -> {
                try {
                    if (DiscordHook.isConnected()) {
                        connectionStatusLabel.setText("✓ Connected to Discord");
                        connectionStatusLabel.setForeground(Color.GREEN);
                    } else {
                        connectionStatusLabel.setText("✗ Not connected to Discord");
                        connectionStatusLabel.setForeground(Color.RED);
                    }
                } catch (Exception ex) {
                    connectionStatusLabel.setText("✗ Connection failed");
                    connectionStatusLabel.setForeground(Color.RED);
                } finally {
                    testConnectionButton.setEnabled(true);
                }
            });
        }
    }
}