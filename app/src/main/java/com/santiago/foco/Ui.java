package com.santiago.foco;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pequeñas piezas de interfaz para no repetir estilos en cada pantalla. */
final class Ui {

    static final int BG = 0xFF14161A;
    static final int CARD = 0xFF1F2228;
    static final int LINE = 0xFF2C3038;
    static final int TEXT = 0xFFF1F2F4;
    static final int MUTED = 0xFF9AA0AA;
    static final int ACCENT = 0xFF7FB77E;
    static final int RED = 0xFFE07065;
    static final int ORANGE = 0xFFE5A34B;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable rounded(int fill, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(rounded(CARD, 16, c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    /** Botón lleno (principal) o con fondo de tarjeta (secundario). */
    static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? 0xFF10140F : TEXT);
        b.setBackground(rounded(primary ? ACCENT : LINE, 12, c));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 48));
        int h = dp(c, 14);
        b.setPadding(h, 0, h, 0);
        b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    static void topMargin(TextView v, Context c, float dpValue) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, dpValue);
        v.setLayoutParams(lp);
    }
}
