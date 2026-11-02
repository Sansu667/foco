package com.santiago.foco;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Las reglas del bloqueo, en un solo lugar.
 *
 * Un "día" de Foco empieza a la hora de inicio del bloqueo (4:00 a. m. por defecto),
 * así que lo que haces a la 1 de la mañana todavía cuenta para el día anterior.
 */
final class Rules {

    static final int EMERGENCY_MAX_PER_DAY = 2;
    static final int EMERGENCY_MINUTES = 5;
    static final int EMERGENCY_WAIT_SECONDS = 20;

    private Rules() {}

    static int minutesOf(Calendar c) {
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    /** Minutos transcurridos desde la hora de inicio, contando en círculo de 24 horas. */
    static int offset(int minute, int start) {
        return ((minute - start) % 1440 + 1440) % 1440;
    }

    static boolean inWindow(int minute, int start, int end) {
        if (start == end) return false;
        return offset(minute, start) < offset(end, start);
    }

    /** Identificador del día de Foco, por ejemplo 20261102. */
    static long dayKey(Prefs p, Calendar now) {
        Calendar c = (Calendar) now.clone();
        c.add(Calendar.MINUTE, -p.startMin());
        return c.get(Calendar.YEAR) * 10000L + (c.get(Calendar.MONTH) + 1) * 100L + c.get(Calendar.DAY_OF_MONTH);
    }

    /** Estamos dentro del horario de bloqueo (sin mirar si ya terminaste). */
    static boolean windowActive(Prefs p, Calendar now) {
        return p.enabled() && inWindow(minutesOf(now), p.startMin(), p.endMin());
    }

    static boolean freedToday(Prefs p, Calendar now) {
        return p.unlockedDay() == dayKey(p, now);
    }

    static boolean emergencyActive(Prefs p) {
        return System.currentTimeMillis() < p.emergencyUntil();
    }

    /** El bloqueo está vigente: los ajustes quedan congelados (aunque haya una pausa de emergencia). */
    static boolean isLocked(Prefs p, Calendar now) {
        return windowActive(p, now) && !freedToday(p, now);
    }

    /** Las redes se deben cerrar ahora mismo. */
    static boolean isBlocking(Prefs p, Calendar now) {
        return isLocked(p, now) && !emergencyActive(p);
    }

    /** Durante el bloqueo, los pendientes solo se marcan desde la hora de trabajo (11:00 por defecto). */
    static boolean canCheckTasks(Prefs p, Calendar now) {
        if (!isLocked(p, now)) return true;
        return offset(minutesOf(now), p.startMin()) >= offset(p.workMin(), p.startMin());
    }

    /**
     * Al empezar un día nuevo, los pendientes que ya hiciste se borran y los que
     * quedaron sin hacer pasan al día siguiente.
     */
    static void rollover(Prefs p, Calendar now) {
        long key = dayKey(p, now);
        if (p.tasksDay() == key) return;
        List<Prefs.Task> list = p.tasks();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).done) list.remove(i);
        p.saveTasks(list);
        p.setTasksDay(key);
    }

    /** Si todos los pendientes de hoy están hechos, libera las redes. Devuelve true si acaba de liberarlas. */
    static boolean tryUnlock(Prefs p, Calendar now) {
        if (!isLocked(p, now)) return false;
        List<Prefs.Task> list = p.tasks();
        if (list.isEmpty()) return false;
        for (Prefs.Task t : list) if (!t.done) return false;
        p.setUnlockedDay(dayKey(p, now));
        return true;
    }

    static String fmt(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        String suf = h < 12 ? "a. m." : "p. m.";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, m, suf);
    }
}
