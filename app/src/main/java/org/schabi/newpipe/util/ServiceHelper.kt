/*
 * SPDX-FileCopyrightText: 2018-2026 NewPipe contributors <https://newpipe.net>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

object ServiceHelper {
    private val DEFAULT_FALLBACK_SERVICE: StreamingService = ServiceList.YouTube

    @JvmStatic
    @DrawableRes
    fun getIcon(serviceId: Int): Int {
        return when (serviceId) {
            ServiceList.YouTube.serviceId -> R.drawable.ic_smart_display
            else -> R.drawable.ic_circle
        }
    }

    @JvmStatic
    fun getTranslatedFilterString(filter: String, context: Context): String {
        return when (filter) {
            "all" -> context.getString(R.string.all)
            "videos", "sepia_videos", "music_videos" -> context.getString(R.string.videos_string)
            "channels" -> context.getString(R.string.channels)
            "playlists", "music_playlists" -> context.getString(R.string.playlists)
            else -> filter
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @param serviceId service to get the instructions for
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructions(serviceId: Int): Int {
        return when (serviceId) {
            ServiceList.YouTube.serviceId -> R.string.import_youtube_instructions
            else -> -1
        }
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @param serviceId service to get the hint for
     * @return the hint's string resource or -1 if the service don't support it
     */
    @JvmStatic
    @StringRes
    fun getImportInstructionsHint(serviceId: Int): Int = -1

    @JvmStatic
    fun getSelectedServiceId(context: Context): Int {
        val selectedServiceName = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(
                context.getString(R.string.current_service_key),
                DEFAULT_FALLBACK_SERVICE.serviceInfo.name
            )

        if (selectedServiceName != DEFAULT_FALLBACK_SERVICE.serviceInfo.name) {
            setSelectedServicePreferences(context, DEFAULT_FALLBACK_SERVICE.serviceInfo.name)
        }

        return DEFAULT_FALLBACK_SERVICE.serviceId
    }

    @JvmStatic
    fun getSelectedService(context: Context): StreamingService = DEFAULT_FALLBACK_SERVICE

    @JvmStatic
    fun getNameOfServiceById(serviceId: Int): String {
        return when (serviceId) {
            DEFAULT_FALLBACK_SERVICE.serviceId -> DEFAULT_FALLBACK_SERVICE.serviceInfo.name
            else -> "<unknown>"
        }
    }

    /**
     * @param serviceId the id of the service
     * @return the YouTube service, which is the only supported service in the app UI
     */
    @JvmStatic
    fun getServiceById(serviceId: Int): StreamingService = DEFAULT_FALLBACK_SERVICE

    @JvmStatic
    fun setSelectedServiceId(context: Context, serviceId: Int) {
        setSelectedServicePreferences(context, DEFAULT_FALLBACK_SERVICE.serviceInfo.name)
    }

    private fun setSelectedServicePreferences(context: Context, serviceName: String?) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit { putString(context.getString(R.string.current_service_key), serviceName) }
    }

    @JvmStatic
    fun getCacheExpirationMillis(serviceId: Int): Long {
        return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS)
    }

    fun initService(context: Context, serviceId: Int) = Unit

    @JvmStatic
    fun initServices(context: Context) = Unit
}
