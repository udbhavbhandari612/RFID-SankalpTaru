package com.rr.hf.SankalpTaru.utils;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.rr.hf.SankalpTaru.R;

public class Fx {
    /**
     * @param ctx -context
     * @param v   -view
     */
    public static Animation slide_down(Context ctx, View v) {

        Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_down);
        if (a != null) {
            a.reset();
            if (v != null) {
                v.clearAnimation();
                v.startAnimation(a);
            }
        }
        return a;
    }

    /**
     * @param ctx -context
     * @param v   -view
     */
    public static Animation slide_up(Context ctx, View v) {

        Animation a = AnimationUtils.loadAnimation(ctx, R.anim.slide_up);
        if (a != null) {
            a.reset();
            if (v != null) {
                v.clearAnimation();
                v.startAnimation(a);
            }
        }
        return a;
    }
}
