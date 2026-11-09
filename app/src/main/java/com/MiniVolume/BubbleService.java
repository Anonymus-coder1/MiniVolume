package com.MiniVolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class BubbleService extends Service {

    public static boolean running = false;

    private static final String CHANNEL = "mini_volume";
    private static final String ACTION_STOP = "com.MiniVolume.STOP";

    private static final int[] STREAMS = {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM};
    private static final String[] NAMES = {"Média", "Sonnerie", "Alarme"};

    private WindowManager wm;
    private AudioManager am;
    private WindowManager.LayoutParams lp;
    private LinearLayout root, panel, menuRow, sliderRow;
    private TextView bubble, levelTv, chanTv, armed;
    private SeekBar seek;
    private boolean seeking = false;
    private boolean warned = false;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean expanded = false;
    private int streamIdx = 0;
    private int screenW, screenH, slop;

    private final BroadcastReceiver volReceiver = new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            refreshLevel();
        }
    };

    private final Runnable disarm = new Runnable() {
        public void run() {
            if (armed != null) {
                armed.setText((String) armed.getTag());
                armed = null;
            }
        }
    };

    private final Runnable clampRunnable = new Runnable() {
        public void run() {
            if (root == null) return;
            lp.x = clampX(lp.x);
            lp.y = clampY(lp.y);
            try {
                wm.updateViewLayout(root, lp);
            } catch (Throwable t) {
                // vue detachee
            }
        }
    };

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        am = (AudioManager) getSystemService(AUDIO_SERVICE);
        slop = ViewConfiguration.get(this).getScaledTouchSlop();
        registerReceiver(volReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        if (root == null) {
            try {
                buildBubble();
            } catch (Throwable t) {
                Toast.makeText(this, "Superposition refusée : active l'autorisation", Toast.LENGTH_LONG).show();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        return START_STICKY;
    }

    public void onDestroy() {
        running = false;
        h.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(volReceiver);
        } catch (Throwable t) {
            // ignore
        }
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Throwable t) {
                // ignore
            }
            root = null;
        }
        super.onDestroy();
    }

    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateScreenSize();
        h.postDelayed(clampRunnable, 150);
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Bulle volume", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, BubbleService.class).setAction(ACTION_STOP), 0);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentTitle("Mini Volume")
                .setContentText("Bulle active - touche pour ouvrir l'app")
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_manage, "Ouvrir l'app", open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stop)
                .build();
        startForeground(1, n);
    }

    // ---------- UI ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateScreenSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
    }

    private int clampX(int x) {
        int w = root.getWidth() > 0 ? root.getWidth() : dp(52);
        return Math.max(0, Math.min(x, screenW - w));
    }

    private int clampY(int y) {
        int hh = root.getHeight() > 0 ? root.getHeight() : dp(52);
        return Math.max(0, Math.min(y, screenH - hh));
    }

    private TextView roundBtn(String text, int sizeDp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xDD3D5AFE);
        t.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        t.setLayoutParams(p);
        return t;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(28));
        g.setColor(color);
        return g;
    }

    private void buildBubble() {
        updateScreenSize();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Bulle deplacable
        bubble = roundBtn("🔊", 52);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE61E1E2C);
        bg.setStroke(dp(2), 0xFF3D5AFE);
        bubble.setBackground(bg);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;

            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX() - downX;
                        float my = e.getRawY() - downY;
                        if (!moved && Math.hypot(mx, my) < slop) return true;
                        moved = true;
                        lp.x = clampX(startX + (int) mx);
                        lp.y = clampY(startY + (int) my);
                        wm.updateViewLayout(root, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) savePosition();
                        else toggleExpanded();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return true;
            }
        });

        // Panneau volume : - niveau + ⚡
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setBackground(pill(0xE61E1E2C));
        panel.setPadding(dp(4), dp(2), dp(4), dp(2));
        panel.setVisibility(View.GONE);

        TextView minus = roundBtn("−", 44);
        minus.setOnTouchListener(new RepeatTouch(AudioManager.ADJUST_LOWER));
        TextView plus = roundBtn("+", 44);
        plus.setOnTouchListener(new RepeatTouch(AudioManager.ADJUST_RAISE));

        levelTv = new TextView(this);
        levelTv.setTextColor(0xFFFFFFFF);
        levelTv.setTextSize(12);
        levelTv.setGravity(Gravity.CENTER);
        levelTv.setMinWidth(dp(64));
        // Tap sur le niveau : ouvre/ferme le slider 0-100
        levelTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sliderRow.setVisibility(sliderRow.getVisibility() == View.VISIBLE
                        ? View.GONE : View.VISIBLE);
                refreshLevel();
                h.postDelayed(clampRunnable, 120);
            }
        });
        levelTv.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                try {
                    am.adjustStreamVolume(STREAMS[streamIdx], AudioManager.ADJUST_TOGGLE_MUTE, 0);
                } catch (Throwable t) {
                    Toast.makeText(BubbleService.this, "Mute refusé (mode Ne pas déranger ?)", Toast.LENGTH_SHORT).show();
                }
                refreshLevel();
                return true;
            }
        });

        TextView bolt = roundBtn("⚡", 44);
        bolt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                menuRow.setVisibility(menuRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                disarm.run();
                h.postDelayed(clampRunnable, 120);
            }
        });

        panel.addView(minus);
        panel.addView(levelTv);
        panel.addView(plus);
        panel.addView(bolt);

        // Bouton : ouvrir l'app
        TextView openBtn = roundBtn("📱", 44);
        openBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openApp();
            }
        });
        panel.addView(openBtn);

        // Ligne slider 0-100 : [Canal] [-------o-------]
        sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        sliderRow.setBackground(pill(0xE61E1E2C));
        sliderRow.setPadding(dp(10), dp(4), dp(10), dp(4));
        sliderRow.setVisibility(View.GONE);

        chanTv = new TextView(this);
        chanTv.setText("Canal ↻");
        chanTv.setTextColor(0xFFFFFFFF);
        chanTv.setTextSize(12);
        chanTv.setGravity(Gravity.CENTER);
        chanTv.setPadding(dp(10), dp(8), dp(10), dp(8));
        chanTv.setBackground(pill(0xFF37374D));
        chanTv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                streamIdx = (streamIdx + 1) % STREAMS.length;
                refreshLevel();
            }
        });

        seek = new SeekBar(this);
        seek.setMax(100);
        seek.setLayoutParams(new LinearLayout.LayoutParams(dp(180),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        try {
            seek.getProgressDrawable().setTint(0xFF3D5AFE);
            seek.getThumb().setTint(0xFFFFFFFF);
        } catch (Throwable t) {
            // teinte par defaut
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) setPercent(p);
            }

            public void onStartTrackingTouch(SeekBar s) {
                seeking = true;
                warned = false;
            }

            public void onStopTrackingTouch(SeekBar s) {
                seeking = false;
                refreshLevel();
            }
        });

        sliderRow.addView(chanTv);
        sliderRow.addView(seek);

        row.addView(bubble);
        row.addView(panel);

        // Menu Shizuku
        menuRow = new LinearLayout(this);
        menuRow.setOrientation(LinearLayout.HORIZONTAL);
        menuRow.setBackground(pill(0xE61E1E2C));
        menuRow.setPadding(dp(6), dp(4), dp(6), dp(4));
        menuRow.setVisibility(View.GONE);
        addMenuBtn("Reboot", "reboot");
        addMenuBtn("Soft", "soft");
        addMenuBtn("Recovery", "recovery");
        addMenuBtn("Download", "download");

        root.addView(row);
        root.addView(sliderRow);
        root.addView(menuRow);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences sp = getSharedPreferences("bubble", MODE_PRIVATE);
        lp.x = sp.getInt("x", dp(8));
        lp.y = sp.getInt("y", dp(220));

        wm.addView(root, lp);
        refreshLevel();
        h.postDelayed(clampRunnable, 150);
    }

    private void addMenuBtn(final String label, final String action) {
        final TextView b = new TextView(this);
        b.setText(label);
        b.setTag(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(16));
        g.setColor(0xFF37374D);
        b.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(p);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onMenuTap(b, label, action);
            }
        });
        menuRow.addView(b);
    }

    private void onMenuTap(TextView b, String label, final String action) {
        if (armed == b) {
            h.removeCallbacks(disarm);
            disarm.run();
            runShizuku(action);
            return;
        }
        disarm.run();
        h.removeCallbacks(disarm);
        armed = b;
        b.setText("Confirmer ?");
        h.postDelayed(disarm, 3000);
        h.postDelayed(clampRunnable, 120);
    }

    private void runShizuku(final String action) {
        new Thread(new Runnable() {
            public void run() {
                final String msg = ShizukuHelper.perform(action);
                if (msg != null) {
                    h.post(new Runnable() {
                        public void run() {
                            Toast.makeText(BubbleService.this, msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void toggleExpanded() {
        expanded = !expanded;
        panel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (!expanded) {
            menuRow.setVisibility(View.GONE);
            sliderRow.setVisibility(View.GONE);
            disarm.run();
        }
        refreshLevel();
        h.postDelayed(clampRunnable, 120);
    }

    private void savePosition() {
        getSharedPreferences("bubble", MODE_PRIVATE).edit()
                .putInt("x", lp.x).putInt("y", lp.y).apply();
    }

    // ---------- Volume ----------

    private void adjust(int dir) {
        try {
            am.adjustStreamVolume(STREAMS[streamIdx], dir, 0);
        } catch (Throwable t) {
            Toast.makeText(this, "Réglage refusé (mode Ne pas déranger ?)", Toast.LENGTH_SHORT).show();
        }
        refreshLevel();
    }

    private void refreshLevel() {
        if (root == null) return;
        int s = STREAMS[streamIdx];
        int v = am.getStreamVolume(s);
        int m = am.getStreamMaxVolume(s);
        boolean mute = am.isStreamMute(s);
        int pct = seeking ? seek.getProgress() : (m > 0 ? Math.round(v * 100f / m) : 0);
        levelTv.setText(NAMES[streamIdx] + "\n" + (mute ? "muet" : pct + "%"));
        if (!seeking) seek.setProgress(pct);
        bubble.setText((mute || v == 0) ? "🔈" : "🔊");
    }

    /** Slider 0-100 -> cran reel le plus proche du canal choisi. */
    private void setPercent(int p) {
        int s = STREAMS[streamIdx];
        int m = am.getStreamMaxVolume(s);
        int idx = Math.round(p * m / 100f);
        try {
            am.setStreamVolume(s, idx, 0);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Toast.makeText(this, "Réglage refusé (mode Ne pas déranger ?)", Toast.LENGTH_SHORT).show();
            }
        }
        refreshLevel();
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "Impossible d'ouvrir l'app", Toast.LENGTH_SHORT).show();
        }
    }

    private class RepeatTouch implements View.OnTouchListener {
        private final int dir;
        private final Runnable tick = new Runnable() {
            public void run() {
                adjust(dir);
                h.postDelayed(this, 120);
            }
        };

        RepeatTouch(int dir) {
            this.dir = dir;
        }

        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    adjust(dir);
                    h.postDelayed(tick, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    h.removeCallbacks(tick);
                    return true;
            }
            return true;
        }
    }
}
