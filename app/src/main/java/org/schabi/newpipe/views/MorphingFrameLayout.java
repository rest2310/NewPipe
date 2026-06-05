package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

public class MorphingFrameLayout extends FrameLayout {
    private final RectF bounds = new RectF();
    private final Path clipPath = new Path();
    private float cornerRadius;

    public MorphingFrameLayout(final Context context) {
        super(context);
        init();
    }

    public MorphingFrameLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MorphingFrameLayout(final Context context, final AttributeSet attrs,
                               final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
    }

    public void setCornerRadius(final float radius) {
        if (cornerRadius == radius) {
            return;
        }
        cornerRadius = radius;
        setClipToOutline(cornerRadius > 0.0f);
        updateClipPath();
        invalidateOutline();
        invalidate();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bounds.set(0, 0, w, h);
        updateClipPath();
    }

    @Override
    public void draw(final Canvas canvas) {
        if (cornerRadius <= 0) {
            super.draw(canvas);
            return;
        }

        final int save = canvas.save();
        canvas.clipPath(clipPath);
        super.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void updateClipPath() {
        clipPath.reset();
        clipPath.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);
    }
}
