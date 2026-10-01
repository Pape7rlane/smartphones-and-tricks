package com.ppsr.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONTokener;

public class MainActivity extends Activity {
    private static final long LARGE_JSON_SIZE = 512 * 1024;
    private static final String SRTOOLS_URL = "https://srtools.neonteam.dev/1001/detail";
    private TextView serverState;
    private TextView startButton;
    private TextView lineupValue;
    private TextView languageButton;
    private ServerUiState serverUiState = ServerUiState.STOPPED;
    private boolean receiverRegistered;
    private boolean storageInitialized;
    private boolean notificationPermissionRequested;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean running = intent.getBooleanExtra(PythonServerService.EXTRA_RUNNING, false);
            showState(running);
            String error = intent.getStringExtra(PythonServerService.EXTRA_ERROR);
            if (error != null) new AlertDialog.Builder(MainActivity.this)
                    .setTitle(ui(R.string.start_failed)).setMessage(error)
                    .setPositiveButton(ui(R.string.close), null).show();
        }
    };

    private enum ServerUiState { STOPPED, STARTING, VALIDATING, RUNNING }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(getColor(R.color.background));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);
        bindViews();
        applyLanguage();
        ensureStorageAccess();
    }

    private void bindViews() {
        serverState = findViewById(R.id.serverState);
        startButton = findViewById(R.id.startButton);
        lineupValue = findViewById(R.id.lineupValue);
        languageButton = findViewById(R.id.languageButton);
        languageButton.setOnClickListener(v -> {
            UiLanguage.toggle(this);
            applyLanguage();
        });
        startButton.setOnClickListener(v -> startServer());
        findViewById(R.id.stopButton).setOnClickListener(v -> stopServer());
        findViewById(R.id.tbButton).setOnClickListener(v -> chooseTrailblazer());
        findViewById(R.id.marchButton).setOnClickListener(v -> chooseMarch());
        findViewById(R.id.selectButton).setOnClickListener(v -> choosePath(false));
        findViewById(R.id.addPathButton).setOnClickListener(v -> choosePath(true));
        findViewById(R.id.manualButton).setOnClickListener(v -> inputCharacterIds());
        findViewById(R.id.removeButton).setOnClickListener(v -> inputRemoveIndex());
        findViewById(R.id.clearButton).setOnClickListener(v -> confirmClear());
        findViewById(R.id.iconButton).setOnClickListener(v -> chooseIcon());
        findViewById(R.id.miscButton).setOnClickListener(v -> openMisc());
        findViewById(R.id.srtoolsButton).setOnClickListener(v -> openSrTools());
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(PythonServerService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        receiverRegistered = true;
        refreshLineup();
    }

    @Override protected void onResume() {
        super.onResume();
        if (StorageAccess.isGranted(this)) {
            if (!storageInitialized) initializeStorage();
            if (!notificationPermissionRequested) {
                notificationPermissionRequested = true;
                requestNotificationPermission();
            }
        }
    }

    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(stateReceiver); receiverRegistered = false; }
        super.onStop();
    }

    private void startServer() {
        if (!ensureStorageAccess()) return;
        Intent intent = new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        setServerUiState(ServerUiState.STARTING);
    }

    protected boolean ensureStorageAccess() {
        if (StorageAccess.isGranted(this)) {
            if (!storageInitialized) initializeStorage();
            return storageInitialized;
        }
        StorageAccess.request(this);
        toast(ui(R.string.storage_permission));
        return false;
    }

    private void initializeStorage() {
        try {
            ServerFiles.install(this);
            storageInitialized = true;
            refreshLineup();
        } catch (Exception error) {
            storageInitialized = false;
            showError(error);
        }
    }

    private void openSrTools() {
        if (!ensureStorageAccess()) return;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SRTOOLS_URL)));
    }

    private void stopServer() {
        startService(new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_STOP));
    }

    private void showState(boolean running) {
        setServerUiState(running ? ServerUiState.RUNNING : ServerUiState.STOPPED);
    }

    protected void showValidatingState() { setServerUiState(ServerUiState.VALIDATING); }

    private void setServerUiState(ServerUiState state) {
        serverUiState = state;
        renderServerState();
    }

    private void renderServerState() {
        int stateText;
        if (serverUiState == ServerUiState.RUNNING) stateText = R.string.server_running;
        else if (serverUiState == ServerUiState.STARTING) stateText = R.string.server_starting;
        else if (serverUiState == ServerUiState.VALIDATING) stateText = R.string.server_validating;
        else stateText = R.string.server_stopped;
        serverState.setText(ui(stateText));
        startButton.setText(ui(serverUiState == ServerUiState.RUNNING
                ? R.string.running_at : R.string.start_server));
        startButton.setBackgroundResource(serverUiState == ServerUiState.RUNNING
                ? R.drawable.bg_primary_button_running : R.drawable.bg_primary_button);
    }

    private void applyLanguage() {
        languageButton.setText(UiLanguage.isEnglish(this) ? "EN" : "CN");
        setText(R.id.serverSectionTitle, R.string.section_server);
        setText(R.id.quickSectionTitle, R.string.section_quick_actions);
        setText(R.id.lineupSectionTitle, R.string.section_lineup);
        setText(R.id.toolsSectionTitle, R.string.section_tools);
        setText(R.id.stopButton, R.string.stop_server);
        setText(R.id.tbButton, R.string.change_trailblazer_path);
        setText(R.id.marchButton, R.string.change_march_path);
        setText(R.id.selectButton, R.string.select_character_by_path);
        setText(R.id.addPathButton, R.string.add_entire_path);
        setText(R.id.manualButton, R.string.enter_character_ids);
        setText(R.id.removeButton, R.string.remove_character);
        setText(R.id.clearButton, R.string.clear_lineup);
        setText(R.id.iconButton, R.string.change_apk_icon);
        setText(R.id.miscButton, R.string.open_misc_files);
        setText(R.id.srtoolsButton, R.string.open_srtools);
        renderServerState();
        refreshLineup();
    }

    private void setText(int viewId, int textId) {
        ((TextView) findViewById(viewId)).setText(ui(textId));
    }

    protected String ui(int resource, Object... arguments) {
        return UiLanguage.text(this, resource, arguments);
    }

    private void chooseTrailblazer() {
        chooseConfig(ui(R.string.trailblazer_path), UiLanguage.array(this, R.array.trailblazer_path_labels),
                new String[]{"Destruction", "Preservation", "Harmony", "Remembrance", "Elation"}, "tb_path");
    }

    private void chooseMarch() {
        chooseConfig(ui(R.string.march_path), UiLanguage.array(this, R.array.march_path_labels),
                new String[]{"Preservation", "Hunt"}, "march_path");
    }

    private void chooseConfig(String title, String[] labels, String[] values, String key) {
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, which) -> {
            try {
                ConfigStore.setPath(this, key, values[which]);
                toast(ui(R.string.switched_path, labels[which]));
            } catch (Exception error) { showError(error); }
        }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void choosePath(boolean wholePath) {
        String[] pathLabels = UiLanguage.array(this, R.array.path_labels);
        new AlertDialog.Builder(this).setTitle(ui(wholePath
                        ? R.string.add_entire_path : R.string.select_path))
                .setItems(pathLabels, (dialog, which) -> {
                    String path = CharacterData.PATH_VALUES[which];
                    if (wholePath) confirmAddPath(pathLabels[which], path);
                    else chooseCharacter(pathLabels[which], path);
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void chooseCharacter(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        String[] items = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) items[i] = String.valueOf(ids.get(i));
        new AlertDialog.Builder(this).setTitle(label).setItems(items, (dialog, which) -> {
            try { ConfigStore.addIds(this, Collections.singletonList(ids.get(which))); refreshLineup(); }
            catch (Exception error) { showError(error); }
        }).setNegativeButton(ui(R.string.back), null).show();
    }

    private void confirmAddPath(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        new AlertDialog.Builder(this).setTitle(ui(R.string.add_whole_path_title, label))
                .setMessage(ui(R.string.add_whole_path_message, ids.size()))
                .setPositiveButton(ui(R.string.add), (d, w) -> {
                    try { ConfigStore.addIds(this, ids); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void inputCharacterIds() {
        EditText input = input(ui(R.string.character_ids_hint), InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle(ui(R.string.add_character_ids)).setView(input)
                .setPositiveButton(ui(R.string.add), (d, w) -> {
                    try {
                        String value = input.getText().toString().trim();
                        if (!value.matches("[0-9 ]+")) {
                            throw new IllegalArgumentException(ui(R.string.invalid_ids));
                        }
                        List<Integer> ids = new ArrayList<>();
                        for (String token : value.split(" +")) ids.add(Integer.parseInt(token));
                        ConfigStore.addIds(this, ids); refreshLineup();
                    } catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void inputRemoveIndex() {
        EditText input = input(ui(R.string.remove_index_hint), InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle(ui(R.string.remove_character_title)).setView(input)
                .setPositiveButton(ui(R.string.remove), (d, w) -> {
                    try { ConfigStore.removeAt(this, Integer.parseInt(input.getText().toString())); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle(ui(R.string.clear_lineup_title))
                .setPositiveButton(ui(R.string.clear), (d, w) -> {
                    try { ConfigStore.clear(this); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void refreshLineup() {
        if (lineupValue == null) return;
        try {
            List<Integer> ids = ConfigStore.lineup(this);
            if (ids.isEmpty()) { lineupValue.setText(ui(R.string.lineup_empty)); return; }
            StringBuilder value = new StringBuilder(ui(R.string.lineup_heading)).append('\n');
            boolean english = UiLanguage.isEnglish(this);
            for (int i = 0; i < ids.size(); i++) { int id = ids.get(i); String name = CharacterData.nameOf(id, english); value.append(i + 1).append(". ").append(id); if (name != null) value.append(" - ").append(name); value.append('\n'); }
            lineupValue.setText(value.toString().trim());
        } catch (Exception error) {
            lineupValue.setText(ui(R.string.lineup_read_failed, error.getMessage()));
        }
    }

    private void chooseIcon() {
        new AlertDialog.Builder(this).setTitle(ui(R.string.icon_dialog_title))
                .setItems(UiLanguage.array(this, R.array.icon_labels), (d, which) -> {
                    IconSwitcher.apply(this, which);
                    toast(ui(R.string.icon_changed));
                }).setNegativeButton(ui(R.string.cancel), null).show();
    }

    private void openMisc() {
        try {
            File misc = new File(ServerFiles.install(this), "misc");
            List<File> files = new ArrayList<>();
            collectJsonFiles(misc, files);
            Collections.sort(files, (left, right) ->
                    left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath()));
            if (files.isEmpty()) {
                toast(ui(R.string.no_json_files));
                return;
            }
            String[] labels = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                labels[i] = misc.toPath().relativize(files.get(i).toPath()).toString()
                        .replace(File.separatorChar, '/');
            }
            new AlertDialog.Builder(this).setTitle(ui(R.string.misc_files))
                    .setItems(labels, (dialog, which) -> openJsonFile(labels[which], files.get(which)))
                    .setNegativeButton(ui(R.string.close), null).show();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void collectJsonFiles(File directory, List<File> result) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectJsonFiles(child, result);
            else if (child.getName().toLowerCase().endsWith(".json")) result.add(child);
        }
    }

    private void openJsonFile(String relativePath, File file) {
        if (file.length() < LARGE_JSON_SIZE) {
            editMiscFile(relativePath, file);
            return;
        }
        String size = String.format(java.util.Locale.ROOT, "%.1f MB",
                file.length() / (1024.0 * 1024.0));
        new AlertDialog.Builder(this)
                .setTitle(ui(R.string.large_file_title, relativePath))
                .setMessage(ui(R.string.large_file_message, size))
                .setPositiveButton(ui(R.string.choose_external_app),
                        (dialog, which) -> openWithExternalApp(file))
                .setNegativeButton(ui(R.string.edit_in_app),
                        (dialog, which) -> editMiscFile(relativePath, file))
                .setNeutralButton(ui(R.string.cancel), null)
                .show();
    }

    private void openWithExternalApp(File file) {
        try {
            Uri uri = MiscFileProvider.uriForFile(this, file);
            Intent edit = new Intent(Intent.ACTION_EDIT)
                    .setDataAndType(uri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            edit.setClipData(android.content.ClipData.newRawUri(file.getName(), uri));
            startActivity(Intent.createChooser(edit, ui(R.string.choose_editor)));
        } catch (Exception error) {
            showError(error);
        }
    }

    private void editMiscFile(String relativePath, File file) {
        try {
            EditText editor = new EditText(this);
            editor.setText(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            editor.setTextSize(13);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setGravity(Gravity.TOP | Gravity.START);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setHorizontallyScrolling(true);
            editor.setMinLines(16);
            editor.setMaxLines(24);
            editor.setPadding(32, 20, 32, 20);

            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(relativePath)
                    .setView(editor)
                    .setPositiveButton(ui(R.string.save), null)
                    .setNegativeButton(ui(R.string.cancel), null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        try {
                            String value = editor.getText().toString();
                            new JSONTokener(value).nextValue();
                            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
                            refreshLineup();
                            toast(ui(R.string.saved_restart));
                            dialog.dismiss();
                        } catch (Exception error) {
                            editor.setError(ui(R.string.json_error, error.getMessage()));
                        }
                    }));
            dialog.show();
        } catch (Exception error) {
            showError(error);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private EditText input(String hint, int type) {
        EditText input = new EditText(this); input.setHint(hint); input.setInputType(type);
        input.setPadding(48, 8, 48, 0); return input;
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private void showError(Throwable error) {
        new AlertDialog.Builder(this).setTitle(ui(R.string.operation_failed))
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton(ui(R.string.close), null).show();
    }
}
