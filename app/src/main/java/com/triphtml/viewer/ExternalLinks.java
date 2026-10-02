package com.triphtml.viewer;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import java.net.URISyntaxException;

final class ExternalLinks {
    private final Activity activity;
    ExternalLinks(Activity activity) { this.activity = activity; }

    boolean open(String url) {
        String destination = url;
        String fallback = null;
        if (url.startsWith("intent:")) {
            try {
                Intent untrusted = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                destination = untrusted.getDataString();
                fallback = untrusted.getStringExtra("browser_fallback_url");
                // Never forward supplied components, selectors, flags, packages or arbitrary extras.
            } catch (URISyntaxException error) { return false; }
        }
        if (LinkPolicy.isSafeExternal(destination) && launch(destination)) return true;
        if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))
                && LinkPolicy.isSafeExternal(fallback)) return launch(fallback);
        return false;
    }

    private boolean launch(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        if (LinkPolicy.isGoogleMaps(url)) {
            intent.setPackage("com.google.android.apps.maps");
            if (tryStart(intent)) return true;
            intent.setPackage(null);
        }
        return tryStart(intent);
    }

    private boolean tryStart(Intent intent) {
        try { activity.startActivity(intent); return true; }
        catch (ActivityNotFoundException | SecurityException error) { return false; }
    }
}
