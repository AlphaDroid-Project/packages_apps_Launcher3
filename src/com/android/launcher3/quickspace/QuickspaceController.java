/*
 * Copyright (C) 2021-2026 crDroid Android Project
 * Copyright (C) 2026 AlphaDroid
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
package com.android.launcher3.quickspace;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.media.MediaMetadata;
import android.text.TextUtils;
import android.util.Log;
import android.view.View.OnClickListener;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.MediaSessionManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controls the Quickspace (at-a-glance) area on the launcher home screen.
 *
 * Weather data comes exclusively from AxQuickLook via {@link LauncherQuickLookBridge}.
 * AxQuickLook internally resolves OmniJaws vs Google weather based on the system-wide
 * {@code lockscreen_weather_source} setting (configured in AlphaSettings).
 */
public class QuickspaceController implements MediaSessionManagerHelper.MediaMetadataListener {

    private static final String TAG = "Launcher3:QuickspaceController";

    private final List<OnDataListener> mListeners =
        Collections.synchronizedList(new ArrayList<>());
    private final Context mContext;
    private QuickEventsController mEventsController;
    private boolean mMediaRegistered = false;

    private static final long PSA_UPDATE_DELAY_MS = 3 * 60 * 1000;

    private final Handler mHandler = MAIN_EXECUTOR.getHandler();

    private final LauncherQuickLookBridge mQuickLookBridge;
    private boolean mQuickLookActive = false;

    private String mWeatherText;
    private Icon mWeatherIcon;
    private int mLastBmpHash;

    private final MediaSessionManagerHelper mMediaSessionHelper;

    private final LauncherQuickLookBridge.Listener mQuickLookListener =
            new LauncherQuickLookBridge.Listener() {
                @Override
                public void onQuickLookWeatherUpdated(String displayText, Bitmap icon) {
                    int hash = (icon == null) ? 0 : icon.getGenerationId();
                    if (TextUtils.equals(displayText, mWeatherText) && hash == mLastBmpHash) {
                        return;
                    }
                    mLastBmpHash = hash;
                    mWeatherText = displayText;
                    mWeatherIcon = icon == null ? null : Icon.createWithBitmap(icon);
                    notifyListeners();
                }

                @Override
                public void onQuickLookWeatherCleared() {
                    mWeatherText = null;
                    mWeatherIcon = null;
                    mLastBmpHash = 0;
                    notifyListeners();
                }
            };

    private final Runnable mOnDataUpdatedRunnable = () -> {
        for (OnDataListener list : new ArrayList<>(mListeners)) {
            list.onDataUpdated();
        }
    };

    private final Runnable mPsaRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.removeCallbacks(this);
            if (mEventsController == null) return;
            mEventsController.updatePsonality();
            mHandler.postDelayed(this, PSA_UPDATE_DELAY_MS);
            notifyListeners();
        }
    };

    public interface OnDataListener {
        void onDataUpdated();
    }

    public QuickspaceController(Context context) {
        mContext = context;
        mEventsController = new QuickEventsController(context);
        mMediaSessionHelper = MediaSessionManagerHelper.Companion.getInstance(context);
        mQuickLookBridge = new LauncherQuickLookBridge(context, mHandler);
    }

    private void startWeather() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) {
            stopWeather();
            return;
        }
        if (!mQuickLookActive && !mListeners.isEmpty()) {
            mQuickLookBridge.start(mQuickLookListener);
            mQuickLookActive = true;
            Log.i(TAG, "AxQuickLook weather bridge started");
        }
    }

    private void stopWeather() {
        if (mQuickLookActive) {
            mQuickLookBridge.stop();
            mQuickLookActive = false;
            mWeatherText = null;
            mWeatherIcon = null;
            mLastBmpHash = 0;
            Log.i(TAG, "AxQuickLook weather bridge stopped");
        }
    }

    public void addListener(OnDataListener listener) {
        if (listener == null) return;
        boolean wasEmpty = mListeners.isEmpty();
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
        if (wasEmpty) {
            startWeather();
            registerMediaController();
            mEventsController.initQuickEvents();
            updatePSAevent();
        }
        listener.onDataUpdated();
    }

    public void removeListener(OnDataListener listener) {
        if (listener == null) return;
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            stopWeather();
            unregisterMediaController();
            mHandler.removeCallbacks(mPsaRunnable);
            mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        }
    }

    public boolean isQuickEvent() {
        return mEventsController.isQuickEvent();
    }

    public QuickEventsController getEventController() {
        return mEventsController;
    }

    public boolean isWeatherAvailable() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_WEATHER.get(mContext)) return false;
        return !TextUtils.isEmpty(mWeatherText) || mWeatherIcon != null;
    }

    public Drawable getWeatherIcon() {
        return mWeatherIcon != null ? mWeatherIcon.loadDrawable(mContext) : null;
    }

    public String getWeatherTemp() {
        return mWeatherText;
    }

    public void onPause() {
        unregisterMediaController();
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        stopWeather();
    }

    public void onResume() {
        registerMediaController();
        updateMediaController();
        startWeather();
        updatePSAevent();
        notifyListeners();
    }

    public void onDestroy() {
        unregisterMediaController();
        stopWeather();
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        for (OnDataListener listener : new ArrayList<>(mListeners)) {
            removeListener(listener);
        }
    }

    private void updatePSAevent() {
        mHandler.removeCallbacks(mPsaRunnable);
        mHandler.post(mPsaRunnable);
    }

    public void notifyListeners() {
        mHandler.removeCallbacks(mOnDataUpdatedRunnable);
        mHandler.post(mOnDataUpdatedRunnable);
    }

    private void registerMediaController() {
        if (mMediaRegistered) return;
        mMediaSessionHelper.addMediaMetadataListener(this);
        mMediaRegistered = true;
    }

    private void unregisterMediaController() {
        if (!mMediaRegistered) return;
        mMediaSessionHelper.removeMediaMetadataListener(this);
        mMediaRegistered = false;
    }

    private boolean updateMediaController() {
        if (!LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(mContext)) {
            return false;
        }
        MediaMetadata mediaMetadata = mMediaSessionHelper.getCurrentMediaMetadata();
        boolean isPlaying = mMediaSessionHelper.isMediaPlaying();
        String trackArtist = isPlaying && mediaMetadata != null ?
                mediaMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST) : "";
        String trackTitle = isPlaying && mediaMetadata != null ?
                mediaMetadata.getString(MediaMetadata.METADATA_KEY_TITLE) : "";
        Drawable mediaIcon = mMediaSessionHelper.getMediaAppIcon();
        OnClickListener launchMediaApp =
                view -> mMediaSessionHelper.launchMediaApp();
        mEventsController.setMediaInfo(trackTitle, trackArtist, isPlaying,
                mediaIcon, launchMediaApp);
        mEventsController.updateQuickEvents();
        return true;
    }

    @Override
    public void onMediaMetadataChanged() {
        if (updateMediaController()) notifyListeners();
    }

    @Override
    public void onPlaybackStateChanged() {
        if (updateMediaController()) notifyListeners();
    }
}
