package com.ppsr.control;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class PasswordMainActivity extends MainActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView start = findViewById(R.id.startButton);
        start.setOnClickListener(v -> {
            if (ensureStorageAccess()) showPasswordDialog();
        });
    }

    private void showPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint(PasswordStore.hasSaved(this) ? "留空使用已保存密码" : "请输入服务器密码");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int horizontal = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(horizontal, 8, horizontal, 0);

        new AlertDialog.Builder(this)
                .setTitle("服务器密码")
                .setMessage("密码由 ppSR 服主提供；密码更新后在这里输入新的密码。")
                .setView(input)
                .setPositiveButton("保存并启动", (dialog, which) -> {
                    String password = input.getText().toString().trim();
                    try {
                        if (!password.isEmpty()) PasswordStore.save(this, password);
                        else if (!PasswordStore.hasSaved(this)) {
                            Toast.makeText(this, "请输入服务器密码", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startServerWithSavedPassword();
                    } catch (Exception error) {
                        new AlertDialog.Builder(this).setTitle("密码保存失败")
                                .setMessage(error.getMessage()).setPositiveButton("关闭", null).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startServerWithSavedPassword() {
        if (!ensureStorageAccess()) return;
        Intent intent = new Intent(this, PythonServerService.class)
                .setAction(PythonServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        TextView state = findViewById(R.id.serverState);
        state.setText("正在校验密码并启动");
    }
}
