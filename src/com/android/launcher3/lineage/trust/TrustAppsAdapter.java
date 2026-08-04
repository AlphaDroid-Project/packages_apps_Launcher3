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

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.lineage.trust.db.TrustComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * List with optional sections: Private apps (locked and/or hidden), then Other apps.
 * Hidden apps stay under Private so they can be unhidden without leaving the UI.
 */
class TrustAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_APP = 1;

    private final List<TrustComponent> mComponents = new ArrayList<>();
    private final List<Row> mRows = new ArrayList<>();
    private final Listener mListener;
    private final boolean mHasSecureKeyguard;
    private final Context mContext;
    private final PackageManager mPackageManager;

    TrustAppsAdapter(Context context, Listener listener, boolean hasSecureKeyguard) {
        mContext = context;
        mPackageManager = context.getPackageManager();
        mListener = listener;
        mHasSecureKeyguard = hasSecureKeyguard;
    }

    public void update(List<TrustComponent> list) {
        mComponents.clear();
        if (list != null) {
            mComponents.addAll(list);
        }
        rebuildRows();
        notifyDataSetChanged();
    }

    private void rebuildRows() {
        List<TrustComponent> privateApps = new ArrayList<>();
        List<TrustComponent> otherApps = new ArrayList<>();
        for (TrustComponent c : mComponents) {
            if (c.isHidden() || c.isProtected()) {
                privateApps.add(c);
            } else {
                otherApps.add(c);
            }
        }
        Collections.sort(privateApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        Collections.sort(otherApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        mRows.clear();
        if (!privateApps.isEmpty()) {
            mRows.add(Row.header(R.string.trust_apps_section_private));
            for (TrustComponent c : privateApps) {
                mRows.add(Row.app(c));
            }
            if (!otherApps.isEmpty()) {
                mRows.add(Row.header(R.string.trust_apps_section_other));
            }
        }
        for (TrustComponent c : otherApps) {
            mRows.add(Row.app(c));
        }
    }

    @Override
    public int getItemViewType(int position) {
        return mRows.get(position).isHeader() ? TYPE_HEADER : TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(
                    R.layout.item_trust_section_header, parent, false));
        }
        return new AppViewHolder(inflater.inflate(R.layout.item_hidden_app, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = mRows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(row.headerRes);
        } else if (holder instanceof AppViewHolder && row.component != null) {
            ((AppViewHolder) holder).bind(row.component, mHasSecureKeyguard);
        }
    }

    @Override
    public int getItemCount() {
        return mRows.size();
    }

    public interface Listener {
        void onHiddenItemChanged(@NonNull TrustComponent component);

        void onProtectedItemChanged(@NonNull TrustComponent component);
    }

    private static final class Row {
        @StringRes
        final int headerRes;
        @Nullable
        final TrustComponent component;

        private Row(int headerRes, @Nullable TrustComponent component) {
            this.headerRes = headerRes;
            this.component = component;
        }

        static Row header(@StringRes int res) {
            return new Row(res, null);
        }

        static Row app(@NonNull TrustComponent c) {
            return new Row(0, c);
        }

        boolean isHeader() {
            return component == null;
        }
    }

    private static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.trust_section_title);
        }

        void bind(@StringRes int titleRes) {
            mTitle.setText(titleRes);
        }
    }

    class AppViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIconView;
        private final TextView mLabelView;
        private final ImageView mHiddenView;
        private final ImageView mProtectedView;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            mIconView = itemView.findViewById(R.id.item_hidden_app_icon);
            mLabelView = itemView.findViewById(R.id.item_hidden_app_title);
            mHiddenView = itemView.findViewById(R.id.item_hidden_app_switch);
            mProtectedView = itemView.findViewById(R.id.item_protected_app_switch);
        }

        void bind(TrustComponent component, boolean hasSecureKeyguard) {
            mIconView.setImageDrawable(component.getIcon());
            mLabelView.setText(component.getLabel());

            mHiddenView.setImageResource(component.isHidden()
                    ? R.drawable.ic_hidden_locked : R.drawable.ic_hidden_unlocked);
            mProtectedView.setImageResource(component.isProtected()
                    ? R.drawable.ic_protected_locked : R.drawable.ic_protected_unlocked);

            mProtectedView.setVisibility(hasSecureKeyguard ? View.VISIBLE : View.GONE);

            // Always allow hide toggle for private section apps (they may lack a visible launcher
            // intent after being hidden from package queries).
            boolean canHide = component.isHidden()
                    || mPackageManager.getLaunchIntentForPackage(component.getPackageName()) != null;
            mHiddenView.setVisibility(canHide ? View.VISIBLE : View.GONE);

            mHiddenView.setOnClickListener(v -> {
                component.invertVisibility();
                mHiddenView.setImageResource(component.isHidden()
                        ? R.drawable.avd_hidden_lock : R.drawable.avd_hidden_unlock);
                AnimatedVectorDrawable avd = (AnimatedVectorDrawable) mHiddenView.getDrawable();
                runToggleAnimation(avd, () -> {
                    mListener.onHiddenItemChanged(component);
                    onComponentToggled(component);
                });
            });

            mProtectedView.setOnClickListener(v -> {
                component.invertProtection();
                mProtectedView.setImageResource(component.isProtected()
                        ? R.drawable.avd_protected_lock : R.drawable.avd_protected_unlock);
                AnimatedVectorDrawable avd = (AnimatedVectorDrawable) mProtectedView.getDrawable();
                runToggleAnimation(avd, () -> {
                    mListener.onProtectedItemChanged(component);
                    onComponentToggled(component);
                });
            });
        }

        private void runToggleAnimation(@Nullable AnimatedVectorDrawable avd, Runnable after) {
            if (avd != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                avd.registerAnimationCallback(new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        after.run();
                    }
                });
                avd.start();
            } else {
                if (avd != null) avd.start();
                after.run();
            }
        }

        private void onComponentToggled(@NonNull TrustComponent component) {
            // Keep mComponents in sync; re-section so private/other headers stay correct.
            for (int i = 0; i < mComponents.size(); i++) {
                if (mComponents.get(i).getPackageName().equals(component.getPackageName())) {
                    mComponents.set(i, component);
                    break;
                }
            }
            rebuildRows();
            notifyDataSetChanged();
        }
    }
}
