/*
 * Copyright (C) 2019 The LineageOS Project
 * Copyright (C) 2023 AlphaDroid
 * Copyright (C) 2023-2026 crDroid Android Project
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

import android.app.AxSandboxManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Thin client for the platform {@link AxSandboxManager} API — same role as the old
 * {@code AppLockManager} bridge for Trust UI and drawer/recents observe paths.
 *
 * <p>Lock/hide writes need {@code MANAGE_APP_LOCK}; reads use USE or ungated
 * per-package checks. Framework clears binder identity when persisting
 * {@code sandbox_config}.
 */
public class AppLockHelper {

    private static final String TAG = "AppLockHelper";

    @Nullable
    private final AxSandboxManager mSandboxManager;

    @Nullable
    private static AppLockHelper sSingleton;

    private AppLockHelper(@NonNull Context context) {
        mSandboxManager = context.getSystemService(AxSandboxManager.class);
        if (mSandboxManager == null) {
            Log.w(TAG, "AxSandboxManager unavailable");
        }
    }

    public static synchronized AppLockHelper getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            sSingleton = new AppLockHelper(context.getApplicationContext());
        }
        return sSingleton;
    }

    /** Hide from drawer — {@link AxSandboxManager#setPackageHidden}. */
    public void setShouldHideApp(@NonNull String packageName, boolean hide) {
        if (mSandboxManager == null) return;
        try {
            mSandboxManager.setPackageHidden(packageName, hide);
        } catch (RuntimeException e) {
            Log.w(TAG, "setShouldHideApp failed for " + packageName, e);
        }
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        if (mSandboxManager == null) return false;
        try {
            return mSandboxManager.isPackageHidden(packageName);
        } catch (RuntimeException e) {
            Log.w(TAG, "isPackageHidden failed for " + packageName, e);
            return false;
        }
    }

    /** App lock list — {@link AxSandboxManager#addLockedApp} / {@link #removeLockedApp}. */
    public void setShouldProtectApp(@NonNull String packageName, boolean protect) {
        if (mSandboxManager == null) return;
        try {
            if (protect) {
                mSandboxManager.addLockedApp(packageName);
            } else {
                mSandboxManager.removeLockedApp(packageName);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "setShouldProtectApp failed for " + packageName, e);
        }
    }

    public boolean isPackageProtected(@NonNull String packageName) {
        if (mSandboxManager == null) return false;
        try {
            return mSandboxManager.getAppLockState(packageName).hasAppLock();
        } catch (RuntimeException e) {
            Log.w(TAG, "isPackageProtected failed for " + packageName, e);
            return false;
        }
    }

    /** USE-level count for prediction padding (not the MANAGE name list). */
    public int getHiddenPackagesCount() {
        if (mSandboxManager == null) return 0;
        try {
            return mSandboxManager.getHiddenPackagesCount();
        } catch (RuntimeException e) {
            Log.w(TAG, "getHiddenPackagesCount failed", e);
            return 0;
        }
    }

    /** MANAGE — package names on the lock list. */
    @NonNull
    public List<String> getLockedPackages() {
        if (mSandboxManager == null) return Collections.emptyList();
        try {
            List<String> list = mSandboxManager.getLockedPackages();
            return list != null ? list : Collections.emptyList();
        } catch (RuntimeException e) {
            Log.w(TAG, "getLockedPackages failed", e);
            return Collections.emptyList();
        }
    }

    /** MANAGE — package names on the hidden list. */
    @NonNull
    public List<String> getHiddenPackages() {
        if (mSandboxManager == null) return Collections.emptyList();
        try {
            List<String> list = mSandboxManager.getHiddenPackages();
            return list != null ? list : Collections.emptyList();
        } catch (RuntimeException e) {
            Log.w(TAG, "getHiddenPackages failed", e);
            return Collections.emptyList();
        }
    }
}
