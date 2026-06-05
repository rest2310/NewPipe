package org.schabi.newpipe.util;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;

public final class SimpleDialog {
    private SimpleDialog() {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public static void show(final Context context,
                            @StringRes final int titleRes,
                            @StringRes final int messageRes,
                            @StringRes final int negativeRes,
                            final Runnable negativeAction,
                            @StringRes final int neutralRes,
                            final Runnable neutralAction,
                            @StringRes final int positiveRes,
                            final Runnable positiveAction) {
        final Dialog dialog = new Dialog(context);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 14));

        final GradientDrawable background = new GradientDrawable();
        background.setColor(ContextCompat.getColor(context, R.color.light_background_color));
        background.setCornerRadius(dp(context, 18));
        root.setBackground(background);

        if (titleRes != 0) {
            final TextView title = textView(context, context.getString(titleRes), 20, true);
            root.addView(title, matchWrap());
        }

        if (messageRes != 0) {
            final TextView message = textView(context, context.getString(messageRes), 16, false);
            final LinearLayout.LayoutParams params = matchWrap();
            params.topMargin = titleRes == 0 ? 0 : dp(context, 12);
            root.addView(message, params);
        }

        final LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(context, 18);
        root.addView(actions, actionsParams);

        addButton(context, actions, dialog, negativeRes, negativeAction);
        addButton(context, actions, dialog, neutralRes, neutralAction);
        addButton(context, actions, dialog, positiveRes, positiveAction);

        dialog.setContentView(root);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        final Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(
                    (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView textView(final Context context, final String text, final int textSizeSp,
                                     final boolean bold) {
        final TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(context, R.color.contrastColor));
        textView.setTextSize(textSizeSp);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private static void addButton(final Context context,
                                  final LinearLayout actions,
                                  final Dialog dialog,
                                  @StringRes final int textRes, final Runnable action) {
        if (textRes == 0) {
            return;
        }

        final TextView button = textView(context, context.getString(textRes), 14, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        button.setTextColor(ContextCompat.getColor(context, R.color.feed_filter_selected_start));
        button.setOnClickListener(v -> {
            dialog.dismiss();
            if (action != null) {
                action.run();
            }
        });
        actions.addView(button, wrapWrap());
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(final Context context, final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
