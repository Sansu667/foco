package com.santiago.foco;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends Activity {

    private Prefs prefs;
    private LinearLayout root;
    private EditText input;
    private TextView statusTitle;
    private TextView statusDetail;
    private String lastSignature = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            // Si cambió el estado (empezó el bloqueo, llegaron las 11, terminó la pausa), redibuja todo.
            if (!signature().equals(lastSignature)) render(); else updateStatus();
            handler.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        root.setPadding(p, dp(16), p, dp(40));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);

        input = new EditText(this);
        input.setHint("Ej.: escribir 2 páginas de la tesis");
        input.setHintTextColor(Ui.MUTED);
        input.setTextColor(Ui.TEXT);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTaskFromInput();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 20_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    // ------------------------------------------------------------------ dibujo

    private String signature() {
        Calendar now = Calendar.getInstance();
        return Rules.isLocked(prefs, now) + "|" + Rules.canCheckTasks(prefs, now) + "|"
                + Rules.emergencyActive(prefs) + "|" + Rules.dayKey(prefs, now) + "|"
                + BlockerService.isEnabled(this);
    }

    private void render() {
        Calendar now = Calendar.getInstance();
        Rules.rollover(prefs, now);
        boolean locked = Rules.isLocked(prefs, now);
        lastSignature = signature();

        root.removeAllViews();

        root.addView(Ui.text(this, "Foco", 30, Ui.TEXT, true));
        String date = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CO")).format(now.getTime());
        root.addView(Ui.text(this, date.substring(0, 1).toUpperCase(Locale.ROOT) + date.substring(1), 15, Ui.MUTED, false));

        if (!BlockerService.isEnabled(this)) root.addView(permissionCard());

        LinearLayout status = Ui.card(this);
        statusTitle = Ui.text(this, "", 22, Ui.TEXT, true);
        statusDetail = Ui.text(this, "", 15, Ui.MUTED, false);
        Ui.topMargin(statusDetail, this, 4);
        status.addView(statusTitle);
        status.addView(statusDetail);
        root.addView(status);
        updateStatus();

        root.addView(tasksCard(now, locked));
        root.addView(scheduleCard(locked));
        root.addView(appsCard(locked));
        root.addView(hyperOsCard());
    }

    private void updateStatus() {
        if (statusTitle == null) return;
        Calendar now = Calendar.getInstance();
        int pending = prefs.pendingCount();
        String end = Rules.fmt(prefs.endMin());

        if (!prefs.enabled()) {
            statusTitle.setText("Bloqueo apagado");
            statusTitle.setTextColor(Ui.MUTED);
            statusDetail.setText("Las redes están libres. Vuelve a encenderlo en la sección Horario.");
        } else if (!Rules.windowActive(prefs, now)) {
            statusTitle.setText("Fuera del horario");
            statusTitle.setTextColor(Ui.ACCENT);
            statusDetail.setText("Las redes están libres. El bloqueo empieza a las " + Rules.fmt(prefs.startMin()) + ".");
        } else if (Rules.freedToday(prefs, now)) {
            statusTitle.setText("Libre por hoy");
            statusTitle.setTextColor(Ui.ACCENT);
            statusDetail.setText("Terminaste tus pendientes exigentes. Mañana a las "
                    + Rules.fmt(prefs.startMin()) + " vuelve el bloqueo.");
        } else if (Rules.emergencyActive(prefs)) {
            long ms = prefs.emergencyUntil() - System.currentTimeMillis();
            long min = Math.max(1, (ms + 59_999) / 60_000);
            statusTitle.setText("Pausa de emergencia");
            statusTitle.setTextColor(Ui.ORANGE);
            statusDetail.setText("Te queda" + (min == 1 ? " 1 minuto" : "n " + min + " minutos")
                    + ". Después las redes se cierran solas.");
        } else {
            statusTitle.setText("Redes bloqueadas");
            statusTitle.setTextColor(Ui.RED);
            if (pending == 0) {
                statusDetail.setText("No tienes pendientes anotados, así que el bloqueo sigue hasta las " + end
                        + ". Anota tus pendientes exigentes y al terminarlos se liberan.");
            } else {
                String falta = pending == 1 ? "Te falta 1 pendiente" : "Te faltan " + pending + " pendientes";
                statusDetail.setText(falta + ". Cuando los termines se liberan; si no alcanzas, se liberan a las " + end + ".");
            }
        }
    }

    private LinearLayout permissionCard() {
        LinearLayout c = Ui.card(this);
        c.setBackground(Ui.rounded(0xFF3A2422, 16, this));
        c.addView(Ui.text(this, "Falta activar el permiso", 18, Ui.TEXT, true));
        TextView t = Ui.text(this,
                "Sin el permiso de accesibilidad Foco no puede ver qué app abres, así que no bloquea nada.\n\n"
                        + "1. Toca «Abrir accesibilidad», entra a «Apps descargadas» y activa Foco.\n"
                        + "2. Si sale un aviso de «ajuste restringido», toca «Info de la app», luego el menú ⋮ "
                        + "de arriba a la derecha y «Permitir ajustes restringidos». Después repite el paso 1.",
                15, Ui.MUTED, false);
        Ui.topMargin(t, this, 6);
        c.addView(t);
        Button a = Ui.button(this, "Abrir accesibilidad", true);
        a.setOnClickListener(v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        c.addView(a);
        Button b = Ui.button(this, "Info de la app", false);
        b.setOnClickListener(v -> openAppInfo());
        c.addView(b);
        return c;
    }

    // ------------------------------------------------------------------ pendientes

    private LinearLayout tasksCard(Calendar now, boolean locked) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pendientes exigentes de hoy", 18, Ui.TEXT, true));

        boolean canCheck = Rules.canCheckTasks(prefs, now);
        if (!canCheck) {
            TextView hint = Ui.text(this, "Podrás marcarlos desde las " + Rules.fmt(prefs.workMin())
                    + ", cuando empieza tu bloque de trabajo.", 14, Ui.ACCENT, false);
            Ui.topMargin(hint, this, 4);
            c.addView(hint);
        }

        List<Prefs.Task> tasks = prefs.tasks();
        if (tasks.isEmpty()) {
            TextView empty = Ui.text(this, "Todavía no hay nada. Anota lo que tienes que sacar hoy; "
                    + "puedes hacerlo desde la noche anterior.", 15, Ui.MUTED, false);
            Ui.topMargin(empty, this, 8);
            c.addView(empty);
        }

        for (Prefs.Task task : tasks) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(6);
            row.setLayoutParams(rlp);

            CheckBox cb = new CheckBox(this);
            cb.setText(task.text);
            cb.setTextSize(16);
            cb.setTextColor(task.done ? Ui.MUTED : Ui.TEXT);
            cb.setChecked(task.done);
            cb.setEnabled(canCheck);
            cb.setOnClickListener(v -> {
                if (cb.isChecked()) {
                    cb.setChecked(false); // se confirma antes de marcarlo
                    confirmDone(task);
                } else {
                    prefs.setDone(task.id, false);
                    render();
                }
            });
            row.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // Borrar pendientes durante el bloqueo sería la salida fácil, así que solo se puede fuera de él.
            if (!locked) {
                TextView del = Ui.text(this, "✕", 18, Ui.MUTED, false);
                int pad = dp(12);
                del.setPadding(pad, pad, pad / 2, pad);
                del.setOnClickListener(v -> {
                    prefs.removeTask(task.id);
                    render();
                });
                row.addView(del);
            }
            c.addView(row);
        }

        if (input.getParent() != null) ((ViewGroup) input.getParent()).removeView(input);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(10);
        c.addView(input, ilp);

        Button add = Ui.button(this, "Agregar pendiente", false);
        add.setOnClickListener(v -> addTaskFromInput());
        c.addView(add);

        if (locked) {
            TextView note = Ui.text(this, "Durante el bloqueo puedes agregar pendientes, pero no borrarlos.",
                    13, Ui.MUTED, false);
            Ui.topMargin(note, this, 8);
            c.addView(note);
        }
        return c;
    }

    private void addTaskFromInput() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        prefs.addTask(text);
        input.setText("");
        render();
    }

    private void confirmDone(Prefs.Task task) {
        new AlertDialog.Builder(this)
                .setTitle("¿Lo terminaste?")
                .setMessage("«" + task.text + "»")
                .setPositiveButton("Sí, terminado", (d, w) -> {
                    prefs.setDone(task.id, true);
                    if (Rules.tryUnlock(prefs, Calendar.getInstance())) {
                        Toast.makeText(this, "¡Listo! Las redes quedan libres por hoy.", Toast.LENGTH_LONG).show();
                    }
                    render();
                })
                .setNegativeButton("Todavía no", null)
                .show();
    }

    // ------------------------------------------------------------------ horario

    private LinearLayout scheduleCard(boolean locked) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Horario", 18, Ui.TEXT, true));

        c.addView(timeRow("Empieza el bloqueo", prefs.startMin(), locked, m -> {
            if (m == prefs.endMin()) return "El inicio y la hora límite no pueden ser la misma.";
            if (Rules.offset(prefs.workMin(), m) >= Rules.offset(prefs.endMin(), m))
                return "Con ese inicio, la hora de trabajo quedaría por fuera del bloqueo.";
            prefs.setStartMin(m);
            return null;
        }));
        c.addView(timeRow("Puedo marcar pendientes desde", prefs.workMin(), locked, m -> {
            if (Rules.offset(m, prefs.startMin()) >= Rules.offset(prefs.endMin(), prefs.startMin()))
                return "Tiene que quedar entre el inicio del bloqueo y la hora límite.";
            prefs.setWorkMin(m);
            return null;
        }));
        c.addView(timeRow("Hora límite (se libera aunque falten)", prefs.endMin(), locked, m -> {
            if (m == prefs.startMin()) return "El inicio y la hora límite no pueden ser la misma.";
            if (Rules.offset(prefs.workMin(), prefs.startMin()) >= Rules.offset(m, prefs.startMin()))
                return "La hora límite tiene que ser después de la hora de trabajo.";
            prefs.setEndMin(m);
            return null;
        }));

        Switch sw = new Switch(this);
        sw.setText("Bloqueo encendido");
        sw.setTextColor(Ui.TEXT);
        sw.setTextSize(16);
        sw.setChecked(prefs.enabled());
        sw.setEnabled(!locked);
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setEnabled(on);
            render();
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(14);
        c.addView(sw, slp);

        if (locked) {
            TextView note = Ui.text(this, "El horario y el interruptor se pueden cambiar cuando el bloqueo no esté activo.",
                    13, Ui.MUTED, false);
            Ui.topMargin(note, this, 8);
            c.addView(note);
        }
        return c;
    }

    interface TimeSetter {
        /** Devuelve un mensaje de error, o null si el cambio quedó guardado. */
        String apply(int minutes);
    }

    private View timeRow(String label, int minutes, boolean locked, TimeSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int v = dp(12);
        row.setPadding(0, v, 0, v);

        TextView l = Ui.text(this, label, 15, Ui.MUTED, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = Ui.text(this, Rules.fmt(minutes), 17, locked ? Ui.MUTED : Ui.ACCENT, true);
        row.addView(t);

        row.setOnClickListener(x -> {
            if (Rules.isLocked(prefs, Calendar.getInstance())) {
                Toast.makeText(this, "No se puede cambiar mientras el bloqueo está activo.", Toast.LENGTH_SHORT).show();
                return;
            }
            new TimePickerDialog(this, (picker, h, m) -> {
                String error = setter.apply(h * 60 + m);
                if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                render();
            }, minutes / 60, minutes % 60, false).show();
        });
        return row;
    }

    // ------------------------------------------------------------------ apps

    private LinearLayout appsCard(boolean locked) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Apps bloqueadas", 18, Ui.TEXT, true));
        Set<String> blocked = prefs.blockedApps();

        List<String[]> entries = new ArrayList<>();
        for (String[] a : Prefs.KNOWN_APPS) entries.add(new String[]{a[0], a[1]});
        for (String pkg : prefs.extraApps()) entries.add(new String[]{pkg, Prefs.labelFor(this, pkg)});

        for (String[] e : entries) {
            String pkg = e[0];
            boolean installed = isInstalled(pkg);
            boolean isExtra = prefs.extraApps().contains(pkg);
            // Las variantes que no tienes instaladas no hace falta mostrarlas.
            if (!installed && !isExtra && !isMainApp(pkg)) continue;

            CheckBox cb = new CheckBox(this);
            cb.setText(e[1] + (installed ? "" : "  (no instalada)"));
            cb.setTextColor(installed ? Ui.TEXT : Ui.MUTED);
            cb.setTextSize(16);
            cb.setChecked(blocked.contains(pkg));
            cb.setOnClickListener(v -> {
                boolean on = cb.isChecked();
                if (!on && Rules.isLocked(prefs, Calendar.getInstance())) {
                    cb.setChecked(true);
                    Toast.makeText(this, "Durante el bloqueo puedes agregar apps, pero no quitarlas.", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setBlocked(pkg, on);
            });
            c.addView(cb);
        }

        Button add = Ui.button(this, "Agregar otra app", false);
        add.setOnClickListener(v -> pickExtraApp());
        c.addView(add);

        TextView note = Ui.text(this, "Ojo: Foco bloquea las apps, no las páginas web. Si entras a instagram.com o "
                + "youtube.com desde el navegador, eso no lo detiene.", 13, Ui.MUTED, false);
        Ui.topMargin(note, this, 10);
        c.addView(note);
        return c;
    }

    /** Las cinco que pediste siempre se muestran, estén o no instaladas. */
    private boolean isMainApp(String pkg) {
        return pkg.equals("com.instagram.android") || pkg.equals("com.whatsapp") || pkg.equals("com.facebook.katana")
                || pkg.equals("com.zhiliaoapp.musically") || pkg.equals("com.google.android.youtube");
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void pickExtraApp() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> already = prefs.blockedApps();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || already.contains(pkg)) continue;
            byLabel.put(ri.loadLabel(pm).toString() + "\u0000" + pkg, pkg);
        }
        List<String> labels = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        for (String key : byLabel.keySet()) {
            labels.add(key.substring(0, key.indexOf('\u0000')));
            pkgs.add(byLabel.get(key));
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "No encontré más apps para agregar.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("¿Qué app agregas al bloqueo?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    prefs.addExtraApp(pkgs.get(which));
                    render();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ HyperOS

    private LinearLayout hyperOsCard() {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Para que HyperOS no la apague", 18, Ui.TEXT, true));
        TextView t = Ui.text(this,
                "Xiaomi cierra apps en segundo plano para ahorrar batería, y a veces de paso apaga el permiso de "
                        + "accesibilidad. Haz esto una vez:\n\n"
                        + "• Batería: déjala «Sin restricciones».\n"
                        + "• Inicio automático: actívalo para Foco.\n"
                        + "• En Recientes, mantén presionada la tarjeta de Foco y toca el candado.",
                15, Ui.MUTED, false);
        Ui.topMargin(t, this, 6);
        c.addView(t);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        Button battery = Ui.button(this, ignoring ? "Batería: sin restricciones ✓" : "Quitar restricción de batería", !ignoring);
        battery.setEnabled(!ignoring);
        battery.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            open(i);
        });
        c.addView(battery);

        Button autostart = Ui.button(this, "Inicio automático", false);
        autostart.setOnClickListener(v -> {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            if (!tryOpen(i)) openAppInfo();
        });
        c.addView(autostart);

        Button info = Ui.button(this, "Info de la app", false);
        info.setOnClickListener(v -> openAppInfo());
        c.addView(info);
        return c;
    }

    // ------------------------------------------------------------------ utilidades

    private void openAppInfo() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + getPackageName()));
        open(i);
    }

    private void open(Intent i) {
        if (!tryOpen(i)) Toast.makeText(this, "No pude abrir esa pantalla de ajustes.", Toast.LENGTH_SHORT).show();
    }

    private boolean tryOpen(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
