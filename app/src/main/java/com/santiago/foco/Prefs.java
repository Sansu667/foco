package com.santiago.foco;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Todo lo que la app guarda en el teléfono. Nada sale del celular. */
final class Prefs {

    /** Apps conocidas: paquete, nombre visible y si viene bloqueada por defecto. */
    static final String[][] KNOWN_APPS = {
            {"com.instagram.android", "Instagram", "1"},
            {"com.whatsapp", "WhatsApp", "1"},
            {"com.whatsapp.w4b", "WhatsApp Business", "1"},
            {"com.facebook.katana", "Facebook", "1"},
            {"com.facebook.lite", "Facebook Lite", "1"},
            {"com.zhiliaoapp.musically", "TikTok", "1"},
            {"com.ss.android.ugc.trill", "TikTok (otra versión)", "1"},
            {"com.zhiliaoapp.musically.go", "TikTok Lite", "1"},
            {"com.google.android.youtube", "YouTube", "1"},
            {"com.facebook.orca", "Messenger", "0"},
    };

    static final class Task {
        final String id;
        final String text;
        boolean done;

        Task(String id, String text, boolean done) {
            this.id = id;
            this.text = text;
            this.done = done;
        }
    }

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("foco", Context.MODE_PRIVATE);
    }

    // ---------- Horario (minutos desde la medianoche) ----------

    boolean enabled() { return sp.getBoolean("enabled", true); }
    void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    /** Hora en que empieza el bloqueo cada día. Por defecto 4:00 a. m. */
    int startMin() { return sp.getInt("start", 4 * 60); }
    void setStartMin(int v) { sp.edit().putInt("start", v).apply(); }

    /** Desde qué hora se pueden marcar pendientes. Por defecto 11:00 a. m. */
    int workMin() { return sp.getInt("work", 11 * 60); }
    void setWorkMin(int v) { sp.edit().putInt("work", v).apply(); }

    /** Hora límite: si a esta hora no has terminado, el bloqueo se levanta igual. Por defecto 9:00 p. m. */
    int endMin() { return sp.getInt("end", 21 * 60); }
    void setEndMin(int v) { sp.edit().putInt("end", v).apply(); }

    // ---------- Estado del día ----------

    long unlockedDay() { return sp.getLong("unlockedDay", 0); }
    void setUnlockedDay(long v) { sp.edit().putLong("unlockedDay", v).apply(); }

    long tasksDay() { return sp.getLong("tasksDay", 0); }
    void setTasksDay(long v) { sp.edit().putLong("tasksDay", v).apply(); }

    long emergencyUntil() { return sp.getLong("emergencyUntil", 0); }

    int emergenciesUsed(long day) {
        return sp.getLong("emergencyDay", 0) == day ? sp.getInt("emergencyCount", 0) : 0;
    }

    void startEmergency(long day, long untilMillis) {
        int used = emergenciesUsed(day);
        sp.edit()
                .putLong("emergencyDay", day)
                .putInt("emergencyCount", used + 1)
                .putLong("emergencyUntil", untilMillis)
                .apply();
    }

    // ---------- Apps bloqueadas ----------

    Set<String> blockedApps() {
        Set<String> saved = sp.getStringSet("blocked", null);
        if (saved != null) return new HashSet<>(saved);
        Set<String> def = new HashSet<>();
        for (String[] a : KNOWN_APPS) if ("1".equals(a[2])) def.add(a[0]);
        return def;
    }

    void setBlocked(String pkg, boolean blocked) {
        Set<String> s = blockedApps();
        if (blocked) s.add(pkg); else s.remove(pkg);
        sp.edit().putStringSet("blocked", s).apply();
    }

    /** Paquetes que otras apps agregaron a mano (no están en KNOWN_APPS). */
    Set<String> extraApps() {
        Set<String> s = sp.getStringSet("extra", null);
        return s == null ? new HashSet<>() : new HashSet<>(s);
    }

    void addExtraApp(String pkg) {
        Set<String> s = extraApps();
        s.add(pkg);
        sp.edit().putStringSet("extra", s).apply();
        setBlocked(pkg, true);
    }

    static String labelFor(Context c, String pkg) {
        for (String[] a : KNOWN_APPS) if (a[0].equals(pkg)) return a[1].replace(" (otra versión)", "");
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    // ---------- Pendientes ----------

    List<Task> tasks() {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString("tasks", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Task(o.getString("id"), o.getString("text"), o.optBoolean("done", false)));
            }
        } catch (JSONException ignored) {
            // Si algo se corrompe, arrancamos con la lista vacía.
        }
        return out;
    }

    void saveTasks(List<Task> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Task t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("text", t.text);
                o.put("done", t.done);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        sp.edit().putString("tasks", arr.toString()).apply();
    }

    void addTask(String text) {
        List<Task> l = tasks();
        l.add(new Task(UUID.randomUUID().toString(), text, false));
        saveTasks(l);
    }

    void setDone(String id, boolean done) {
        List<Task> l = tasks();
        for (Task t : l) if (t.id.equals(id)) t.done = done;
        saveTasks(l);
    }

    void removeTask(String id) {
        List<Task> l = tasks();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).id.equals(id)) l.remove(i);
        saveTasks(l);
    }

    int pendingCount() {
        int n = 0;
        for (Task t : tasks()) if (!t.done) n++;
        return n;
    }

    List<Task> pendingTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : tasks()) if (!t.done) out.add(t);
        return out;
    }
}
