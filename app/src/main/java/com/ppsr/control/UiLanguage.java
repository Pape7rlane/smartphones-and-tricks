package com.ppsr.control;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

final class UiLanguage {
    private UiLanguage() {}

    // Chinese-only build: the interface, map names and character names are
    // always Simplified Chinese, regardless of the system language. There is
    // no English toggle.
    static boolean isEnglish(Context context) {
        return false;
    }

    static String text(Context context, int resource, Object... arguments) {
        return localized(context).getString(resource, arguments);
    }

    static String[] array(Context context, int resource) {
        return localized(context).getResources().getStringArray(resource);
    }

    private static Context localized(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.SIMPLIFIED_CHINESE);
        return context.createConfigurationContext(configuration);
    }
}
