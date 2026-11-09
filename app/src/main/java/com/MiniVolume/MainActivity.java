package com.MiniVolume;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView statusOverlay, statusShizuku;
    private Button btnBubble;

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int sizeSp) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(20), dp(20), dp(20), dp(20));
        sv.addView(ll);

        ll.addView(label("Mini Volume", 26));

        statusOverlay = label("", 16);
        ll.addView(statusOverlay);
        Button btnOverlay = new Button(this);
        btnOverlay.setText("Autoriser la superposition");
        btnOverlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        ll.addView(btnOverlay);

        btnBubble = new Button(this);
        btnBubble.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleBubble();
            }
        });
        ll.addView(btnBubble);

        statusShizuku = label("", 16);
        ll.addView(statusShizuku);
        Button btnShizuku = new Button(this);
        btnShizuku.setText("Autoriser Shizuku");
        btnShizuku.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!ShizukuHelper.libPresent()) {
                    Toast.makeText(MainActivity.this, "Lib Shizuku absente : voir README", Toast.LENGTH_LONG).show();
                } else if (!ShizukuHelper.binderAlive()) {
                    Toast.makeText(MainActivity.this, "Lance d'abord Shizuku", Toast.LENGTH_LONG).show();
                } else {
                    ShizukuHelper.requestPermission();
                }
            }
        });
        ll.addView(btnShizuku);

        ll.addView(label("Astuces\n"
                + "• Tap sur la bulle : ouvre/ferme le panneau\n"
                + "• Glisse la bulle où tu veux\n"
                + "• Maintiens + ou − : le volume défile\n"
                + "• Tap sur le niveau : slider 0-100 (Canal ↻ pour changer Média / Sonnerie / Alarme)\n"
                + "• Appui long sur le niveau : muet\n"
                + "• ⚡ : menu Reboot / Soft / Recovery / Download (double tap pour confirmer)\n"
                + "• 📱 : ouvre cette app", 14));

        setContentView(sv);
    }

    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void toggleBubble() {
        if (BubbleService.running) {
            stopService(new Intent(this, BubbleService.class));
            BubbleService.running = false;
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Autorise d'abord la superposition", Toast.LENGTH_LONG).show();
                return;
            }
            startForegroundService(new Intent(this, BubbleService.class));
            BubbleService.running = true;
        }
        refresh();
    }

    private void refresh() {
        statusOverlay.setText("Superposition : "
                + (Settings.canDrawOverlays(this) ? "OK" : "non autorisée"));
        btnBubble.setText(BubbleService.running ? "Arrêter la bulle" : "Démarrer la bulle");
        statusShizuku.setText("Shizuku : " + ShizukuHelper.status());
    }
}
