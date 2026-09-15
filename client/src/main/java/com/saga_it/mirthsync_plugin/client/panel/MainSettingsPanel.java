/*
 * Copyright 2021 Kaur Palang (original template)
 * Copyright 2025 Saga IT, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.saga_it.mirthsync_plugin.client.panel;

import com.saga_it.mirthsync_plugin.shared.PluginConstants;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.components.MirthCheckBox;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.core.ClientException;
import com.saga_it.mirthsync_plugin.shared.interfaces.MirthSyncApi;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.Locale;

import com.mirth.connect.model.User;

public class MainSettingsPanel extends AbstractSettingsPanel {

    private static final Color SECTION_BORDER_COLOR = new Color(204, 204, 204);

    private MirthTextField serverUrlField;
    private MirthTextField remoteUsernameField;
    private MirthPasswordField remotePasswordField;
    private MirthCheckBox ignoreCertWarningsCheckbox;

    private JComboBox<ActionOption> actionComboBox;
    private JComboBox<String> presetComboBox;
    private JButton savePresetButton;
    private JButton deletePresetButton;
    private JLabel gitCommandLabel;
    private MirthTextField gitCommandField;
    private MirthTextField targetDirectoryField;
    private JComboBox<DiskModeOption> diskModeComboBox;
    private MirthTextField restrictPathField;
    private JSpinner verbositySpinner;

    private MirthCheckBox forceCheckbox;
    private MirthCheckBox includeConfigurationMapCheckbox;
    private MirthCheckBox skipDisabledCheckbox;
    private MirthCheckBox deployCheckbox;
    private MirthCheckBox deployAllCheckbox;
    private MirthCheckBox deployChangedCheckbox;
    private MirthCheckBox deployNewCheckbox;
    private MirthCheckBox interactiveCheckbox;
    private MirthCheckBox autoCommitCheckbox;
    private MirthCheckBox gitInitCheckbox;
    private MirthCheckBox deleteOrphanedCheckbox;

    private MirthTextField commitMessageField;
    private MirthTextField gitAuthorField;
    private MirthTextField gitEmailField;
    private JRadioButton serverExecutionRadio;
    private JRadioButton clientExecutionRadio;
    private JButton targetBrowseButton;

    private JButton executeButton;
    private JButton mirthsyncHelpButton;

    private JButton clearOutputButton;
    private final JTextPane outputArea = new JTextPane();
    private OutputStyles mainOutputStyles;

    private boolean updatingFromModel;
    private final Map<String, Map<String, String>> defaultPresets = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> userPresets = new LinkedHashMap<>();
    private static final String PRESET_PLACEHOLDER = "-- Select a preset --";
    private boolean populatingPresets;
    private static final List<String> SETTING_KEYS = Arrays.asList(
            Keys.SERVER_URL,
            Keys.EXECUTION_MODE,
            Keys.IGNORE_CERT_WARNINGS,
            Keys.ACTION,
            Keys.TARGET_DIRECTORY,
            Keys.DISK_MODE,
            Keys.RESTRICT_PATH,
            Keys.VERBOSITY,
            Keys.FORCE,
            Keys.INCLUDE_CONFIGURATION_MAP,
            Keys.SKIP_DISABLED,
            Keys.DEPLOY,
            Keys.DEPLOY_ALL,
            Keys.DEPLOY_CHANGED,
            Keys.DEPLOY_NEW,
            Keys.INTERACTIVE,
            Keys.AUTO_COMMIT,
            Keys.GIT_INIT,
            Keys.DELETE_ORPHANED,
            Keys.COMMIT_MESSAGE,
            Keys.GIT_AUTHOR,
            Keys.GIT_EMAIL,
            Keys.GIT_COMMAND
    );
    private static final String PRESET_NAME_DELIMITER = "\u001F";
    private static final String PRESET_DATA_SEPARATOR = ";";
    private static final String PRESET_DATA_KEY_VALUE_SEPARATOR = ":";
    private static final Pattern ANSI_ESCAPE_PATTERN = Pattern.compile("\\x1B\\[[0-9;:]*[ -/]*[@-~]");
    private static final Pattern ANSI_OSC_PATTERN = Pattern.compile("\\x1B\\][^\\x07]*(\\x07|\\x1B\\\\)");
    private static final Pattern INVALID_CONTROL_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    public MainSettingsPanel() {
        // The name of our tab in the Settings menu
        super(PluginConstants.SETTINGS_TABNAME_MAIN);
        initComponents();
    }

    private void initComponents() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setLayout(new MigLayout("insets 12, novisualpadding, fillx, gap 12", "[grow][pref!]", "[][][][][][grow]"));

        add(buildConnectionPanel(), "cell 0 0, growx");
        add(buildPresetPanel(), "cell 1 0, growx");
        add(buildOperationPanel(), "cell 0 1 2 1, growx");
        add(buildOptionsPanel(), "cell 0 2 2 1, growx");
        add(buildGitMetadataPanel(), "cell 0 3 2 1, growx");
        add(buildActionPanel(), "cell 0 4 2 1, growx");
        add(buildOutputPanel(), "cell 0 5 2 1, grow, push");

        populateUserGitInfo();
        initDefaultPresets();
        refreshPresetDropdown();
        updateActionVisibility();
    }

    private JPanel buildConnectionPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6", "[right]12[grow]", ""));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Connection"));

        serverUrlField = new MirthTextField();
        serverUrlField.setColumns(30);
        registerDocumentListener(serverUrlField);

        remoteUsernameField = new MirthTextField();
        remoteUsernameField.setColumns(20);
        registerDocumentListener(remoteUsernameField);

        remotePasswordField = new MirthPasswordField();
        remotePasswordField.setColumns(20);
        registerDocumentListener(remotePasswordField);

        ignoreCertWarningsCheckbox = createCheckBox("Ignore certificate warnings");
        ignoreCertWarningsCheckbox.setToolTipText("Skip TLS certificate validation warnings for HTTPS connections.");
        registerCheckboxListener(ignoreCertWarningsCheckbox);

        panel.add(new JLabel("Server URL:"), "right");
        panel.add(serverUrlField, "growx, wrap");

        panel.add(new JLabel("Username:"), "right");
        panel.add(remoteUsernameField, "growx, wrap");

        panel.add(new JLabel("Password:"), "right");
        panel.add(remotePasswordField, "growx, wrap");

        panel.add(ignoreCertWarningsCheckbox, "span 2, left");

        return panel;
    }

    private JPanel buildOperationPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6", "[right]12[grow][pref!]", ""));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Action & Paths"));

        serverExecutionRadio = new JRadioButton("Run on server");
        serverExecutionRadio.setOpaque(false);
        clientExecutionRadio = new JRadioButton("Run locally");
        clientExecutionRadio.setOpaque(false);

        ButtonGroup executionGroup = new ButtonGroup();
        executionGroup.add(serverExecutionRadio);
        executionGroup.add(clientExecutionRadio);
        serverExecutionRadio.setSelected(true);

        serverExecutionRadio.addActionListener(e -> {
            updateExecutionControls();
            markDirty();
        });
        clientExecutionRadio.addActionListener(e -> {
            updateExecutionControls();
            markDirty();
        });

        JPanel executionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        executionPanel.setOpaque(false);
        executionPanel.add(serverExecutionRadio);
        executionPanel.add(Box.createHorizontalStrut(12));
        executionPanel.add(clientExecutionRadio);

        actionComboBox = new JComboBox<>(ActionOption.values());
        actionComboBox.addActionListener(e -> {
            updateActionVisibility();
            markDirty();
        });

        gitCommandLabel = new JLabel("Git command:");
        gitCommandField = new MirthTextField();
        gitCommandField.setColumns(30);
        gitCommandField.setToolTipText("Provide the git subcommand and arguments, e.g. \"status\" or \"diff --staged\".");
        registerDocumentListener(gitCommandField);

        targetDirectoryField = new MirthTextField();
        targetDirectoryField.setColumns(30);
        targetDirectoryField.setToolTipText("Base directory used for pushing or pulling files.");
        registerDocumentListener(targetDirectoryField);

        targetBrowseButton = new JButton("Browse...");
        targetBrowseButton.addActionListener(e -> browseForTargetDirectory());

        diskModeComboBox = new JComboBox<>(DiskModeOption.values());
        diskModeComboBox.setSelectedItem(DiskModeOption.CODE);
        diskModeComboBox.addActionListener(e -> markDirty());

        restrictPathField = new MirthTextField();
        restrictPathField.setColumns(30);
        restrictPathField.setToolTipText("Optional relative path inside the target directory to limit push scope.");
        registerDocumentListener(restrictPathField);

        verbositySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
        JComponent editor = verbositySpinner.getEditor();
        if (editor instanceof JSpinner.NumberEditor numberEditor) {
            numberEditor.getTextField().setColumns(3);
        }
        verbositySpinner.addChangeListener(e -> markDirty());

        panel.add(new JLabel("Execute on:"), "right");
        panel.add(executionPanel, "growx, span 2, wrap");

        panel.add(new JLabel("Action:"), "right");
        panel.add(actionComboBox, "growx, span 2, wrap");

        panel.add(gitCommandLabel, "right");
        panel.add(gitCommandField, "growx, span 2, wrap");

        panel.add(new JLabel("Target directory:"), "right");
        panel.add(targetDirectoryField, "growx");
        panel.add(targetBrowseButton, "right, wrap");

        panel.add(new JLabel("Disk mode:"), "right");
        panel.add(diskModeComboBox, "growx, span 2, wrap");

        panel.add(new JLabel("Restrict to path:"), "right");
        panel.add(restrictPathField, "growx, span 2, wrap");

        panel.add(new JLabel("Verbosity level:"), "right");
        panel.add(verbositySpinner, "left, span 2");

        updateExecutionControls();

        return panel;
    }

    private JPanel buildOptionsPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6", "[grow]12[grow]", ""));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Flags & Toggles"));

        forceCheckbox = createCheckBox("Force overwrite");
        forceCheckbox.setToolTipText("Overwrite local or remote files without revision checks.");

        includeConfigurationMapCheckbox = createCheckBox("Include configuration map");
        skipDisabledCheckbox = createCheckBox("Skip disabled channels");
        deployCheckbox = createCheckBox("Deploy channels after push");
        deployAllCheckbox = createCheckBox("Deploy all together");
        deployChangedCheckbox = createCheckBox("Deploy only changed channels");
        deployChangedCheckbox.setToolTipText("After the push, deploy only channels the server reports as changed (--deploy-changed). Clears the other deploy options.");
        deployNewCheckbox = createCheckBox("Also deploy new channels");
        deployNewCheckbox.setToolTipText("With \"Deploy only changed channels\": also deploy pushed channels that are not currently deployed (--deploy-new).");
        interactiveCheckbox = createCheckBox("Allow interactive prompts");
        autoCommitCheckbox = createCheckBox("Auto commit after operations");
        gitInitCheckbox = createCheckBox("Initialise git repository if missing");
        deleteOrphanedCheckbox = createCheckBox("Delete orphaned files on pull");
        registerCheckboxListener(forceCheckbox);
        registerCheckboxListener(includeConfigurationMapCheckbox);
        registerCheckboxListener(skipDisabledCheckbox);
        registerCheckboxListener(deployCheckbox);
        registerCheckboxListener(deployAllCheckbox);
        registerCheckboxListener(deployChangedCheckbox);
        registerCheckboxListener(deployNewCheckbox);
        // "Only changed" cannot be honoured alongside the other two deploy
        // modes (mirthsync runs both and warns), so ticking one side clears
        // the other. ItemListeners also fire on programmatic setSelected, so
        // preset restore and properties load stay consistent without extra calls.
        deployChangedCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                deployCheckbox.setSelected(false);
                deployAllCheckbox.setSelected(false);
            }
            syncDeployNewEnabled();
        });
        deployCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                deployChangedCheckbox.setSelected(false);
            }
        });
        deployAllCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                deployChangedCheckbox.setSelected(false);
            }
        });
        syncDeployNewEnabled();
        registerCheckboxListener(interactiveCheckbox);
        registerCheckboxListener(autoCommitCheckbox);
        registerCheckboxListener(gitInitCheckbox);
        registerCheckboxListener(deleteOrphanedCheckbox);

        panel.add(forceCheckbox, "growx");
        panel.add(includeConfigurationMapCheckbox, "growx, wrap");

        panel.add(skipDisabledCheckbox, "growx");
        panel.add(deployCheckbox, "growx, wrap");

        interactiveCheckbox.setEnabled(false);
        interactiveCheckbox.setToolTipText("Currently unsupported");

        JPanel interactivePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        interactivePanel.setOpaque(false);
        interactivePanel.add(interactiveCheckbox);

        panel.add(deployAllCheckbox, "growx");
        panel.add(interactivePanel, "growx, wrap");

        panel.add(deployChangedCheckbox, "growx");
        panel.add(deployNewCheckbox, "growx, wrap");

        panel.add(autoCommitCheckbox, "growx");
        panel.add(gitInitCheckbox, "growx, wrap");

        panel.add(deleteOrphanedCheckbox, "growx");

        return panel;
    }

    private JPanel buildGitMetadataPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6", "[right]12[grow]", ""));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Git Metadata"));

        commitMessageField = new MirthTextField();
        commitMessageField.setColumns(30);
        commitMessageField.setText("mirthsync commit");
        registerDocumentListener(commitMessageField);

        gitAuthorField = new MirthTextField();
        gitAuthorField.setColumns(20);
        gitAuthorField.setEditable(false);
        registerDocumentListener(gitAuthorField);
        gitEmailField = new MirthTextField();
        gitEmailField.setColumns(20);
        gitEmailField.setEditable(false);
        registerDocumentListener(gitEmailField);

        panel.add(new JLabel("Commit message:"), "right");
        panel.add(commitMessageField, "growx, wrap");

        panel.add(new JLabel("Git author:"), "right");
        panel.add(gitAuthorField, "growx, wrap");

        panel.add(new JLabel("Git email:"), "right");
        panel.add(gitEmailField, "growx");

        return panel;
    }

    private JPanel buildActionPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 0, novisualpadding, gap 6", "[left][left][left][push]", "[]"));
        panel.setBackground(Color.WHITE);

        executeButton = new JButton("Run mirthsync");
        executeButton.addActionListener(e -> executeMirthsync());

        mirthsyncHelpButton = new JButton("Show MirthSync Help");
        mirthsyncHelpButton.addActionListener(e -> runMirthsyncHelp());

        clearOutputButton = new JButton("Clear output");
        clearOutputButton.addActionListener(e -> {
            outputArea.setText("");
            outputArea.setCaretPosition(0);
        });

        panel.add(executeButton);
        panel.add(mirthsyncHelpButton);
        panel.add(clearOutputButton);

        return panel;
    }

    private JPanel buildPresetPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6", "[right]8[grow]8[]", "[]6[]"));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Presets"));

        presetComboBox = new JComboBox<>();
        presetComboBox.addItem(PRESET_PLACEHOLDER);
        presetComboBox.addActionListener(e -> {
            if (populatingPresets) {
                updatePresetButtons();
                return;
            }
            applySelectedPreset();
            updatePresetButtons();
        });

        savePresetButton = new JButton("Save current as preset");
        savePresetButton.addActionListener(e -> promptAndSavePreset());

        deletePresetButton = new JButton("Delete preset");
        deletePresetButton.setEnabled(false);
        deletePresetButton.addActionListener(e -> deleteSelectedPreset());

        panel.add(new JLabel("Presets:"), "cell 0 0, right");
        panel.add(presetComboBox, "cell 1 0 2 1, growx");
        panel.add(savePresetButton, "cell 1 1, right");
        panel.add(deletePresetButton, "cell 2 1, right");

        return panel;
    }

    private JPanel buildOutputPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 12, novisualpadding, hidemode 3, gap 6, fill", "[grow]", "[][grow]"));
        panel.setBackground(Color.WHITE);
        panel.setBorder(createSectionBorder("Output"));

        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ensureMainOutputStyles();

        JPanel toolbar = new JPanel(new MigLayout("insets 0, novisualpadding, gap 6", "[push][right]", "[]"));
        toolbar.setOpaque(false);
        JButton popOutButton = new JButton("Open in new window");
        popOutButton.addActionListener(e -> openOutputInNewWindow());
        toolbar.add(new JLabel(), "growx");
        toolbar.add(popOutButton, "right");

        JScrollPane scrollPane = new JScrollPane(outputArea);
        panel.add(toolbar, "growx, wrap");
        panel.add(scrollPane, "grow, push");

        return panel;
    }

    private void populateUserGitInfo() {
        boolean previous = updatingFromModel;
        updatingFromModel = true;
        try {
            if (gitAuthorField == null || gitEmailField == null) {
                return;
            }

            User currentUser = null;
            if (PlatformUI.MIRTH_FRAME != null && PlatformUI.MIRTH_FRAME.mirthClient != null) {
                try {
                    currentUser = PlatformUI.MIRTH_FRAME.mirthClient.getCurrentUser();
                } catch (ClientException ignored) {
                    // best effort only; fall back to existing values
                }
            }

            if (currentUser == null) {
                return;
            }

            String author = buildAuthorFromUser(currentUser);
            if (!isBlank(author) && isBlank(gitAuthorField.getText())) {
                gitAuthorField.setText(author);
            }

            String email = currentUser.getEmail();
            if (isBlank(email)) {
                email = currentUser.getUsername();
            }
            if (!isBlank(email) && isBlank(gitEmailField.getText())) {
                gitEmailField.setText(email);
            }
        } finally {
            updatingFromModel = previous;
        }
    }

    private String buildAuthorFromUser(User user) {
        if (user == null) {
            return "";
        }
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        StringJoiner joiner = new StringJoiner(" ");
        if (!isBlank(firstName)) {
            joiner.add(firstName.trim());
        }
        if (!isBlank(lastName)) {
            joiner.add(lastName.trim());
        }
        String combined = joiner.toString();
        if (!isBlank(combined)) {
            return combined;
        }
        return isBlank(user.getUsername()) ? "" : user.getUsername().trim();
    }

    private void initDefaultPresets() {
        defaultPresets.clear();

        String serverApiUrl = defaultServerApiUrl();
        String serverTargetDirectory = "./appdata/mirthsync";
        String clientTargetDirectory = defaultClientTargetDirectory();
        String clientTargetLabel = abbreviateHomePath(clientTargetDirectory);

        Map<String, String> pullPreset = createEmptyPreset();
        pullPreset.put(Keys.SERVER_URL, serverApiUrl);
        pullPreset.put(Keys.TARGET_DIRECTORY, serverTargetDirectory);
        pullPreset.put(Keys.ACTION, ActionOption.PULL.getCliValue());
        pullPreset.put(Keys.IGNORE_CERT_WARNINGS, Boolean.TRUE.toString());
        pullPreset.put(Keys.FORCE, Boolean.TRUE.toString());
        pullPreset.put(Keys.INCLUDE_CONFIGURATION_MAP, Boolean.TRUE.toString());
        pullPreset.put(Keys.GIT_INIT, Boolean.TRUE.toString());
        defaultPresets.put("Pull (server HTTPS -> ./appdata/mirthsync)", pullPreset);

        Map<String, String> pullClientPreset = createEmptyPreset();
        pullClientPreset.put(Keys.SERVER_URL, serverApiUrl);
        pullClientPreset.put(Keys.EXECUTION_MODE, ExecutionMode.CLIENT.name());
        pullClientPreset.put(Keys.TARGET_DIRECTORY, clientTargetDirectory);
        pullClientPreset.put(Keys.ACTION, ActionOption.PULL.getCliValue());
        pullClientPreset.put(Keys.IGNORE_CERT_WARNINGS, Boolean.TRUE.toString());
        pullClientPreset.put(Keys.FORCE, Boolean.TRUE.toString());
        pullClientPreset.put(Keys.INCLUDE_CONFIGURATION_MAP, Boolean.TRUE.toString());
        pullClientPreset.put(Keys.GIT_INIT, Boolean.TRUE.toString());
        defaultPresets.put("Pull (client HTTPS -> " + clientTargetLabel + ")", pullClientPreset);

        Map<String, String> gitStatus = createEmptyPreset();
        gitStatus.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitStatus.put(Keys.SERVER_URL, "");
        gitStatus.put(Keys.TARGET_DIRECTORY, serverTargetDirectory);
        gitStatus.put(Keys.GIT_COMMAND, "status");
        defaultPresets.put("Git: status (server)", gitStatus);

        Map<String, String> gitLog = createEmptyPreset();
        gitLog.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitLog.put(Keys.SERVER_URL, "");
        gitLog.put(Keys.TARGET_DIRECTORY, serverTargetDirectory);
        gitLog.put(Keys.GIT_COMMAND, "log --oneline --decorate -n 20");
        defaultPresets.put("Git: log (last 20, server)", gitLog);

        Map<String, String> gitDiffWorking = createEmptyPreset();
        gitDiffWorking.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitDiffWorking.put(Keys.SERVER_URL, "");
        gitDiffWorking.put(Keys.TARGET_DIRECTORY, serverTargetDirectory);
        gitDiffWorking.put(Keys.GIT_COMMAND, "diff");
        defaultPresets.put("Git: diff (working tree, server)", gitDiffWorking);

        Map<String, String> gitDiffStaged = createEmptyPreset();
        gitDiffStaged.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitDiffStaged.put(Keys.SERVER_URL, "");
        gitDiffStaged.put(Keys.TARGET_DIRECTORY, serverTargetDirectory);
        gitDiffStaged.put(Keys.GIT_COMMAND, "diff --cached");
        defaultPresets.put("Git: diff --cached (server)", gitDiffStaged);

        Map<String, String> gitStatusClient = createEmptyPreset();
        gitStatusClient.put(Keys.SERVER_URL, "");
        gitStatusClient.put(Keys.EXECUTION_MODE, ExecutionMode.CLIENT.name());
        gitStatusClient.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitStatusClient.put(Keys.TARGET_DIRECTORY, clientTargetDirectory);
        gitStatusClient.put(Keys.GIT_COMMAND, "status");
        defaultPresets.put("Git: status (client)", gitStatusClient);

        Map<String, String> gitLogClient = createEmptyPreset();
        gitLogClient.put(Keys.SERVER_URL, "");
        gitLogClient.put(Keys.EXECUTION_MODE, ExecutionMode.CLIENT.name());
        gitLogClient.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitLogClient.put(Keys.TARGET_DIRECTORY, clientTargetDirectory);
        gitLogClient.put(Keys.GIT_COMMAND, "log --oneline --decorate -n 20");
        defaultPresets.put("Git: log (last 20, client)", gitLogClient);

        Map<String, String> gitDiffWorkingClient = createEmptyPreset();
        gitDiffWorkingClient.put(Keys.SERVER_URL, "");
        gitDiffWorkingClient.put(Keys.EXECUTION_MODE, ExecutionMode.CLIENT.name());
        gitDiffWorkingClient.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitDiffWorkingClient.put(Keys.TARGET_DIRECTORY, clientTargetDirectory);
        gitDiffWorkingClient.put(Keys.GIT_COMMAND, "diff");
        defaultPresets.put("Git: diff (working tree, client)", gitDiffWorkingClient);

        Map<String, String> gitDiffStagedClient = createEmptyPreset();
        gitDiffStagedClient.put(Keys.SERVER_URL, "");
        gitDiffStagedClient.put(Keys.EXECUTION_MODE, ExecutionMode.CLIENT.name());
        gitDiffStagedClient.put(Keys.ACTION, ActionOption.GIT.getCliValue());
        gitDiffStagedClient.put(Keys.TARGET_DIRECTORY, clientTargetDirectory);
        gitDiffStagedClient.put(Keys.GIT_COMMAND, "diff --cached");
        defaultPresets.put("Git: diff --cached (client)", gitDiffStagedClient);
    }

    /**
     * --deploy-new only means something with --deploy-changed (mirthsync warns
     * and ignores it otherwise). Clearing it when disabled keeps what is
     * persisted equal to what the panel shows.
     */
    private void syncDeployNewEnabled() {
        boolean enabled = deployChangedCheckbox.isSelected();
        deployNewCheckbox.setEnabled(enabled);
        if (!enabled) {
            deployNewCheckbox.setSelected(false);
        }
    }

    private Map<String, String> createEmptyPreset() {
        Map<String, String> preset = new LinkedHashMap<>();
        preset.put(Keys.SERVER_URL, "");
        preset.put(Keys.EXECUTION_MODE, ExecutionMode.SERVER.name());
        preset.put(Keys.IGNORE_CERT_WARNINGS, Boolean.FALSE.toString());
        preset.put(Keys.ACTION, ActionOption.PULL.getCliValue());
        preset.put(Keys.TARGET_DIRECTORY, "");
        preset.put(Keys.DISK_MODE, DiskModeOption.CODE.getCliValue());
        preset.put(Keys.RESTRICT_PATH, "");
        preset.put(Keys.VERBOSITY, "0");
        preset.put(Keys.FORCE, Boolean.FALSE.toString());
        preset.put(Keys.INCLUDE_CONFIGURATION_MAP, Boolean.FALSE.toString());
        preset.put(Keys.SKIP_DISABLED, Boolean.FALSE.toString());
        preset.put(Keys.DEPLOY, Boolean.FALSE.toString());
        preset.put(Keys.DEPLOY_ALL, Boolean.FALSE.toString());
        preset.put(Keys.DEPLOY_CHANGED, Boolean.FALSE.toString());
        preset.put(Keys.DEPLOY_NEW, Boolean.FALSE.toString());
        preset.put(Keys.INTERACTIVE, Boolean.FALSE.toString());
        preset.put(Keys.AUTO_COMMIT, Boolean.FALSE.toString());
        preset.put(Keys.GIT_INIT, Boolean.FALSE.toString());
        preset.put(Keys.DELETE_ORPHANED, Boolean.FALSE.toString());
        preset.put(Keys.COMMIT_MESSAGE, "mirthsync commit");
        preset.put(Keys.GIT_AUTHOR, "");
        preset.put(Keys.GIT_EMAIL, "");
        preset.put(Keys.GIT_COMMAND, "");
        return preset;
    }

    private String defaultServerApiUrl() {
        String serverUrl = PlatformUI.SERVER_URL;
        if (isBlank(serverUrl)) {
            serverUrl = "https://localhost:8443";
        }
        return ensureVersionedApiUrl(serverUrl);
    }

    private String stripTrailingSlashes(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private String stripLeadingSlashes(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = 0;
        while (start < value.length() && value.charAt(start) == '/') {
            start++;
        }
        return value.substring(start);
    }

    private String ensureVersionedApiUrl(String serverUrl) {
        if (serverUrl == null) {
            return "";
        }
        String trimmed = serverUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String version = PlatformUI.SERVER_VERSION == null ? "" : PlatformUI.SERVER_VERSION.trim();
        String normalized = stripTrailingSlashes(trimmed);
        int apiIndex = normalized.indexOf("/api");

        if (apiIndex < 0) {
            if (version.isEmpty()) {
                return normalized + "/api";
            }
            return normalized + "/api/" + version;
        }

        String prefix = stripTrailingSlashes(normalized.substring(0, apiIndex));
        String suffix = stripLeadingSlashes(normalized.substring(apiIndex + 4));
        StringBuilder builder = new StringBuilder();
        if (!prefix.isEmpty()) {
            builder.append(prefix);
        }
        builder.append("/api");

        if (version.isEmpty()) {
            if (!suffix.isEmpty()) {
                builder.append("/").append(suffix);
            }
            return builder.toString();
        }

        if (suffix.isEmpty()) {
            builder.append("/").append(version);
            return builder.toString();
        }

        String[] segments = suffix.split("/", 2);
        String firstSegment = segments[0];
        String remainder = segments.length > 1 ? "/" + segments[1] : "";

        if (firstSegment.equals(version)) {
            builder.append("/").append(firstSegment).append(remainder);
            return builder.toString();
        }

        if (firstSegment.matches("\\d+(\\.\\d+)*")) {
            builder.append("/").append(version).append(remainder);
            return builder.toString();
        }

        builder.append("/").append(version).append("/").append(suffix);
        return builder.toString();
    }

    private String defaultClientTargetDirectory() {
        String userHome = System.getProperty("user.home");
        if (isBlank(userHome)) {
            return "./mirthsync";
        }
        try {
            return Paths.get(userHome, "mirthsync").toString();
        } catch (Exception ignored) {
            return userHome + File.separator + "mirthsync";
        }
    }

    private String abbreviateHomePath(String path) {
        if (isBlank(path)) {
            return path;
        }
        String userHome = System.getProperty("user.home");
        if (isBlank(userHome)) {
            return path;
        }
        String normalizedHome = stripTrailingSlashes(userHome);
        if (path.equals(normalizedHome)) {
            return "~";
        }
        if (path.startsWith(normalizedHome + File.separator)) {
            return "~" + path.substring(normalizedHome.length());
        }
        return path;
    }

    private void refreshPresetDropdown() {
        if (presetComboBox == null) {
            return;
        }

        Object currentSelection = presetComboBox.getSelectedItem();
        populatingPresets = true;
        try {
            presetComboBox.removeAllItems();
            presetComboBox.addItem(PRESET_PLACEHOLDER);

            for (String name : defaultPresets.keySet()) {
                presetComboBox.addItem(name);
            }

            for (String name : userPresets.keySet()) {
                presetComboBox.addItem(name);
            }

            if (currentSelection != null && !PRESET_PLACEHOLDER.equals(currentSelection)) {
                presetComboBox.setSelectedItem(currentSelection);
            } else {
                presetComboBox.setSelectedItem(PRESET_PLACEHOLDER);
            }
        } finally {
            populatingPresets = false;
        }
        updatePresetButtons();
    }

    private void applySelectedPreset() {
        if (presetComboBox == null) {
            return;
        }
        Object selected = presetComboBox.getSelectedItem();
        updatePresetButtons();
        if (populatingPresets) {
            return;
        }
        if (!(selected instanceof String name) || PRESET_PLACEHOLDER.equals(name)) {
            return;
        }

        Map<String, String> preset = userPresets.get(name);
        if (preset == null) {
            preset = defaultPresets.get(name);
        }
        if (preset == null) {
            return;
        }

        applyPresetValues(preset);
    }

    private void promptAndSavePreset() {
        String suggestedName = "";
        Object currentSelection = presetComboBox == null ? null : presetComboBox.getSelectedItem();
        if (currentSelection instanceof String currentName && !PRESET_PLACEHOLDER.equals(currentName)) {
            suggestedName = currentName + " (custom)";
        }

        String input = (String) JOptionPane.showInputDialog(
                this,
                "Preset name:",
                "Save Preset",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                suggestedName
        );
        if (input == null) {
            return;
        }
        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a non-empty preset name.", "Preset", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (PRESET_PLACEHOLDER.equals(name)) {
            JOptionPane.showMessageDialog(this, "That name is reserved. Choose a different name.", "Preset", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (name.indexOf('\u001F') >= 0) {
            JOptionPane.showMessageDialog(this, "Preset name cannot contain control characters.", "Preset", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (defaultPresets.containsKey(name)) {
            JOptionPane.showMessageDialog(this, "A default preset already uses that name. Choose a different name.", "Preset", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Map<String, String> snapshot = snapshotCurrentSettings();
        userPresets.put(name, snapshot);
        refreshPresetDropdown();
        presetComboBox.setSelectedItem(name);
        updatePresetButtons();
        setSaveEnabled(true);
    }

    private void deleteSelectedPreset() {
        if (presetComboBox == null) {
            return;
        }
        Object selected = presetComboBox.getSelectedItem();
        if (!(selected instanceof String name) || !userPresets.containsKey(name)) {
            return;
        }

        int response = JOptionPane.showConfirmDialog(
                this,
                "Delete preset \"" + name + "\"?",
                "Delete Preset",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (response != JOptionPane.YES_OPTION) {
            return;
        }

        userPresets.remove(name);
        refreshPresetDropdown();
        presetComboBox.setSelectedItem(PRESET_PLACEHOLDER);
        updatePresetButtons();
        setSaveEnabled(true);
    }

    private void updatePresetButtons() {
        if (presetComboBox == null || deletePresetButton == null) {
            return;
        }
        Object selected = presetComboBox.getSelectedItem();
        boolean deletable = selected instanceof String name && userPresets.containsKey(name);
        deletePresetButton.setEnabled(deletable);
    }

    private Map<String, String> snapshotCurrentSettings() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put(Keys.SERVER_URL, ensureVersionedApiUrl(textOrEmpty(serverUrlField)));
        snapshot.put(Keys.EXECUTION_MODE, getSelectedExecutionMode().name());
        snapshot.put(Keys.IGNORE_CERT_WARNINGS, Boolean.toString(ignoreCertWarningsCheckbox.isSelected()));

        ActionOption actionOption = (ActionOption) actionComboBox.getSelectedItem();
        snapshot.put(Keys.ACTION, actionOption == null ? "" : actionOption.getCliValue());

        snapshot.put(Keys.TARGET_DIRECTORY, textOrEmpty(targetDirectoryField));

        DiskModeOption diskModeOption = (DiskModeOption) diskModeComboBox.getSelectedItem();
        snapshot.put(Keys.DISK_MODE, diskModeOption == null ? "" : diskModeOption.getCliValue());

        snapshot.put(Keys.RESTRICT_PATH, textOrEmpty(restrictPathField));
        snapshot.put(Keys.VERBOSITY, Objects.toString(verbositySpinner.getValue(), "0"));
        snapshot.put(Keys.FORCE, Boolean.toString(forceCheckbox.isSelected()));
        snapshot.put(Keys.INCLUDE_CONFIGURATION_MAP, Boolean.toString(includeConfigurationMapCheckbox.isSelected()));
        snapshot.put(Keys.SKIP_DISABLED, Boolean.toString(skipDisabledCheckbox.isSelected()));
        snapshot.put(Keys.DEPLOY, Boolean.toString(deployCheckbox.isSelected()));
        snapshot.put(Keys.DEPLOY_ALL, Boolean.toString(deployAllCheckbox.isSelected()));
        snapshot.put(Keys.DEPLOY_CHANGED, Boolean.toString(deployChangedCheckbox.isSelected()));
        snapshot.put(Keys.DEPLOY_NEW, Boolean.toString(deployNewCheckbox.isSelected()));
        snapshot.put(Keys.INTERACTIVE, Boolean.toString(interactiveCheckbox.isSelected()));
        snapshot.put(Keys.AUTO_COMMIT, Boolean.toString(autoCommitCheckbox.isSelected()));
        snapshot.put(Keys.GIT_INIT, Boolean.toString(gitInitCheckbox.isSelected()));
        snapshot.put(Keys.DELETE_ORPHANED, Boolean.toString(deleteOrphanedCheckbox.isSelected()));
        snapshot.put(Keys.COMMIT_MESSAGE, textOrEmpty(commitMessageField));
        snapshot.put(Keys.GIT_AUTHOR, textOrEmpty(gitAuthorField));
        snapshot.put(Keys.GIT_EMAIL, textOrEmpty(gitEmailField));
        snapshot.put(Keys.GIT_COMMAND, textOrEmpty(gitCommandField));
        return snapshot;
    }

    private void applyPresetValues(Map<String, String> preset) {
        boolean previous = updatingFromModel;
        updatingFromModel = true;
        try {
            setExecutionMode(parseExecutionMode(preset.get(Keys.EXECUTION_MODE)));
            String presetServerUrl = ensureVersionedApiUrl(preset.getOrDefault(Keys.SERVER_URL, ""));
            if (!Objects.equals(presetServerUrl, preset.get(Keys.SERVER_URL))) {
                preset.put(Keys.SERVER_URL, presetServerUrl);
            }
            serverUrlField.setText(presetServerUrl);
            ignoreCertWarningsCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.IGNORE_CERT_WARNINGS, "false")));

            String actionValue = preset.get(Keys.ACTION);
            if (!isBlank(actionValue)) {
                for (ActionOption option : ActionOption.values()) {
                    if (actionValue.equalsIgnoreCase(option.getCliValue())) {
                        actionComboBox.setSelectedItem(option);
                        break;
                    }
                }
            } else {
                actionComboBox.setSelectedItem(ActionOption.PULL);
            }

            targetDirectoryField.setText(preset.getOrDefault(Keys.TARGET_DIRECTORY, ""));

            String diskMode = preset.get(Keys.DISK_MODE);
            if (!isBlank(diskMode)) {
                for (DiskModeOption option : DiskModeOption.values()) {
                    if (diskMode.equalsIgnoreCase(option.getCliValue())) {
                        diskModeComboBox.setSelectedItem(option);
                        break;
                    }
                }
            } else {
                diskModeComboBox.setSelectedItem(DiskModeOption.CODE);
            }

            restrictPathField.setText(preset.getOrDefault(Keys.RESTRICT_PATH, ""));

            int verbosity = parseIntOrDefault(preset.get(Keys.VERBOSITY), 0);
            verbositySpinner.setValue(Math.max(0, verbosity));

            forceCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.FORCE, "false")));
            includeConfigurationMapCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.INCLUDE_CONFIGURATION_MAP, "false")));
            skipDisabledCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.SKIP_DISABLED, "false")));
            deployCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.DEPLOY, "false")));
            deployAllCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.DEPLOY_ALL, "false")));
            deployChangedCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.DEPLOY_CHANGED, "false")));
            deployNewCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.DEPLOY_NEW, "false")));
            syncDeployNewEnabled();
            interactiveCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.INTERACTIVE, "false")));
            autoCommitCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.AUTO_COMMIT, "false")));
            gitInitCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.GIT_INIT, "false")));
            deleteOrphanedCheckbox.setSelected(Boolean.parseBoolean(preset.getOrDefault(Keys.DELETE_ORPHANED, "false")));

            commitMessageField.setText(preset.getOrDefault(Keys.COMMIT_MESSAGE, "mirthsync commit"));

            String author = preset.getOrDefault(Keys.GIT_AUTHOR, "");
            gitAuthorField.setText(author);

            String email = preset.getOrDefault(Keys.GIT_EMAIL, "");
            gitEmailField.setText(email);

            gitCommandField.setText(preset.getOrDefault(Keys.GIT_COMMAND, ""));

            populateUserGitInfo();
            updateActionVisibility();
        } finally {
            updatingFromModel = previous;
        }

        setSaveEnabled(true);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void loadUserPresets(Properties properties) {
        userPresets.clear();
        if (properties == null) {
            return;
        }

        String namesValue = properties.getProperty(Keys.PRESET_NAMES);
        if (isBlank(namesValue)) {
            return;
        }

        String[] names = namesValue.split(Pattern.quote(PRESET_NAME_DELIMITER), -1);
        for (String name : names) {
            if (isBlank(name)) {
                continue;
            }
            String encodedName = encodePresetName(name);
            String data = properties.getProperty(Keys.PRESET_PREFIX + encodedName);
            Map<String, String> presetValues = deserializePresetValues(data);
            if (!presetValues.isEmpty()) {
                userPresets.put(name, presetValues);
            } else {
                userPresets.put(name, createEmptyPreset());
            }
        }
    }

    private void storeUserPresets(Properties properties) {
        if (properties == null) {
            return;
        }

        List<String> keysToRemove = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(Keys.PRESET_PREFIX)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            properties.remove(key);
        }

        if (userPresets.isEmpty()) {
            properties.remove(Keys.PRESET_NAMES);
            return;
        }

        StringJoiner nameJoiner = new StringJoiner(PRESET_NAME_DELIMITER);
        for (Map.Entry<String, Map<String, String>> entry : userPresets.entrySet()) {
            String name = entry.getKey();
            nameJoiner.add(name);
            String encodedName = encodePresetName(name);
            properties.setProperty(Keys.PRESET_PREFIX + encodedName, serializePresetValues(entry.getValue()));
        }

        properties.setProperty(Keys.PRESET_NAMES, nameJoiner.toString());
    }

    private String encodePresetName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    private String serializePresetValues(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner(PRESET_DATA_SEPARATOR);
        for (String key : SETTING_KEYS) {
            String value = values.getOrDefault(key, "");
            String encodedKey = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(key.getBytes(StandardCharsets.UTF_8));
            String encodedValue = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(value.getBytes(StandardCharsets.UTF_8));
            joiner.add(encodedKey + PRESET_DATA_KEY_VALUE_SEPARATOR + encodedValue);
        }
        return joiner.toString();
    }

    private Map<String, String> deserializePresetValues(String data) {
        Map<String, String> values = new LinkedHashMap<>();
        if (isBlank(data)) {
            return values;
        }

        String[] entries = data.split(Pattern.quote(PRESET_DATA_SEPARATOR), -1);
        for (String entry : entries) {
            if (isBlank(entry)) {
                continue;
            }
            int separatorIndex = entry.indexOf(PRESET_DATA_KEY_VALUE_SEPARATOR);
            if (separatorIndex <= 0) {
                continue;
            }
            String keyPart = entry.substring(0, separatorIndex);
            String valuePart = entry.substring(separatorIndex + PRESET_DATA_KEY_VALUE_SEPARATOR.length());
            try {
                String key = new String(Base64.getUrlDecoder().decode(keyPart), StandardCharsets.UTF_8);
                String value = new String(Base64.getUrlDecoder().decode(valuePart), StandardCharsets.UTF_8);
                if (SETTING_KEYS.contains(key)) {
                    values.put(key, value);
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed entries
            }
        }
        return values;
    }


    private void updateActionVisibility() {
        if (gitCommandLabel == null || gitCommandField == null || actionComboBox == null) {
            return;
        }

        ActionOption selectedOption = (ActionOption) actionComboBox.getSelectedItem();
        boolean gitSelected = selectedOption == ActionOption.GIT;
        gitCommandLabel.setVisible(gitSelected);
        gitCommandField.setVisible(gitSelected);
        updateCredentialControls(selectedOption);

        Container parent = gitCommandLabel.getParent();
        if (parent != null) {
            parent.revalidate();
            parent.repaint();
        }
    }

    private void updateCredentialControls(ActionOption selectedOption) {
        boolean credentialsEnabled = selectedOption == null || selectedOption.requiresCredentials();
        if (serverUrlField != null) {
            serverUrlField.setEnabled(credentialsEnabled);
        }
        if (remoteUsernameField != null) {
            remoteUsernameField.setEnabled(credentialsEnabled);
        }
        if (remotePasswordField != null) {
            remotePasswordField.setEnabled(credentialsEnabled);
        }
        if (ignoreCertWarningsCheckbox != null) {
            ignoreCertWarningsCheckbox.setEnabled(credentialsEnabled);
        }
    }

    private void updateExecutionControls() {
        boolean localSelected = clientExecutionRadio != null && clientExecutionRadio.isSelected();
        if (targetBrowseButton != null) {
            targetBrowseButton.setEnabled(localSelected);
        }
    }

    private void browseForTargetDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        String current = textOrEmpty(targetDirectoryField);
        if (!current.isEmpty()) {
            File currentDir = new File(current);
            if (currentDir.exists()) {
                chooser.setCurrentDirectory(currentDir.isDirectory() ? currentDir : currentDir.getParentFile());
                chooser.setSelectedFile(currentDir);
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                targetDirectoryField.setText(selected.getAbsolutePath());
                markDirty();
            }
        }
    }

    private ExecutionMode getSelectedExecutionMode() {
        return clientExecutionRadio != null && clientExecutionRadio.isSelected()
                ? ExecutionMode.CLIENT
                : ExecutionMode.SERVER;
    }

    private void setExecutionMode(ExecutionMode mode) {
        ExecutionMode resolved = mode == null ? ExecutionMode.SERVER : mode;
        if (resolved == ExecutionMode.CLIENT) {
            if (clientExecutionRadio != null) {
                clientExecutionRadio.setSelected(true);
            }
        } else if (serverExecutionRadio != null) {
            serverExecutionRadio.setSelected(true);
        }
        updateExecutionControls();
    }

    private ExecutionMode parseExecutionMode(String value) {
        if (value == null || value.isBlank()) {
            return ExecutionMode.SERVER;
        }
        try {
            return ExecutionMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ExecutionMode.SERVER;
        }
    }

    private void executeMirthsync() {
        List<String> arguments;
        try {
            arguments = buildExecutionArguments();
        } catch (IllegalArgumentException ex) {
            appendOutput("Validation failed: " + ex.getMessage());
            return;
        }

        executeButton.setEnabled(false);
        appendOutput("Running mirthsync...");

        List<String> finalArguments = new ArrayList<>(arguments);
        boolean runOnServer = getSelectedExecutionMode() == ExecutionMode.SERVER;
        SwingWorker<Map<String, Object>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                if (runOnServer) {
                    return invokeMirthsync(finalArguments);
                }
                List<String> localArgs = new ArrayList<>(finalArguments);
                ensureTokenForLocal(localArgs);
                return runMirthsyncLocally(localArgs);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> response = get();
                    String formatted = formatExecutionResponse(response);
                    appendOutput(formatted);
                } catch (Exception ex) {
                    appendOutput("MirthSync execution failed:\n" + buildFriendlyError(ex));
                } finally {
                    executeButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private List<String> buildExecutionArguments() {
        ActionOption actionOption = (ActionOption) actionComboBox.getSelectedItem();
        if (actionOption == null) {
            throw new IllegalArgumentException("Select an action to run.");
        }

        List<String> args = new ArrayList<>();

        String serverUrl = ensureVersionedApiUrl(textOrNull(serverUrlField));
        String username = textOrNull(remoteUsernameField);

        char[] passwordChars = remotePasswordField.getPassword();
        String password = passwordChars == null ? "" : new String(passwordChars);
        if (passwordChars != null) {
            Arrays.fill(passwordChars, '\0');
        }

        boolean hasUsername = !isBlank(username);
        boolean hasPassword = !isBlank(password);

        if (hasUsername ^ hasPassword) {
            throw new IllegalArgumentException("Username and password must both be provided.");
        }

        if (actionOption.requiresCredentials()) {
            if (isBlank(serverUrl)) {
                throw new IllegalArgumentException("--server is required for this action.");
            }
        }

        addOption(args, "--server", serverUrl);
        if (hasUsername && hasPassword) {
            addOption(args, "--username", username);
            addOption(args, "--password", password);
        }

        addFlag(args, ignoreCertWarningsCheckbox.isSelected(), "--ignore-cert-warnings");

        int verbosity = Math.max(0, ((Number) verbositySpinner.getValue()).intValue());
        for (int i = 0; i < verbosity; i++) {
            args.add("-v");
        }

        addFlag(args, forceCheckbox.isSelected(), "--force");

        String targetDirectory = textOrNull(targetDirectoryField);
        if (isBlank(targetDirectory)) {
            throw new IllegalArgumentException("--target is required.");
        }
        addOption(args, "--target", targetDirectory);

        DiskModeOption diskModeOption = (DiskModeOption) diskModeComboBox.getSelectedItem();
        if (diskModeOption != null) {
            addOption(args, "--disk-mode", diskModeOption.getCliValue());
        }

        addOption(args, "--restrict-to-path", textOrNull(restrictPathField));

        addFlag(args, includeConfigurationMapCheckbox.isSelected(), "--include-configuration-map");
        addFlag(args, skipDisabledCheckbox.isSelected(), "--skip-disabled");
        addFlag(args, deployCheckbox.isSelected(), "--deploy");
        addFlag(args, deployAllCheckbox.isSelected(), "--deploy-all");
        addFlag(args, deployChangedCheckbox.isSelected(), "--deploy-changed");
        addFlag(args, deployChangedCheckbox.isSelected() && deployNewCheckbox.isSelected(), "--deploy-new");
        addFlag(args, interactiveCheckbox.isSelected(), "--interactive");
        addOption(args, "--commit-message", textOrNull(commitMessageField));
        addOption(args, "--git-author", textOrNull(gitAuthorField));
        addOption(args, "--git-email", textOrNull(gitEmailField));
        addFlag(args, autoCommitCheckbox.isSelected(), "--auto-commit");
        addFlag(args, gitInitCheckbox.isSelected(), "--git-init");
        addFlag(args, deleteOrphanedCheckbox.isSelected(), "--delete-orphaned");

        args.add(actionOption.getCliValue());

        if (actionOption.isGit()) {
            List<String> gitArgs = parseCommandLine(gitCommandField.getText());
            if (gitArgs.isEmpty()) {
                throw new IllegalArgumentException("Enter a git subcommand to run.");
            }
            normalizeGitArguments(gitArgs);
            args.addAll(gitArgs);
        }

        return args;
    }

    private Map<String, Object> invokeMirthsync(List<String> arguments) throws Exception {
        try {
            MirthSyncApi servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(MirthSyncApi.class);
            return servlet.executeMirthsync(arguments);
        } catch (ClientException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    private Map<String, Object> runMirthsyncLocally(List<String> arguments) throws Exception {
        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();

        int exitCode;
        try (PrintWriter outWriter = new PrintWriter(stdout, true);
             PrintWriter errWriter = new PrintWriter(stderr, true)) {
            exitCode = mirthsync.core.mainFunc(arguments.toArray(new String[0]), outWriter, errWriter);
            outWriter.flush();
            errWriter.flush();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("command", redactSensitiveArgs(arguments));
        response.put("stdout", sanitizeConsoleOutput(stdout.toString()));
        response.put("stderr", sanitizeConsoleOutput(stderr.toString()));
        response.put("exitCode", exitCode);
        return response;
    }

    private void ensureTokenForLocal(List<String> args) throws Exception {
        boolean hasToken = false;
        boolean hasUsername = false;
        boolean hasPassword = false;
        String serverUrl = null;

        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--token".equals(arg)) {
                hasToken = true;
                break;
            }
            if ("--username".equals(arg) || "-u".equals(arg)) {
                hasUsername = true;
                i++;
                continue;
            }
            if ("--password".equals(arg) || "-p".equals(arg)) {
                hasPassword = true;
                i++;
                continue;
            }
            if ("--server".equals(arg) || "-s".equals(arg)) {
                if (i + 1 < args.size()) {
                    serverUrl = args.get(i + 1);
                }
                i++;
            }
        }

        if (hasToken || (hasUsername && hasPassword) || isBlank(serverUrl)) {
            return;
        }

        try {
            MirthSyncApi servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(MirthSyncApi.class);
            String token = servlet.getSessionToken();
            if (isBlank(token)) {
                throw new Exception("Unable to retrieve session token; provide username and password instead.");
            }
            args.add("--token");
            args.add(token);
        } catch (ClientException ce) {
            throw new Exception("Unable to retrieve session token: " + ce.getMessage(), ce);
        }
    }

    private String formatExecutionResponse(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "MirthSync execution failed: empty response.";
        }

        StringBuilder builder = new StringBuilder("=== mirthsync execution ===");

        List<String> command = extractCommand(response.get("command"));
        if (!command.isEmpty()) {
            builder.append("\n$ mirthsync ").append(formatCommand(command));
        }

        String stdout = sanitizeConsoleOutput(toStringValue(response.get("stdout")));
        String stderr = sanitizeConsoleOutput(toStringValue(response.get("stderr")));

        if (stdout != null && !stdout.isBlank()) {
            builder.append("\n\nstdout:\n").append(stdout.stripTrailing());
        }

        if (stderr != null && !stderr.isBlank()) {
            builder.append("\n\nstderr:\n").append(stderr.stripTrailing());
        }

        if ((stdout == null || stdout.isBlank()) && (stderr == null || stderr.isBlank())) {
            builder.append("\n\n(no output)");
        }

        builder.append("\n\nexit code: ").append(extractExitCode(response.get("exitCode")));

        return builder.toString().trim();
    }

    private String sanitizeConsoleOutput(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        normalized = ANSI_OSC_PATTERN.matcher(normalized).replaceAll("");
        normalized = ANSI_ESCAPE_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replace("\u001B", "");
        normalized = INVALID_CONTROL_PATTERN.matcher(normalized).replaceAll("");
        if (normalized.trim().isEmpty()) {
            return null;
        }
        return normalized;
    }

    private String buildFriendlyError(Throwable throwable) {
        Throwable root = unwrapThrowable(throwable);
        String message = root == null ? "" : sanitizeForDisplay(root.getMessage());
        String lower = message.toLowerCase(Locale.ENGLISH);

        if (lower.contains("character reference \"&#x1b\"") || lower.contains("invalid xml character")) {
            String details = message.isBlank() ? "" : "\n\nDetails: " + message;
            return "Git command failed because the output could not be formatted safely. "
                    + "This often means the target directory is not an initialized git repository yet. "
                    + "Run a pull or enable \"Initialize git repository\" and try again."
                    + details;
        }

        if (lower.contains("not a git repository")) {
            String details = message.isBlank() ? "" : "\n\nDetails: " + message;
            return "Git command failed: the target directory is not a git repository yet. "
                    + "Run a pull or enable \"Initialize git repository\" under Flags & Toggles."
                    + details;
        }

        if (message.isBlank() && root != null) {
            message = sanitizeForDisplay(root.toString());
        }

        if (message.isBlank()) {
            return "Unknown error occurred while running mirthsync.";
        }

        return message;
    }

    private String sanitizeForDisplay(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') {
                sanitized.append('?');
            } else {
                sanitized.append(ch);
            }
        }
        return sanitized.toString().trim();
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }


    private String formatCommand(List<String> args) {
        if (args == null || args.isEmpty()) {
            return "";
        }

        List<String> formatted = new ArrayList<>();
        boolean maskNext = false;

        for (String arg : args) {
            if (maskNext) {
                formatted.add("****");
                maskNext = false;
                continue;
            }

            if (arg == null) {
                continue;
            }

            if ("--password".equals(arg) || "-p".equals(arg) || "--token".equals(arg)) {
                formatted.add(arg);
                maskNext = true;
                continue;
            }

            formatted.add(quoteIfNeeded(arg));
        }

        if (maskNext) {
            formatted.add("****");
        }

        return String.join(" ", formatted);
    }

    private List<String> redactSensitiveArgs(List<String> args) {
        List<String> sanitized = new ArrayList<>();
        if (args == null) {
            return sanitized;
        }
        boolean maskNext = false;
        for (String arg : args) {
            if (maskNext) {
                sanitized.add("****");
                maskNext = false;
                continue;
            }
            if ("--password".equals(arg) || "-p".equals(arg) || "--token".equals(arg)) {
                sanitized.add(arg);
                maskNext = true;
                continue;
            }
            sanitized.add(arg);
        }
        if (maskNext) {
            sanitized.add("****");
        }
        return sanitized;
    }

    private String quoteIfNeeded(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }

        boolean needsQuotes = token.contains(" ") || token.contains("\"") || token.contains("'");
        if (!needsQuotes) {
            return token;
        }

        String escaped = token.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    private List<String> extractCommand(Object commandObj) {
        List<String> command = new ArrayList<>();
        if (commandObj instanceof List<?> list) {
            for (Object value : list) {
                if (value != null) {
                    command.add(value.toString());
                }
            }
        }
        return redactSensitiveArgs(command);
    }

    private String textOrEmpty(JTextField field) {
        String value = field == null ? null : field.getText();
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String textOrNull(JTextField field) {
        String value = textOrEmpty(field);
        return value.isEmpty() ? null : value;
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = Objects.toString(value, null);
        return text == null ? null : text;
    }

    private int extractExitCode(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return -1;
    }

    private void addOption(List<String> args, String option, String value) {
        if (!isBlank(value)) {
            args.add(option);
            args.add(value);
        }
    }

    private void addFlag(List<String> args, boolean enabled, String flag) {
        if (enabled) {
            args.add(flag);
        }
    }

    private void registerDocumentListener(JTextComponent component) {
        if (component == null) {
            return;
        }
        component.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markDirty();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                markDirty();
            }
        });
    }

    private void registerCheckboxListener(AbstractButton button) {
        if (button != null) {
            button.addActionListener(e -> markDirty());
        }
    }

    private void markDirty() {
        if (!updatingFromModel) {
            setSaveEnabled(true);
        }
    }

    private List<String> parseCommandLine(String commandText) {
        List<String> tokens = new ArrayList<>();
        if (commandText == null) {
            return tokens;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < commandText.length(); i++) {
            char ch = commandText.charAt(i);

            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }

            if (ch == '\\') {
                escaping = true;
                continue;
            }

            if (ch == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (ch == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (Character.isWhitespace(ch) && !inSingleQuotes && !inDoubleQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private void normalizeGitArguments(List<String> gitArgs) {
        if (gitArgs == null || gitArgs.isEmpty()) {
            return;
        }
        if (!containsColorDirective(gitArgs)) {
            gitArgs.add("--no-color");
        }
    }

    private boolean containsColorDirective(List<String> gitArgs) {
        for (int i = 0; i < gitArgs.size(); i++) {
            String token = gitArgs.get(i);
            if ("--no-color".equalsIgnoreCase(token)) {
                return true;
            }
            if ("--color=never".equalsIgnoreCase(token)) {
                return true;
            }
            if ("--color".equalsIgnoreCase(token) && i + 1 < gitArgs.size()
                    && "never".equalsIgnoreCase(gitArgs.get(i + 1))) {
                return true;
            }
            if ("-c".equals(token) && i + 1 < gitArgs.size()
                    && gitArgs.get(i + 1).toLowerCase(Locale.ENGLISH).startsWith("color.")) {
                return true;
            }
            if (token.startsWith("-c") && token.toLowerCase(Locale.ENGLISH).contains("color.")) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class OutputStyles {
        final Style normal;
        final Style diffAdd;
        final Style diffRemove;
        final Style diffHeader;
        final Style diffMeta;
        final Style diffHunk;
        final Style stderr;

        private OutputStyles(Style normal,
                             Style diffAdd,
                             Style diffRemove,
                             Style diffHeader,
                             Style diffMeta,
                             Style diffHunk,
                             Style stderr) {
            this.normal = normal;
            this.diffAdd = diffAdd;
            this.diffRemove = diffRemove;
            this.diffHeader = diffHeader;
            this.diffMeta = diffMeta;
            this.diffHunk = diffHunk;
            this.stderr = stderr;
        }
    }

    private static final class Keys {
        static final String SERVER_URL = "serverUrl";
        static final String EXECUTION_MODE = "executionMode";
        static final String IGNORE_CERT_WARNINGS = "ignoreCertWarnings";
        static final String ACTION = "action";
        static final String TARGET_DIRECTORY = "targetDirectory";
        static final String DISK_MODE = "diskMode";
        static final String RESTRICT_PATH = "restrictPath";
        static final String VERBOSITY = "verbosity";
        static final String FORCE = "force";
        static final String INCLUDE_CONFIGURATION_MAP = "includeConfigurationMap";
        static final String SKIP_DISABLED = "skipDisabled";
        static final String DEPLOY = "deploy";
        static final String DEPLOY_ALL = "deployAll";
        static final String DEPLOY_CHANGED = "deployChanged";
        static final String DEPLOY_NEW = "deployNew";
        static final String INTERACTIVE = "interactive";
        static final String AUTO_COMMIT = "autoCommit";
        static final String GIT_INIT = "gitInit";
        static final String DELETE_ORPHANED = "deleteOrphaned";
        static final String COMMIT_MESSAGE = "commitMessage";
        static final String GIT_AUTHOR = "gitAuthor";
        static final String GIT_EMAIL = "gitEmail";
        static final String GIT_COMMAND = "gitCommand";
        static final String PRESET_NAMES = "preset.names";
        static final String PRESET_PREFIX = "preset.";

        private Keys() {}
    }

    private TitledBorder createSectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SECTION_BORDER_COLOR),
                title,
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", Font.BOLD, 11)
        );
    }

    private MirthCheckBox createCheckBox(String label) {
        MirthCheckBox checkBox = new MirthCheckBox(label);
        checkBox.setBackground(Color.WHITE);
        return checkBox;
    }

    private void runMirthsyncHelp() {
        mirthsyncHelpButton.setEnabled(false);
        appendOutput("Fetching MirthSync help...");
        new Thread(() -> {
            String dialogText;
            try {
                dialogText = fetchMirthsyncHelp();
                if (dialogText.isBlank()) {
                    dialogText = "(no output)";
                }
            } catch (Exception ex) {
                dialogText = "Error retrieving MirthSync help:\n" + ex.getMessage();
            }

            final String output = dialogText;
            SwingUtilities.invokeLater(() -> {
                mirthsyncHelpButton.setEnabled(true);
                appendOutput("=== MirthSync Help ===\n" + output);

                JTextArea textArea = new JTextArea(output);
                textArea.setEditable(false);
                textArea.setLineWrap(true);
                textArea.setWrapStyleWord(true);

                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(600, 400));

                JOptionPane.showMessageDialog(
                        MainSettingsPanel.this,
                        scrollPane,
                        "MirthSync Help",
                        JOptionPane.INFORMATION_MESSAGE
                );
            });
        }, "mirthsync-help").start();
    }

    private String fetchMirthsyncHelp() throws Exception {
        try {
            MirthSyncApi servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(MirthSyncApi.class);
            return servlet.getMirthsyncHelp();
        } catch (ClientException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    private void appendOutput(String message) {
        String safeMessage = message == null ? "" : message;
        Runnable updater = () -> {
            OutputStyles styles = ensureMainOutputStyles();
            appendStyledText(outputArea, styles, safeMessage, true);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        };

        if (SwingUtilities.isEventDispatchThread()) {
            updater.run();
        } else {
            SwingUtilities.invokeLater(updater);
        }
    }

    private void openOutputInNewWindow() {
        String text = outputArea.getText();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("MirthSync Output");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            JTextPane detachedArea = new JTextPane();
            detachedArea.setEditable(false);
            detachedArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            OutputStyles styles = createOutputStyles(detachedArea);
            renderText(detachedArea, styles, text);

            JScrollPane scrollPane = new JScrollPane(detachedArea);
            frame.add(scrollPane);
            frame.setSize(700, 500);
            frame.setLocationRelativeTo(SwingUtilities.getWindowAncestor(MainSettingsPanel.this));
            frame.setVisible(true);
        });
    }

    private void renderText(JTextPane pane, OutputStyles styles, String text) {
        if (pane == null || styles == null) {
            return;
        }
        pane.setText("");
        if (text == null || text.isEmpty()) {
            pane.setCaretPosition(0);
            return;
        }
        appendStyledText(pane, styles, text, false);
        pane.setCaretPosition(pane.getDocument().getLength());
    }

    private void appendStyledText(JTextPane pane, OutputStyles styles, String message, boolean separate) {
        if (pane == null || styles == null || message == null || message.isEmpty()) {
            return;
        }
        StyledDocument document = pane.getStyledDocument();
        try {
            if (separate && document.getLength() > 0) {
                document.insertString(document.getLength(), "\n\n", styles.normal);
            }
            String normalized = normalizeNewlines(message);
            int index = 0;
            while (index < normalized.length()) {
                int newlineIndex = normalized.indexOf('\n', index);
                String segment;
                if (newlineIndex == -1) {
                    segment = normalized.substring(index);
                    index = normalized.length();
                } else {
                    segment = normalized.substring(index, newlineIndex + 1);
                    index = newlineIndex + 1;
                }
                if (!segment.isEmpty()) {
                    document.insertString(document.getLength(), segment, styleForLine(segment, styles));
                }
            }
        } catch (BadLocationException e) {
            pane.setText(pane.getText() + (pane.getText().isEmpty() ? "" : "\n\n") + message);
        }
    }

    private OutputStyles ensureMainOutputStyles() {
        if (mainOutputStyles == null) {
            mainOutputStyles = createOutputStyles(outputArea);
        }
        return mainOutputStyles;
    }

    private OutputStyles createOutputStyles(JTextPane pane) {
        StyledDocument document = pane.getStyledDocument();
        String styleKey = "mirthsync-" + System.identityHashCode(document);
        Style base = document.addStyle(styleKey + "-base", null);
        StyleConstants.setFontFamily(base, Font.MONOSPACED);
        StyleConstants.setFontSize(base, 12);

        Style normal = document.addStyle(styleKey + "-normal", base);

        Style diffAdd = document.addStyle(styleKey + "-diff-add", base);
        StyleConstants.setForeground(diffAdd, new Color(34, 139, 34));

        Style diffRemove = document.addStyle(styleKey + "-diff-remove", base);
        StyleConstants.setForeground(diffRemove, new Color(205, 92, 92));

        Style diffHeader = document.addStyle(styleKey + "-diff-header", base);
        StyleConstants.setForeground(diffHeader, new Color(70, 130, 180));
        StyleConstants.setBold(diffHeader, true);

        Style diffMeta = document.addStyle(styleKey + "-diff-meta", base);
        StyleConstants.setForeground(diffMeta, new Color(138, 43, 226));

        Style diffHunk = document.addStyle(styleKey + "-diff-hunk", base);
        StyleConstants.setForeground(diffHunk, new Color(199, 123, 72));

        Style stderr = document.addStyle(styleKey + "-stderr", base);
        StyleConstants.setForeground(stderr, new Color(178, 34, 34));
        StyleConstants.setBold(stderr, true);

        return new OutputStyles(normal, diffAdd, diffRemove, diffHeader, diffMeta, diffHunk, stderr);
    }

    private Style styleForLine(String line, OutputStyles styles) {
        if (line == null) {
            return styles.normal;
        }
        if (line.startsWith("+++")) {
            return styles.diffHeader;
        }
        if (line.startsWith("---")) {
            return styles.diffHeader;
        }
        if (line.startsWith("@@")) {
            return styles.diffHunk;
        }
        if (line.startsWith("diff ") || line.startsWith("index ")) {
            return styles.diffMeta;
        }
        if (line.startsWith("+") && !line.startsWith("+++")) {
            return styles.diffAdd;
        }
        if (line.startsWith("-") && !line.startsWith("---")) {
            return styles.diffRemove;
        }
        String lower = line.trim().toLowerCase(Locale.ENGLISH);
        if (lower.startsWith("stderr:") || lower.startsWith("error")) {
            return styles.stderr;
        }
        return styles.normal;
    }

    private String normalizeNewlines(String message) {
        return message.replace("\r\n", "\n").replace('\r', '\n');
    }

    @Override
    public void doRefresh() {
        updatingFromModel = true;
        Properties properties = new Properties();
        try {
            Properties loaded = PlatformUI.MIRTH_FRAME.mirthClient.getPluginProperties(PluginConstants.PLUGIN_POINTNAME, null);
            if (loaded != null) {
                properties.putAll(loaded);
            }
        } catch (ClientException e) {
            appendOutput("Failed to load saved settings: " + e.getMessage());
        }

        loadUserPresets(properties);
        refreshPresetDropdown();
        presetComboBox.setSelectedItem(PRESET_PLACEHOLDER);
        updatePresetButtons();

        setExecutionMode(parseExecutionMode(properties.getProperty(Keys.EXECUTION_MODE)));
        serverUrlField.setText(ensureVersionedApiUrl(properties.getProperty(Keys.SERVER_URL, "")));
        remoteUsernameField.setText("");
        remotePasswordField.setText("");
        ignoreCertWarningsCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.IGNORE_CERT_WARNINGS, "false")));

        String action = properties.getProperty(Keys.ACTION);
        if (action != null) {
            for (ActionOption option : ActionOption.values()) {
                if (option.getCliValue().equalsIgnoreCase(action)) {
                    actionComboBox.setSelectedItem(option);
                    break;
                }
            }
        }

        targetDirectoryField.setText(properties.getProperty(Keys.TARGET_DIRECTORY, ""));

        String diskMode = properties.getProperty(Keys.DISK_MODE);
        if (diskMode != null) {
            for (DiskModeOption option : DiskModeOption.values()) {
                if (option.getCliValue().equalsIgnoreCase(diskMode)) {
                    diskModeComboBox.setSelectedItem(option);
                    break;
                }
            }
        }

        restrictPathField.setText(properties.getProperty(Keys.RESTRICT_PATH, ""));

        try {
            int verbosity = Integer.parseInt(properties.getProperty(Keys.VERBOSITY, "0"));
            verbositySpinner.setValue(Math.max(0, verbosity));
        } catch (NumberFormatException ignored) {
            verbositySpinner.setValue(0);
        }

        forceCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.FORCE, "false")));
        includeConfigurationMapCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.INCLUDE_CONFIGURATION_MAP, "false")));
        skipDisabledCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.SKIP_DISABLED, "false")));
        deployCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.DEPLOY, "false")));
        deployAllCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.DEPLOY_ALL, "false")));
        deployChangedCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.DEPLOY_CHANGED, "false")));
        deployNewCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.DEPLOY_NEW, "false")));
        syncDeployNewEnabled();
        interactiveCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.INTERACTIVE, "false")));
        autoCommitCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.AUTO_COMMIT, "false")));
        gitInitCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.GIT_INIT, "false")));
        deleteOrphanedCheckbox.setSelected(Boolean.parseBoolean(properties.getProperty(Keys.DELETE_ORPHANED, "false")));

        commitMessageField.setText(properties.getProperty(Keys.COMMIT_MESSAGE, "mirthsync commit"));
        gitAuthorField.setText(properties.getProperty(Keys.GIT_AUTHOR, ""));
        gitEmailField.setText(properties.getProperty(Keys.GIT_EMAIL, ""));

        gitCommandField.setText(properties.getProperty(Keys.GIT_COMMAND, ""));
        populateUserGitInfo();
        updateActionVisibility();
        updatingFromModel = false;
        setSaveEnabled(false);
    }

    @Override
    public boolean doSave() {
        setSaveEnabled(false);
        Properties properties = buildProperties();
        executeSave(properties);
        return true;
    }

    private Properties buildProperties() {
        Properties properties = new Properties();

        properties.setProperty(Keys.SERVER_URL, ensureVersionedApiUrl(textOrEmpty(serverUrlField)));
        properties.setProperty(Keys.EXECUTION_MODE, getSelectedExecutionMode().name());
        properties.setProperty(Keys.IGNORE_CERT_WARNINGS, Boolean.toString(ignoreCertWarningsCheckbox.isSelected()));

        ActionOption actionOption = (ActionOption) actionComboBox.getSelectedItem();
        if (actionOption != null) {
            properties.setProperty(Keys.ACTION, actionOption.getCliValue());
        }

        properties.setProperty(Keys.TARGET_DIRECTORY, textOrEmpty(targetDirectoryField));

        DiskModeOption diskModeOption = (DiskModeOption) diskModeComboBox.getSelectedItem();
        if (diskModeOption != null) {
            properties.setProperty(Keys.DISK_MODE, diskModeOption.getCliValue());
        }

        properties.setProperty(Keys.RESTRICT_PATH, textOrEmpty(restrictPathField));
        properties.setProperty(Keys.VERBOSITY, verbositySpinner.getValue().toString());

        properties.setProperty(Keys.FORCE, Boolean.toString(forceCheckbox.isSelected()));
        properties.setProperty(Keys.INCLUDE_CONFIGURATION_MAP, Boolean.toString(includeConfigurationMapCheckbox.isSelected()));
        properties.setProperty(Keys.SKIP_DISABLED, Boolean.toString(skipDisabledCheckbox.isSelected()));
        properties.setProperty(Keys.DEPLOY, Boolean.toString(deployCheckbox.isSelected()));
        properties.setProperty(Keys.DEPLOY_ALL, Boolean.toString(deployAllCheckbox.isSelected()));
        properties.setProperty(Keys.DEPLOY_CHANGED, Boolean.toString(deployChangedCheckbox.isSelected()));
        properties.setProperty(Keys.DEPLOY_NEW, Boolean.toString(deployNewCheckbox.isSelected()));
        properties.setProperty(Keys.INTERACTIVE, Boolean.toString(interactiveCheckbox.isSelected()));
        properties.setProperty(Keys.AUTO_COMMIT, Boolean.toString(autoCommitCheckbox.isSelected()));
        properties.setProperty(Keys.GIT_INIT, Boolean.toString(gitInitCheckbox.isSelected()));
        properties.setProperty(Keys.DELETE_ORPHANED, Boolean.toString(deleteOrphanedCheckbox.isSelected()));

        properties.setProperty(Keys.COMMIT_MESSAGE, textOrEmpty(commitMessageField));
        properties.setProperty(Keys.GIT_AUTHOR, textOrEmpty(gitAuthorField));
        properties.setProperty(Keys.GIT_EMAIL, textOrEmpty(gitEmailField));
        properties.setProperty(Keys.GIT_COMMAND, textOrEmpty(gitCommandField));

        storeUserPresets(properties);

        return properties;
    }

    private void executeSave(Properties properties) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                PlatformUI.MIRTH_FRAME.mirthClient.setPluginProperties(PluginConstants.PLUGIN_POINTNAME, properties, false);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    doRefresh();
                    appendOutput("Settings saved.");
                } catch (Exception ex) {
                    appendOutput("Failed to save settings: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                    setSaveEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private enum ExecutionMode {
        SERVER,
        CLIENT
    }

    private enum ActionOption {
        PUSH("Push", "push"),
        PULL("Pull", "pull"),
        GIT("Git", "git");

        private final String label;
        private final String cliValue;

        ActionOption(String label, String cliValue) {
            this.label = label;
            this.cliValue = cliValue;
        }

        public String getCliValue() {
            return cliValue;
        }

        public boolean requiresCredentials() {
            return this == PUSH || this == PULL;
        }

        public boolean isGit() {
            return this == GIT;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum DiskModeOption {
        CODE("Code (default)", "code"),
        ITEMS("Items", "items"),
        GROUPS("Groups", "groups"),
        BACKUP("Backup", "backup");

        private final String label;
        private final String cliValue;

        DiskModeOption(String label, String cliValue) {
            this.label = label;
            this.cliValue = cliValue;
        }

        public String getCliValue() {
            return cliValue;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
