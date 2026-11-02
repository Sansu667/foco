package com.santiago.foco;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/** La pantalla que aparece cuando intentas abrir una red bloqueada. */
public class BlockActivity extends Activity {

    static final String EXTRA_APP = "app";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Button emergencyButton;
    private int secondsLeft;
    private boolean readyToConfirm;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            secondsLeft--;
            if (secondsLeft > 0) {
                emergencyButton.setText("Espera " + secondsLeft + " s y piénsalo…");
                handler.postDelayed(this, 1000);
            } else {
                readyToConfirm = true;
                emergencyButton.setEnabled(true);
                emergencyButton.setText("Sí, abrir " + Rules.EMERGENCY_MINUTES + " minutos");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        build();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        build();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Si sales a mitad de la cuenta regresiva, toca empezarla de nuevo.
        handler.removeCallbacks(countdown);
        readyToConfirm = false;
        if (emergencyButton != null) {
            emergencyButton.setEnabled(true);
            emergencyButton.setText(emergencyLabel());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si mientras tanto terminaste o se acabó el horario, esta pantalla sobra.
        if (!Rules.isBlocking(new Prefs(this), Calendar.getInstance())) finish();
    }

    private void build() {
        Prefs prefs = new Prefs(this);
        Calendar now = Calendar.getInstance();
        Rules.rollover(prefs, now);
        String app = getIntent().getStringExtra(EXTRA_APP);
        if (app == null) app = "Esta app";

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 24);
        root.setPadding(p, p, p, p);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(Ui.text(this, "Ahora no.", 34, Ui.TEXT, true));
        TextView sub = Ui.text(this, app + " está bloqueada hasta que termines tus pendientes exigentes.",
                18, Ui.MUTED, false);
        Ui.topMargin(sub, this, 8);
        root.addView(sub);

        LinearLayout card = Ui.card(this);
        List<Prefs.Task> pending = prefs.pendingTasks();
        if (pending.isEmpty()) {
            card.addView(Ui.text(this, "No tienes pendientes anotados", 16, Ui.TEXT, true));
            TextView t = Ui.text(this, "Así el bloqueo sigue hasta las " + Rules.fmt(prefs.endMin())
                    + ". Anota lo que tienes que sacar hoy y, cuando lo termines, las redes se liberan.",
                    15, Ui.MUTED, false);
            Ui.topMargin(t, this, 6);
            card.addView(t);
        } else {
            String title = pending.size() == 1 ? "Te falta 1 pendiente" : "Te faltan " + pending.size() + " pendientes";
            card.addView(Ui.text(this, title, 16, Ui.TEXT, true));
            int shown = 0;
            for (Prefs.Task task : pending) {
                if (shown == 4) {
                    TextView more = Ui.text(this, "y " + (pending.size() - 4) + " más", 15, Ui.MUTED, false);
                    Ui.topMargin(more, this, 6);
                    card.addView(more);
                    break;
                }
                TextView t = Ui.text(this, "○  " + task.text, 15, Ui.MUTED, false);
                Ui.topMargin(t, this, 6);
                card.addView(t);
                shown++;
            }
            if (!Rules.canCheckTasks(prefs, now)) {
                TextView t = Ui.text(this, "Tu bloque de trabajo empieza a las " + Rules.fmt(prefs.workMin()) + ".",
                        14, Ui.ACCENT, false);
                Ui.topMargin(t, this, 10);
                card.addView(t);
            }
        }
        root.addView(card);

        Button tasks = Ui.button(this, "Ver mis pendientes", true);
        tasks.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        root.addView(tasks);

        Button home = Ui.button(this, "Volver al inicio", false);
        home.setOnClickListener(v -> goHome());
        root.addView(home);

        emergencyButton = Ui.button(this, emergencyLabel(), false);
        emergencyButton.setBackground(null);
        emergencyButton.setTextColor(Ui.MUTED);
        emergencyButton.setOnClickListener(v -> onEmergency());
        root.addView(emergencyButton);

        setContentView(scroll);
    }

    private String emergencyLabel() {
        Prefs prefs = new Prefs(this);
        int left = Rules.EMERGENCY_MAX_PER_DAY - prefs.emergenciesUsed(Rules.dayKey(prefs, Calendar.getInstance()));
        if (left <= 0) return "Ya usaste tus pausas de emergencia de hoy";
        return "Es urgente: abrir " + Rules.EMERGENCY_MINUTES + " minutos (" + (left == 1 ? "queda 1 hoy" : "quedan " + left + " hoy") + ")";
    }

    private void onEmergency() {
        Prefs prefs = new Prefs(this);
        long day = Rules.dayKey(prefs, Calendar.getInstance());
        if (prefs.emergenciesUsed(day) >= Rules.EMERGENCY_MAX_PER_DAY) return;

        if (!readyToConfirm) {
            emergencyButton.setEnabled(false);
            secondsLeft = Rules.EMERGENCY_WAIT_SECONDS;
            emergencyButton.setText("Espera " + secondsLeft + " s y piénsalo…");
            handler.removeCallbacks(countdown);
            handler.postDelayed(countdown, 1000);
            return;
        }

        prefs.startEmergency(day, System.currentTimeMillis() + Rules.EMERGENCY_MINUTES * 60_000L);
        readyToConfirm = false;
        finish();
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }
}
