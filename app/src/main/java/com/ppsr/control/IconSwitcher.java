package com.ppsr.control;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class IconSwitcher {
    static final String[] LABELS = {"默认 PNG", "圆形 PNG", "备用 PNG"};
    private static final String[] ALIASES = {"IconDefault", "IconLight", "IconRed"};
    private IconSwitcher() {}

    static void apply(Context context, int selected) {
        PackageManager pm = context.getPackageManager();
        for (int i = 0; i < ALIASES.length; i++) {
            ComponentName component = new ComponentName(context.getPackageName(),
                    context.getPackageName() + "." + ALIASES[i]);
            int state = i == selected ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
        }
    }
}
