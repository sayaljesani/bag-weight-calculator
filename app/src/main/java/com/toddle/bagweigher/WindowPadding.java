package com.toddle.bagweigher;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15 draws every app edge to edge, so without this the top of a screen
 * hides under the status bar and the buttons at the bottom hide under the
 * navigation bar. Padding the root view by the system bars puts everything back
 * on screen; the keyboard is included so the entry screen lifts above it.
 */
public final class WindowPadding {

    private WindowPadding() { }

    public static void apply(final View root) {
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat windowInsets) {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return windowInsets;
            }
        });
        ViewCompat.requestApplyInsets(root);
    }
}
