/*
 * Copyright (C) 2019-2024 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.lineage.trust;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import com.android.launcher3.lineage.trust.db.TrustComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads all trust-relevant apps. Private apps (locked and/or hidden) always appear
 * even when package visibility would hide them from normal launch-intent queries.
 */
public class LoadTrustComponentsTask extends AsyncTask<Void, Integer, List<TrustComponent>> {
    @NonNull
    private AppLockHelper mAppLockHelper;

    @NonNull
    private PackageManager mPackageManager;

    @NonNull
    private Callback mCallback;

    @NonNull
    private Context mContext;

    LoadTrustComponentsTask(@NonNull AppLockHelper appLockHelper,
            @NonNull PackageManager packageManager,
            @NonNull Callback callback,
            @NonNull Context context) {
        mAppLockHelper = appLockHelper;
        mPackageManager = packageManager;
        mCallback = callback;
        mContext = context;
    }

    @Override
    protected List<TrustComponent> doInBackground(Void... voids) {
        // MANAGE name lists — used to discover private packages that may be
        // missing from launch-intent queries after hide filtering.
        Set<String> privatePkgs = new HashSet<>();
        privatePkgs.addAll(mAppLockHelper.getLockedPackages());
        privatePkgs.addAll(mAppLockHelper.getHiddenPackages());

        Map<String, TrustComponent> byPackage = new HashMap<>();

        List<PackageInfo> apps = mPackageManager.getInstalledPackages(
                PackageInfoFlags.of(PackageManager.MATCH_ALL));

        int numPackages = apps.size();
        for (int i = 0; i < numPackages; i++) {
            PackageInfo app = apps.get(i);
            try {
                String pkgName = app.packageName;
                boolean isPrivate = privatePkgs.contains(pkgName);
                // Always include private packages; others only if launchable.
                if (!isPrivate && mPackageManager.getLaunchIntentForPackage(pkgName) == null) {
                    publishProgress(Math.round(i * 100f / numPackages));
                    continue;
                }
                if (app.applicationInfo == null) {
                    publishProgress(Math.round(i * 100f / numPackages));
                    continue;
                }
                byPackage.put(pkgName, buildComponent(pkgName, app.applicationInfo));
            } catch (Exception ignored) {
            }
            publishProgress(Math.round(i * 100f / numPackages));
        }

        // Private packages may be filtered out of normal queries — force-add them.
        for (String pkgName : privatePkgs) {
            if (byPackage.containsKey(pkgName)) continue;
            try {
                ApplicationInfo ai = mPackageManager.getApplicationInfo(pkgName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_ALL));
                byPackage.put(pkgName, buildComponent(pkgName, ai));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        List<TrustComponent> list = new ArrayList<>(byPackage.values());
        Collections.sort(list, (a, b) -> {
            boolean aPriv = a.isHidden() || a.isProtected();
            boolean bPriv = b.isHidden() || b.isProtected();
            if (aPriv != bPriv) {
                return aPriv ? -1 : 1;
            }
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
        return list;
    }

    @NonNull
    private TrustComponent buildComponent(@NonNull String pkgName, @NonNull ApplicationInfo ai) {
        String label = mPackageManager.getApplicationLabel(ai).toString();
        Drawable icon = ai.loadIcon(mPackageManager);
        // Live per-package reads for display state (authoritative for flags).
        boolean isHidden = mAppLockHelper.isPackageHidden(pkgName);
        boolean isProtected = mAppLockHelper.isPackageProtected(pkgName);
        return new TrustComponent(pkgName, icon, label, isHidden, isProtected);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (values.length > 0) {
            mCallback.onLoadListProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(List<TrustComponent> trustComponents) {
        mCallback.onLoadCompleted(trustComponents);
    }

    interface Callback {
        void onLoadListProgress(int progress);
        void onLoadCompleted(List<TrustComponent> result);
    }
}
