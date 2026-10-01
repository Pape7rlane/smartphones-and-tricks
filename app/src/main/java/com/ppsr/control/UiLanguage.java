package com.ppsr.control;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

final class UiLanguage {
    private static final String PREFERENCES = "ui_preferences";
    private static final String ENGLISH = "english";

    private UiLanguage() {}

    static boolean isEnglish(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getBoolean(ENGLISH, false);
    }

    static void toggle(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(ENGLISH, !isEnglish(context)).apply();
    }

    static String text(Context context, int resource, Object... arguments) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(isEnglish(context) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE);
        Context localized = context.createConfigurationContext(configuration);
        return localized.getString(resource, arguments);
    }

    static String[] array(Context context, int resource) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(isEnglish(context) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE);
        return context.createConfigurationContext(configuration).getResources().getStringArray(resource);
    }
}
