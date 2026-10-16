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
        input.setHint(ui(PasswordStore.hasSaved(this)
                ? R.string.password_saved_hint : R.string.password_hint));
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int horizontal = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(horizontal, 8, horizontal, 0);

        new AlertDialog.Builder(this)
                .setTitle(ui(R.string.password_title))
                .setMessage(ui(R.string.password_message))
                .setView(input)
                .setPositiveButton(ui(R.string.save_and_start), (dialog, which) -> {
                    String password = input.getText().toString().trim();
                    try {
                        if (!password.isEmpty()) PasswordStore.save(this, password);
                        else if (!PasswordStore.hasSaved(this)) {
                            Toast.makeText(this, ui(R.string.password_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        startServerWithSavedPassword();
                    } catch (Exception error) {
                        new AlertDialog.Builder(this).setTitle(ui(R.string.password_save_failed))
                                .setMessage(error.getMessage())
                                .setPositiveButton(ui(R.string.close), null).show();
                    }
                })
                .setNegativeButton(ui(R.string.cancel), null)
                .show();
    }

    private void startServerWithSavedPassword() {
        if (!ensureStorageAccess()) return;
        Intent intent = new Intent(this, PythonServerService.class)
                .setAction(PythonServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
        showValidatingState();
    }
}
