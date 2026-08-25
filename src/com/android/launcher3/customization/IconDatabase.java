package com.android.launcher3.customization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.icons.AxIconsHelper;
import com.android.launcher3.util.ComponentKey;

public class IconDatabase {

    private static final String PREF_FILE_NAME = BuildConfig.APPLICATION_ID + ".ICON_DATABASE";
    public static final String KEY_ICON_PACK = "pref_icon_pack";
    public static final String VALUE_DEFAULT = "";

    public static String getGlobal(Context context) {
        SharedPreferences prefs = LauncherPrefs.getPrefs(context);
        // An explicit launcher choice — including Default, stored as "" — must win
        // over Theme Store. Only fall back when the user has never selected a pack.
        if (prefs.contains(KEY_ICON_PACK)) {
            String local = prefs.getString(KEY_ICON_PACK, VALUE_DEFAULT);
            return local != null ? local : VALUE_DEFAULT;
        }
        String themeEnginePack = AxIconsHelper.getActiveIconPackPackage(context);
        return themeEnginePack != null ? themeEnginePack : VALUE_DEFAULT;
    }

    public static String getGlobalLabel(Context context) {
        if (context == null) return "Default";
        final String defaultLabel = context.getString(R.string.icon_pack_default_label);
        final String pkgName = getGlobal(context);
        if (VALUE_DEFAULT.equals(pkgName)) {
            return defaultLabel;
        }

        final PackageManager pm = context.getPackageManager();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            return defaultLabel;
        }
    }

    public static void setGlobal(Context context, String value) {
        if (value == null) {
            value = VALUE_DEFAULT;
        }
        // Theme engine must be updated first: SharedPreferences listeners fire
        // synchronously from apply() on the UI thread, and both IDP and icon
        // loading still read theme_engine_data via getGlobal() / AxIconsHelper.
        AxIconsHelper.setActiveIconPackPackage(context, value);
        LauncherPrefs.getPrefs(context).edit().putString(KEY_ICON_PACK, value).apply();
    }

    public static void resetGlobal(Context context) {
        AxIconsHelper.setActiveIconPackPackage(context, "");
        LauncherPrefs.getPrefs(context).edit().remove(KEY_ICON_PACK).apply();
    }

    public static String getByComponent(Context context, ComponentKey key) {
        return getIconPackPrefs(context).getString(key.toString(), getGlobal(context));
    }

    public static void setForComponent(Context context, ComponentKey key, String value) {
        getIconPackPrefs(context).edit().putString(key.toString(), value).apply();
    }

    public static void resetForComponent(Context context, ComponentKey key) {
        getIconPackPrefs(context).edit().remove(key.toString()).apply();
    }

    private static SharedPreferences getIconPackPrefs(Context context) {
        return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }
}
