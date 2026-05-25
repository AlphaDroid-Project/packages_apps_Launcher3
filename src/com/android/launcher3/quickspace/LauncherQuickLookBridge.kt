/*
 * Copyright (C) 2026 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.quickspace

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.android.axion.quicklook.IAxQuickLookService
import com.android.axion.quicklook.IQuickLookCallback
import com.android.axion.quicklook.QuickLookTarget
import com.android.axion.quicklook.weatherData
import com.android.internal.util.alpha.OmniJawsClient

/**
 * Binds to [AxQuickLook] and forwards lockscreen-grade weather (OmniJaws vs Google as resolved by
 * AxQuickLook) into Launcher Quickspace.
 */
class LauncherQuickLookBridge(
    private val context: Context,
    private val mainHandler: Handler,
) {
    interface Listener {
        fun onQuickLookWeatherUpdated(displayText: String, icon: Bitmap?)

        fun onQuickLookWeatherCleared()
    }

    private var listener: Listener? = null
    private var service: IAxQuickLookService? = null
    private var bound = false

    private val callback =
        object : IQuickLookCallback.Stub() {
            override fun onTargetsUpdated(targets: MutableList<QuickLookTarget>) {
                mainHandler.post { processTargets(targets) }
            }
        }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IAxQuickLookService.Stub.asInterface(binder)
                try {
                    service?.registerCallback(callback)
                    service?.getCurrentTargets()?.let { processTargets(it) }
                } catch (e: RemoteException) {
                    Log.e(TAG, "registerCallback / getCurrentTargets", e)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
                listener?.onQuickLookWeatherCleared()
            }
        }

    fun start(l: Listener) {
        listener = l
        val intent =
            Intent(SERVICE_ACTION).apply {
                setPackage(SERVICE_PACKAGE)
            }
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.e(TAG, "bindService", e)
            bound = false
        }
        if (!bound) {
            listener?.onQuickLookWeatherCleared()
        }
    }

    fun stop() {
        listener = null
        try {
            service?.unregisterCallback(callback)
        } catch (_: RemoteException) {
        }
        service = null
        if (bound) {
            try {
                context.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
            bound = false
        }
    }

    private fun processTargets(targets: List<QuickLookTarget>) {
        val l = listener ?: return
        val weatherTarget =
            targets.firstOrNull { it.targetType == QuickLookTarget.TYPE_WEATHER } ?: run {
                l.onQuickLookWeatherCleared()
                return
            }
        val wd =
            weatherTarget.weatherData ?: run {
                l.onQuickLookWeatherCleared()
                return
            }
        val text = buildWeatherLabel(wd)
        val icon =
            wd.iconBytes?.let { bytes ->
                try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (_: Exception) {
                    null
                }
            }
                ?: wd.conditionCode.takeIf { it != 0 }?.let { code ->
                    drawableToBitmap(OmniJawsClient.get().getWeatherConditionImage(context, code))
                }
        l.onQuickLookWeatherUpdated(text, icon)
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable?): Bitmap? {
        if (drawable == null) return null
        return try {
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun buildWeatherLabel(wd: com.android.axion.quicklook.WeatherData): String {
        val sb = StringBuilder()
        wd.city?.takeIf { it.isNotBlank() }?.let { sb.append(it).append(' ') }
        sb.append(wd.temp).append(wd.tempUnit ?: "°")
        if (wd.condition.isNotBlank()) {
            sb.append(" • ").append(wd.condition)
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "LauncherQuickLookBridge"
        private const val SERVICE_ACTION = "com.android.axion.quicklook.SERVICE"
        private const val SERVICE_PACKAGE = "com.android.axion.quicklook"

        @JvmStatic
        fun isInstalled(context: Context): Boolean =
            try {
                context.packageManager.getPackageInfo(SERVICE_PACKAGE, 0)
                true
            } catch (_: Exception) {
                false
            }
    }
}
