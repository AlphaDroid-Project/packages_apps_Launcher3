/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/**
 * Helper that bridges AxThemePicker's icon pack selection (stored in
 * Settings.Secure "theme_engine_data") with Launcher3's icon loading.
 */
public class AxIconsHelper {

    private static final String TAG = "AxIconsHelper";
    private static final String SETTINGS_THEME_ENGINE_DATA = "theme_engine_data";
    private static final String CATEGORY_ICON_PACK = "icon_pack";
    private static final String THEMED_ICON_STYLE_SETTING = "themed_icon_style";
    private static final String STYLE_AOSP = "aosp";

    /**
     * Returns true if the "axion" themed icon style is active (neutral palette).
     * Returns false if "aosp" style is selected (accent palette).
     */
    public static boolean isAxIconsEnabled(Context context) {
        if (context == null) return true;
        String style = Settings.Secure.getString(
                context.getContentResolver(), THEMED_ICON_STYLE_SETTING);
        return !STYLE_AOSP.equals(style);
    }

    /**
     * Returns the currently active icon pack package from theme_engine_data,
     * or null if none is set.
     */
    public static String getActiveIconPackPackage(Context context) {
        try {
            String json = Settings.Secure.getString(
                    context.getContentResolver(), SETTINGS_THEME_ENGINE_DATA);
            if (TextUtils.isEmpty(json)) return null;

            JSONObject config = new JSONObject(json);
            JSONObject themes = config.optJSONObject("themes");
            if (themes == null) return null;

            JSONObject iconPack = themes.optJSONObject(CATEGORY_ICON_PACK);
            if (iconPack == null) return null;

            if (!iconPack.optBoolean("enabled", false)) return null;

            String pkg = iconPack.optString("packageName", "");
            return TextUtils.isEmpty(pkg) || "null".equals(pkg) ? null : pkg;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read theme_engine_data", e);
            return null;
        }
    }

    /**
     * Returns true if an icon pack is actively set via theme_engine_data.
     */
    public static boolean hasActiveIconPack(Context context) {
        return getActiveIconPackPackage(context) != null;
    }

    /**
     * Writes the icon pack selection to theme_engine_data so AxThemePicker stays in sync.
     */
    public static void setActiveIconPackPackage(Context context, String packageName) {
        try {
            String json = Settings.Secure.getString(
                    context.getContentResolver(), SETTINGS_THEME_ENGINE_DATA);
            JSONObject config = TextUtils.isEmpty(json) ? new JSONObject() : new JSONObject(json);

            JSONObject themes = config.optJSONObject("themes");
            if (themes == null) themes = new JSONObject();

            JSONObject iconPackConfig = new JSONObject();
            if (TextUtils.isEmpty(packageName)) {
                iconPackConfig.put("enabled", false);
                iconPackConfig.put("packageName", JSONObject.NULL);
            } else {
                iconPackConfig.put("enabled", true);
                iconPackConfig.put("packageName", packageName);
            }
            themes.put(CATEGORY_ICON_PACK, iconPackConfig);
            config.put("themes", themes);

            if (!config.has("version")) config.put("version", 1);

            Settings.Secure.putString(
                    context.getContentResolver(), SETTINGS_THEME_ENGINE_DATA, config.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write theme_engine_data", e);
        }
    }

    /**
     * Attempts to load an icon for the given component from the active icon pack.
     * Returns null if no icon pack is active or the pack doesn't have an icon for this component.
     */
    public static Drawable loadIconPackDrawable(Context context, ComponentName cn, int iconDpi) {
        String packPackage = getActiveIconPackPackage(context);
        if (packPackage == null) return null;

        try {
            PackageManager pm = context.getPackageManager();
            Resources packRes = pm.getResourcesForApplication(packPackage);

            int filterResId = packRes.getIdentifier("appfilter", "xml", packPackage);
            if (filterResId == 0) return null;

            String targetComponent = "ComponentInfo{" + cn.flattenToString() + "}";
            String drawableName = null;

            XmlResourceParser parser = packRes.getXml(filterResId);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) continue;
                if (!"item".equals(parser.getName())) continue;

                String component = parser.getAttributeValue(null, "component");
                if (targetComponent.equals(component)) {
                    drawableName = parser.getAttributeValue(null, "drawable");
                    break;
                }
            }
            parser.close();

            if (drawableName == null) return null;

            int drawableId = packRes.getIdentifier(drawableName, "drawable", packPackage);
            if (drawableId == 0) return null;

            return packRes.getDrawableForDensity(drawableId, iconDpi, null);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Icon pack not found: " + packPackage);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load icon from pack: " + packPackage, e);
        }
        return null;
    }

    /**
     * Returns true if the given drawable was loaded from an icon pack.
     */
    public static boolean isIconPackDrawable(Drawable drawable) {
        return drawable != null && drawable.getClass().getName().contains("ResourceDrawable");
    }
}
