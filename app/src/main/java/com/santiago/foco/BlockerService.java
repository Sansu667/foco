package com.santiago.foco;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Calendar;

/**
 * Mira qué app pasa a primer plano. Si es una red bloqueada y el bloqueo está vigente,
 * manda el teléfono al inicio y muestra la pantalla de Foco.
 */
public class BlockerService extends AccessibilityService {

    /** Cada cuánto se revisa la app en pantalla, por si el bloqueo empieza o la pausa termina con la red abierta. */
    private static final long SWEEP_MS = 15_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastKickPkg;
    /** App que tiene una llamada en pantalla; mientras tanto no se bloquea. */
    private String callPkg;
    private long lastKickAt;

    private final Runnable sweep = new Runnable() {
        @Override
        public void run() {
            checkForeground();
            handler.postDelayed(this, SWEEP_MS);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler.removeCallbacks(sweep);
        handler.postDelayed(sweep, SWEEP_MS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();

        // Las pantallas de llamada (WhatsApp, Messenger, Instagram) se dejan pasar para poder contestar.
        CharSequence cls = event.getClassName();
        String c = cls == null ? "" : cls.toString();
        if (isCallScreen(c)) {
            callPkg = pkg;
            return;
        }
        // Cuando dentro de esa app se abre otra pantalla que no es de llamada, se acaba el permiso.
        if (pkg.equals(callPkg) && c.startsWith(pkg)) callPkg = null;

        maybeBlock(pkg);
    }

    private static boolean isCallScreen(String className) {
        String c = className.toLowerCase(java.util.Locale.ROOT);
        return c.contains("voip") || c.contains("calling") || c.contains("incomingcall");
    }

    private void checkForeground() {
        AccessibilityNodeInfo root;
        try {
            root = getRootInActiveWindow();
        } catch (RuntimeException e) {
            return;
        }
        if (root == null) return;
        CharSequence pkg = root.getPackageName();
        if (pkg != null) maybeBlock(pkg.toString());
    }

    private void maybeBlock(String pkg) {
        if (pkg.equals(callPkg)) return;
        Prefs prefs = new Prefs(this);
        if (!prefs.blockedApps().contains(pkg)) return;
        if (!Rules.isBlocking(prefs, Calendar.getInstance())) return;

        // Una sola apertura dispara varios eventos seguidos; con uno basta.
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastKickPkg) && now - lastKickAt < 700) return;
        lastKickPkg = pkg;
        lastKickAt = now;

        performGlobalAction(GLOBAL_ACTION_HOME);

        Intent i = new Intent(this, BlockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra(BlockActivity.EXTRA_APP, Prefs.labelFor(this, pkg));
        try {
            startActivity(i);
        } catch (RuntimeException ignored) {
            // Si el sistema no deja abrir la pantalla, al menos ya se volvió al inicio.
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sweep);
        super.onDestroy();
    }

    /** ¿El usuario activó el permiso de accesibilidad para Foco? */
    static boolean isEnabled(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(c, BlockerService.class);
        for (String part : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(part))) return true;
        }
        return false;
    }
}
