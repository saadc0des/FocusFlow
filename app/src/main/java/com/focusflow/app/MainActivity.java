package com.focusflow.app;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "focusflow_prefs";
    public static final String KEY_BLOCK_YT = "block_youtube_shorts";
    public static final String KEY_BLOCK_IG = "block_ig_reels";
    public static final String KEY_ALLOW_FRIENDS = "allow_friends_reels";
    public static final String KEY_SCHEDULE_ON = "schedule_on";
    public static final String KEY_START_HOUR = "start_hour";
    public static final String KEY_START_MIN = "start_min";
    public static final String KEY_END_HOUR = "end_hour";
    public static final String KEY_END_MIN = "end_min";
    public static final String KEY_COUNT_YT = "count_yt";
    public static final String KEY_COUNT_IG = "count_ig";

    private Switch switchYoutube, switchInstagram, switchFriends, switchSchedule;
    private TextView statusText, statusSubtext, btnEnable;
    private TextView countYt, countIg, btnResetCounts;
    private TextView btnStartTime, btnEndTime, scheduleSummary;
    private CardView statusCard, friendsCard;
    private View scheduleTimeRow;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Bind views
        switchYoutube = findViewById(R.id.switch_youtube);
        switchInstagram = findViewById(R.id.switch_instagram);
        switchFriends = findViewById(R.id.switch_friends_reels);
        switchSchedule = findViewById(R.id.switch_schedule);
        statusText = findViewById(R.id.status_text);
        statusSubtext = findViewById(R.id.status_subtext);
        statusCard = findViewById(R.id.status_card);
        btnEnable = findViewById(R.id.btn_enable_accessibility);
        friendsCard = findViewById(R.id.friends_reels_card);
        countYt = findViewById(R.id.count_yt);
        countIg = findViewById(R.id.count_ig);
        btnResetCounts = findViewById(R.id.btn_reset_counts);
        btnStartTime = findViewById(R.id.btn_start_time);
        btnEndTime = findViewById(R.id.btn_end_time);
        scheduleSummary = findViewById(R.id.schedule_summary);
        scheduleTimeRow = findViewById(R.id.schedule_time_row);

        // Load settings
        switchYoutube.setChecked(prefs.getBoolean(KEY_BLOCK_YT, true));
        switchInstagram.setChecked(prefs.getBoolean(KEY_BLOCK_IG, true));
        switchFriends.setChecked(prefs.getBoolean(KEY_ALLOW_FRIENDS, true));
        switchSchedule.setChecked(prefs.getBoolean(KEY_SCHEDULE_ON, false));

        updateFriendsVisibility();
        updateScheduleUI();
        updateCounts();

        // Listeners
        switchYoutube.setOnCheckedChangeListener((b, checked) ->
            prefs.edit().putBoolean(KEY_BLOCK_YT, checked).apply());

        switchInstagram.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_BLOCK_IG, checked).apply();
            updateFriendsVisibility();
        });

        switchFriends.setOnCheckedChangeListener((b, checked) ->
            prefs.edit().putBoolean(KEY_ALLOW_FRIENDS, checked).apply());

        switchSchedule.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(KEY_SCHEDULE_ON, checked).apply();
            updateScheduleUI();
        });

        btnEnable.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        btnStartTime.setOnClickListener(v -> pickTime(true));
        btnEndTime.setOnClickListener(v -> pickTime(false));

        btnResetCounts.setOnClickListener(v -> {
            prefs.edit().putInt(KEY_COUNT_YT, 0).putInt(KEY_COUNT_IG, 0).apply();
            updateCounts();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusUI();
        updateCounts();
    }

    private void pickTime(boolean isStart) {
        int hour = prefs.getInt(isStart ? KEY_START_HOUR : KEY_END_HOUR, isStart ? 9 : 23);
        int min = prefs.getInt(isStart ? KEY_START_MIN : KEY_END_MIN, 0);
        new TimePickerDialog(this, (view, h, m) -> {
            prefs.edit()
                .putInt(isStart ? KEY_START_HOUR : KEY_END_HOUR, h)
                .putInt(isStart ? KEY_START_MIN : KEY_END_MIN, m)
                .apply();
            updateScheduleUI();
        }, hour, min, true).show();
    }

    private void updateScheduleUI() {
        boolean on = switchSchedule.isChecked();
        scheduleTimeRow.setVisibility(on ? View.VISIBLE : View.GONE);

        int sh = prefs.getInt(KEY_START_HOUR, 9);
        int sm = prefs.getInt(KEY_START_MIN, 0);
        int eh = prefs.getInt(KEY_END_HOUR, 23);
        int em = prefs.getInt(KEY_END_MIN, 0);

        btnStartTime.setText(String.format("%02d:%02d", sh, sm));
        btnEndTime.setText(String.format("%02d:%02d", eh, em));

        if (on) {
            scheduleSummary.setText(String.format("Blocking %02d:%02d – %02d:%02d", sh, sm, eh, em));
        } else {
            scheduleSummary.setText("Always blocking");
        }
    }

    private void updateCounts() {
        countYt.setText(String.valueOf(prefs.getInt(KEY_COUNT_YT, 0)));
        countIg.setText(String.valueOf(prefs.getInt(KEY_COUNT_IG, 0)));
    }

    private void updateFriendsVisibility() {
        if (friendsCard != null)
            friendsCard.setVisibility(switchInstagram.isChecked() ? View.VISIBLE : View.GONE);
    }

    private void updateStatusUI() {
        boolean active = isAccessibilityOn();
        statusCard.setCardBackgroundColor(getColor(
            active ? R.color.status_active_bg : R.color.status_inactive_bg));
        statusText.setText(active ? "Protection Active" : "Not Active");
        statusText.setTextColor(getColor(
            active ? R.color.status_active_text : R.color.status_inactive_text));
        statusSubtext.setText(active
            ? "FocusFlow is running in the background"
            : "Tap below to enable the accessibility service");
        btnEnable.setVisibility(active ? View.GONE : View.VISIBLE);
    }

    private boolean isAccessibilityOn() {
        String name = getPackageName() + "/" + BlockerAccessibilityService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(
                getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled == 0) return false;
            String services = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services == null) return false;
            TextUtils.SimpleStringSplitter s = new TextUtils.SimpleStringSplitter(':');
            s.setString(services);
            while (s.hasNext()) if (s.next().equalsIgnoreCase(name)) return true;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }
}
