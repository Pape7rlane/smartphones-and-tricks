package com.ppsr.control;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

final class UiLanguage {
    private UiLanguage() {}

    static boolean isEnglish(Context context) {
        return !isSystemChinese(context);
    }

    static boolean isSystemChinese(Context context) {
        Locale systemLocale = context.getResources().getConfiguration().getLocales().get(0);
        return Locale.CHINESE.getLanguage().equals(systemLocale.getLanguage());
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
