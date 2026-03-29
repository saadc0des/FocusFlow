package com.focusflow.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Calendar;
import java.util.List;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String PKG_YOUTUBE   = "com.google.android.youtube";
    private static final String PKG_INSTAGRAM = "com.instagram.android";
    private static final long   BACK_COOLDOWN = 2500;

    private long lastBackTime = 0;
    private Handler handler;
    private Runnable pendingCheck;

    // YouTube view IDs that ONLY appear in Shorts
    private static final String[] YT_SHORTS_IDS = {
        "com.google.android.youtube:id/reel_player_page_container",
        "com.google.android.youtube:id/shorts_container",
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/shorts_shelf_cell",
    };

    // YouTube view IDs that mean it's a normal video — NEVER back press if these exist
    private static final String[] YT_SAFE_IDS = {
        "com.google.android.youtube:id/player_control_play_pause_replay_button",
        "com.google.android.youtube:id/watch_metadata_app_bar",
        "com.google.android.youtube:id/player_fullscreen_button",
        "com.google.android.youtube:id/results",
        "com.google.android.youtube:id/subscription_list",
        "com.google.android.youtube:id/chip_cloud",
        "com.google.android.youtube:id/compact_link_button",
    };

    // Instagram view IDs that are Reels-specific
    private static final String[] IG_REELS_IDS = {
        "com.instagram.android:id/clips_viewer_pager",
        "com.instagram.android:id/clips_tab",
        "com.instagram.android:id/clips_media_container",
    };

    // Instagram view IDs that are safe — never back press if these exist
    private static final String[] IG_SAFE_IDS = {
        "com.instagram.android:id/direct_thread_view",
        "com.instagram.android:id/direct_inbox_container",
        "com.instagram.android:id/message_list",
        "com.instagram.android:id/profile_header_avatar_container",
        "com.instagram.android:id/follow_button",
        "com.instagram.android:id/row_feed_photo_imageview",
        "com.instagram.android:id/media_group",
        "com.instagram.android:id/stories_viewer_container",
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/row_feed_comment_textview_layout",
    };

    @Override
    protected void onServiceConnected() {
        handler = new Handler(Looper.getMainLooper());
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
            info.notificationTimeout = 100;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || handler == null) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // ONLY act on YouTube and Instagram — never touch any other app
        if (!pkg.equals(PKG_YOUTUBE) && !pkg.equals(PKG_INSTAGRAM)) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        if (prefs.getBoolean(MainActivity.KEY_SCHEDULE_ON, false)
                && !isWithinSchedule(prefs)) return;

        // Cancel any pending check and schedule a fresh one after screen settles
        if (pendingCheck != null) handler.removeCallbacks(pendingCheck);

        final String finalPkg = pkg;
        pendingCheck = () -> {
            if (finalPkg.equals(PKG_YOUTUBE)
                    && prefs.getBoolean(MainActivity.KEY_BLOCK_YT, true)) {
                checkYouTube(prefs);
            } else if (finalPkg.equals(PKG_INSTAGRAM)
                    && prefs.getBoolean(MainActivity.KEY_BLOCK_IG, true)) {
                checkInstagram(prefs);
            }
        };
        handler.postDelayed(pendingCheck, 400);
    }

    private void checkYouTube(SharedPreferences prefs) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Safe check first — if we're in a normal video/page, do nothing
        if (hasAny(root, YT_SAFE_IDS)) { root.recycle(); return; }

        boolean isShorts = hasAny(root, YT_SHORTS_IDS);
        root.recycle();

        if (isShorts) pressBack(prefs, true);
    }

    private void checkInstagram(SharedPreferences prefs) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Safe check — DMs, stories, posts, profiles
        if (hasAny(root, IG_SAFE_IDS)) { root.recycle(); return; }

        boolean isReels = hasAny(root, IG_REELS_IDS);

        // Fallback: Reels bottom nav tab is selected
        if (!isReels) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Reels");
            if (nodes != null) {
                for (AccessibilityNodeInfo n : nodes) {
                    // Must be both selected AND clickable to be the nav tab
                    if (n.isSelected() && n.isClickable()) {
                        isReels = true;
                        break;
                    }
                }
            }
        }

        root.recycle();
        if (isReels) pressBack(prefs, false);
    }

    private void pressBack(SharedPreferences prefs, boolean isYoutube) {
        long now = System.currentTimeMillis();
        if (now - lastBackTime < BACK_COOLDOWN) return;
        lastBackTime = now;
        performGlobalAction(GLOBAL_ACTION_BACK);

        // Update counter
        String key = isYoutube ? MainActivity.KEY_COUNT_YT : MainActivity.KEY_COUNT_IG;
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
    }

    private boolean hasAny(AccessibilityNodeInfo root, String[] ids) {
        for (String id : ids) {
            try {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean isWithinSchedule(SharedPreferences p) {
        int sh = p.getInt(MainActivity.KEY_START_HOUR, 9);
        int sm = p.getInt(MainActivity.KEY_START_MIN, 0);
        int eh = p.getInt(MainActivity.KEY_END_HOUR, 23);
        int em = p.getInt(MainActivity.KEY_END_MIN, 0);
        Calendar now = Calendar.getInstance();
        int cur   = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int start = sh * 60 + sm;
        int end   = eh * 60 + em;
        return start <= end ? (cur >= start && cur < end) : (cur >= start || cur < end);
    }

    @Override
    public void onInterrupt() {
        if (handler != null && pendingCheck != null) {
            handler.removeCallbacks(pendingCheck);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && pendingCheck != null) {
            handler.removeCallbacks(pendingCheck);
        }
    }
}
