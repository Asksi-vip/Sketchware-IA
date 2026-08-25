package mod.hey.studios.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.utility.Network;

public final class AppUpdateNotifier {

    private static final String PREFS = "app_update_notifier";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_LAST_NOTIFIED_TAG = "last_notified_tag";
    private static final String CHANNEL_ID = "app_updates";
    private static final int NOTIFICATION_ID = 7001;
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private AppUpdateNotifier() {
    }

    public static void checkForUpdates(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply();

        new Network().get(Helper.getResString(R.string.link_github_releases_url), response -> {
            ReleaseInfo latestRelease = parseLatestRelease(response);
            if (latestRelease == null || !isNewerVersion(latestRelease.tagName, BuildConfig.VERSION_NAME)) {
                return;
            }
            if (latestRelease.tagName.equals(prefs.getString(KEY_LAST_NOTIFIED_TAG, ""))) {
                return;
            }

            showUpdateNotification(appContext, latestRelease);
            prefs.edit().putString(KEY_LAST_NOTIFIED_TAG, latestRelease.tagName).apply();
        });
    }

    private static ReleaseInfo parseLatestRelease(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        try {
            JSONArray releases = new JSONArray(response);
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    continue;
                }

                String tagName = release.optString("tag_name", "").trim();
                if (tagName.isEmpty()) {
                    continue;
                }

                String title = release.optString("name", tagName).trim();
                String url = release.optString("html_url", Helper.getResString(R.string.link_github_release)).trim();
                return new ReleaseInfo(tagName, title.isEmpty() ? tagName : title, url);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isNewerVersion(String remoteVersion, String currentVersion) {
        int[] remote = parseVersion(remoteVersion);
        int[] current = parseVersion(currentVersion);
        int max = Math.max(remote.length, current.length);
        for (int i = 0; i < max; i++) {
            int remotePart = i < remote.length ? remote[i] : 0;
            int currentPart = i < current.length ? current[i] : 0;
            if (remotePart != currentPart) {
                return remotePart > currentPart;
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String normalized = version == null
                ? ""
                : version.toLowerCase(Locale.US).replaceFirst("^v", "");
        String[] parts = normalized.split("[^0-9]+");
        int[] values = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                values[count++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
            }
        }
        int[] compact = new int[count];
        System.arraycopy(values, 0, compact, 0, count);
        return compact;
    }

    private static void showUpdateNotification(Context context, ReleaseInfo release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.url));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String message = context.getString(R.string.update_notification_message, release.title);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sketchware_24)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private static final class ReleaseInfo {
        final String tagName;
        final String title;
        final String url;

        ReleaseInfo(String tagName, String title, String url) {
            this.tagName = tagName;
            this.title = title;
            this.url = url;
        }
    }
}
