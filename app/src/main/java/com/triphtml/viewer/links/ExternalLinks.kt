package com.triphtml.viewer.links

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.triphtml.viewer.domain.LinkPolicy
import java.net.URISyntaxException

/**
 * Hands a link the user tapped to another Android app. Google Maps links try the Maps app first and fall
 * back to whatever handles the URL. Returns false when nothing on the device can open it.
 */
class ExternalLinks(private val activity: Activity) {
    fun open(url: String): Boolean {
        var destination: String? = url
        var fallback: String? = null
        if (url.startsWith("intent:")) {
            try {
                val untrusted = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                // Only the target URL survives. Components, selectors, flags, packages and extras never pass.
                destination = untrusted.dataString
                fallback = untrusted.getStringExtra("browser_fallback_url")
            } catch (_: URISyntaxException) {
                return false
            }
        }
        if (destination != null && LinkPolicy.isSafeExternal(destination) && launch(destination)) return true
        val webFallback = fallback?.takeIf { (it.startsWith("https://") || it.startsWith("http://")) && LinkPolicy.isSafeExternal(it) }
        return webFallback != null && launch(webFallback)
    }

    private fun launch(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        if (LinkPolicy.isGoogleMaps(url)) {
            if (start(Intent(intent).setPackage(MAPS_PACKAGE))) return true
        }
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
