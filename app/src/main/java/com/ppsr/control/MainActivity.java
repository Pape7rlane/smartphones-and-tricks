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
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private TextView serverState;
    private TextView startButton;
    private TextView lineupValue;
    private boolean receiverRegistered;

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            boolean running = intent.getBooleanExtra(PythonServerService.EXTRA_RUNNING, false);
            showState(running);
            String error = intent.getStringExtra(PythonServerService.EXTRA_ERROR);
            if (error != null) new AlertDialog.Builder(MainActivity.this)
                    .setTitle("启动失败").setMessage(error).setPositiveButton("关闭", null).show();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(16, 17, 20));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);
        bindViews();
        try { ServerFiles.install(this); refreshLineup(); }
        catch (Exception error) { showError(error); }
        requestNotificationPermission();
    }

    private void bindViews() {
        serverState = findViewById(R.id.serverState);
        startButton = findViewById(R.id.startButton);
        lineupValue = findViewById(R.id.lineupValue);
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
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(PythonServerService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(stateReceiver, filter);
        receiverRegistered = true;
        refreshLineup();
    }

    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(stateReceiver); receiverRegistered = false; }
        super.onStop();
    }

    private void startServer() {
        Intent intent = new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
        serverState.setText("服务器正在启动");
    }

    private void stopServer() {
        startService(new Intent(this, PythonServerService.class).setAction(PythonServerService.ACTION_STOP));
    }

    private void showState(boolean running) {
        serverState.setText(running ? "服务器运行中" : "服务器已停止");
        startButton.setText(running ? "服务器运行中 · 127.0.0.1:21000" : "启动服务器");
        startButton.setBackgroundResource(running
                ? R.drawable.bg_primary_button_running : R.drawable.bg_primary_button);
    }

    private void chooseTrailblazer() {
        chooseConfig("开拓者命途", new String[]{"毁灭", "存护", "同谐", "记忆", "欢愉"},
                new String[]{"Destruction", "Preservation", "Harmony", "Remembrance", "Elation"}, "tb_path");
    }

    private void chooseMarch() {
        chooseConfig("三月七命途", new String[]{"存护", "巡猎"},
                new String[]{"Preservation", "Hunt"}, "march_path");
    }

    private void chooseConfig(String title, String[] labels, String[] values, String key) {
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, which) -> {
            try {
                ConfigStore.setPath(this, key, values[which]);
                toast("已切换到：" + labels[which] + "，重启服务后生效");
            } catch (Exception error) { showError(error); }
        }).setNegativeButton("取消", null).show();
    }

    private void choosePath(boolean wholePath) {
        new AlertDialog.Builder(this).setTitle(wholePath ? "整个命途加入" : "选择命途")
                .setItems(CharacterData.PATH_LABELS, (dialog, which) -> {
                    String path = CharacterData.PATH_VALUES[which];
                    if (wholePath) confirmAddPath(CharacterData.PATH_LABELS[which], path);
                    else chooseCharacter(CharacterData.PATH_LABELS[which], path);
                }).setNegativeButton("取消", null).show();
    }

    private void chooseCharacter(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        String[] items = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) items[i] = String.valueOf(ids.get(i));
        new AlertDialog.Builder(this).setTitle(label).setItems(items, (dialog, which) -> {
            try { ConfigStore.addIds(this, Collections.singletonList(ids.get(which))); refreshLineup(); }
            catch (Exception error) { showError(error); }
        }).setNegativeButton("返回", null).show();
    }

    private void confirmAddPath(String label, String path) {
        List<Integer> ids = CharacterData.PATHS.get(path);
        new AlertDialog.Builder(this).setTitle("加入整个" + label + "命途？")
                .setMessage("将加入 " + ids.size() + " 个角色，已存在的角色会跳过。")
                .setPositiveButton("加入", (d, w) -> {
                    try { ConfigStore.addIds(this, ids); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton("取消", null).show();
    }

    private void inputCharacterIds() {
        EditText input = input("例如：1501 1502 1503", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle("添加角色 ID").setView(input)
                .setPositiveButton("添加", (d, w) -> {
                    try {
                        String value = input.getText().toString().trim();
                        if (!value.matches("[0-9 ]+")) throw new IllegalArgumentException("只能输入数字和空格");
                        List<Integer> ids = new ArrayList<>();
                        for (String token : value.split(" +")) ids.add(Integer.parseInt(token));
                        ConfigStore.addIds(this, ids); refreshLineup();
                    } catch (Exception error) { showError(error); }
                }).setNegativeButton("取消", null).show();
    }

    private void inputRemoveIndex() {
        EditText input = input("当前配队中的序号", InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this).setTitle("删除角色").setView(input)
                .setPositiveButton("删除", (d, w) -> {
                    try { ConfigStore.removeAt(this, Integer.parseInt(input.getText().toString())); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton("取消", null).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("清空整个配队？")
                .setPositiveButton("清空", (d, w) -> {
                    try { ConfigStore.clear(this); refreshLineup(); }
                    catch (Exception error) { showError(error); }
                }).setNegativeButton("取消", null).show();
    }

    private void refreshLineup() {
        if (lineupValue == null) return;
        try {
            List<Integer> ids = ConfigStore.lineup(this);
            if (ids.isEmpty()) { lineupValue.setText("当前配队：暂无角色"); return; }
            StringBuilder value = new StringBuilder("当前配队\n");
            for (int i = 0; i < ids.size(); i++) value.append(i + 1).append(". ").append(ids.get(i)).append('\n');
            lineupValue.setText(value.toString().trim());
        } catch (Exception error) { lineupValue.setText("配队读取失败：" + error.getMessage()); }
    }

    private void chooseIcon() {
        new AlertDialog.Builder(this).setTitle("APK 图标（res PNG）")
                .setItems(IconSwitcher.LABELS, (d, which) -> {
                    IconSwitcher.apply(this, which);
                    toast("PNG 图标已切换，桌面可能需要几秒刷新");
                }).setNegativeButton("取消", null).show();
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
        new AlertDialog.Builder(this).setTitle("操作失败")
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("关闭", null).show();
    }
}
