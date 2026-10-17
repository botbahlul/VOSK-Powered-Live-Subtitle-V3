package org.vosk.livesubtitle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class GlobalOverlay {
    private final Context mContext;
    private final WindowManager mWindowManager;
    private final View mRemoveView;
    private final Map<View, OverlayState> mOverlayStates = new HashMap<>();

    public static int DisplayWidth;
    public static int DisplayHeight;

    private static class OverlayState {
        WindowManager.LayoutParams layoutParams;
        View.OnClickListener onClickListener;
        View.OnLongClickListener onLongClickListener;
        OnRemoveOverlayListener onRemoveOverlayListener;
        int initialX;
        int initialY;
        int initialWidth;
        int initialHeight;
        float initialTouchX;
        float initialTouchY;
        boolean isOverRemoveView;
        boolean isDragging;
    }

    public GlobalOverlay(Context context) {
        this(context, newRemoveView(context));
    }

    public GlobalOverlay(Context context, View RemoveView) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mRemoveView = RemoveView;
        setupRemoveView(mRemoveView);
    }

    @SuppressLint("InflateParams")
    private static View newRemoveView(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.overlay_remove_view, null);
    }

    private void setupRemoveView(View RemoveView) {
        RemoveView.setVisibility(View.GONE);
        mWindowManager.addView(RemoveView, newWindowManagerLayoutParamsForRemoveView());
    }

    public final void addOverlayView(View view, int width, int height, int x, int y, View.OnClickListener onClickListener) {
        addOverlayView(view, width, height, x, y, onClickListener, null, null);
    }


    public final void addOverlayView(View view, int width, int height, int x, int y, View.OnClickListener onClickListener, View.OnLongClickListener onLongClickListener, OnRemoveOverlayListener onRemoveOverlayListener) {
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);
        DisplayWidth = displayMetrics.widthPixels;
        DisplayHeight = displayMetrics.heightPixels;

        OverlayState state = new OverlayState();
        state.layoutParams = newWindowManagerLayoutParams();
        state.layoutParams.width = width;
        state.layoutParams.height = height;
        state.layoutParams.x = x;
        state.layoutParams.y = y;
        state.onClickListener = onClickListener;
        state.onLongClickListener = onLongClickListener;
        state.onRemoveOverlayListener = onRemoveOverlayListener;

        add_overlay_close_handle(view);

        mOverlayStates.put(view, state);
        mWindowManager.addView(view, state.layoutParams);

        View.OnTouchListener dragListener = newSimpleOnTouchListener(view, state);
        View.OnTouchListener resizeListener = newResizeTouchListener(view, state);
        setTouchListenerRecursively(view, dragListener, resizeListener);
    }

    private void add_overlay_close_handle(final View overlayView) {
        if (!(overlayView instanceof FrameLayout)) {
            return;
        }

        FrameLayout container = (FrameLayout) overlayView;

        TextView overlay_close_handle = new TextView(mContext);
        overlay_close_handle.setText("×");
        //overlay_close_handle.setTextColor(Color.WHITE);
        overlay_close_handle.setTextColor(Color.parseColor("#80FFFFFF"));
        overlay_close_handle.setTextSize(24);
        overlay_close_handle.setGravity(Gravity.CENTER);
        overlay_close_handle.setIncludeFontPadding(false);
        overlay_close_handle.setContentDescription("Close overlay");

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(Color.TRANSPARENT);
        overlay_close_handle.setBackground(background);

        int size = dpToPx(28);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                size,
                size,
                Gravity.TOP | Gravity.RIGHT);

        overlay_close_handle.setLayoutParams(params);

        //overlay_close_handle.setOnClickListener(v -> {
        //    removeOverlayView(overlayView, true);
        //});
        overlay_close_handle.setOnClickListener(v -> {
            closeOverlayWindow(overlayView);
        });

        container.addView(overlay_close_handle);
    }

    private void setTouchListenerRecursively(View view, View.OnTouchListener dragListener, View.OnTouchListener resizeListener) {
        if (view.getContentDescription() != null &&
                "Close overlay".contentEquals(view.getContentDescription())) {
            view.setOnTouchListener(null);
            return;
        }

        if (view.getId() == R.id.overlay_resize_handle) {
            view.setOnTouchListener(resizeListener);
            return;
        }

        view.setOnTouchListener(dragListener);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;

            for (int i = 0; i < group.getChildCount(); i++) {
                setTouchListenerRecursively(group.getChildAt(i), dragListener, resizeListener);
            }
        }
    }

    public final void removeOverlayView(View view) {
        removeOverlayView(view, false);
    }

    public final void removeOverlayView(View view, boolean isRemovedByUser) {
        OverlayState state = mOverlayStates.remove(view);
        if (state == null) {
            return;
        }

        try {
            mWindowManager.removeView(view);
        } catch (Exception ignored) {
        }

        if (state.onRemoveOverlayListener != null) {
            state.onRemoveOverlayListener.onRemoveOverlay(view, isRemovedByUser);
        }

        if (mOverlayStates.isEmpty()) {
            mRemoveView.setVisibility(View.GONE);
        }
    }

    public final void destroy() {
        for (View view : new HashMap<>(mOverlayStates).keySet()) {
            try {
                mWindowManager.removeView(view);
            } catch (Exception ignored) {
            }
        }

        mOverlayStates.clear();

        try {
            mWindowManager.removeView(mRemoveView);
        } catch (Exception ignored) {
        }
    }

    private View.OnTouchListener newSimpleOnTouchListener(final View overlayView, final OverlayState state) {
        return new View.OnTouchListener() {
            private final int[] overlayViewLocation = {0, 0};
            private final int[] removeViewLocation = {0, 0};
            private final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        state.initialX = state.layoutParams.x;
                        state.initialY = state.layoutParams.y;
                        state.initialTouchX = event.getRawX();
                        state.initialTouchY = event.getRawY();
                        state.isOverRemoveView = false;
                        state.isDragging = false;
                        mRemoveView.setVisibility(View.VISIBLE);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - state.initialTouchX);
                        int deltaY = (int) (event.getRawY() - state.initialTouchY);

                        if (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop) {
                            state.isDragging = true;
                        }

                        if (state.isDragging) {
                            state.layoutParams.x = state.initialX + deltaX;
                            state.layoutParams.y = state.initialY + deltaY;

                            try {
                                mWindowManager.updateViewLayout(overlayView, state.layoutParams);
                            } catch (Exception ignored) {
                            }

                            overlayView.getLocationOnScreen(overlayViewLocation);
                            mRemoveView.getLocationOnScreen(removeViewLocation);
                            state.isOverRemoveView = isOverlayTouchingRemoveView(overlayView);

                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        state.isOverRemoveView = isOverlayTouchingRemoveView(overlayView);
                        //IS_OVER_REMOVEVIEW.IS_OVER = state.isOverRemoveView;
                        if (state.isOverRemoveView) {
                            removeOverlayView(overlayView, true);
                        } else if (!state.isDragging && state.onClickListener != null) {
                            state.onClickListener.onClick(overlayView);
                        }

                        mRemoveView.setVisibility(View.GONE);
                        state.isOverRemoveView = false;
                        state.isDragging = false;
                        //IS_OVER_REMOVEVIEW.IS_OVER = false;
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        mRemoveView.setVisibility(View.GONE);
                        state.isOverRemoveView = false;
                        state.isDragging = false;
                        //IS_OVER_REMOVEVIEW.IS_OVER = false;
                        return true;
                }

                return false;
            }
        };
    }

    private View.OnTouchListener newResizeTouchListener(final View overlayView, final OverlayState state) {
        return new View.OnTouchListener() {
            private final int minWidth = dpToPx(120);
            private final int minHeight = dpToPx(64);
            private int startWidth;
            private int startHeight;
            private float startTouchX;
            private float startTouchY;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startWidth = state.layoutParams.width > 0
                                ? state.layoutParams.width
                                : overlayView.getWidth();
                        startHeight = state.layoutParams.height > 0
                                ? state.layoutParams.height
                                : overlayView.getHeight();

                        startTouchX = event.getRawX();
                        startTouchY = event.getRawY();

                        mRemoveView.setVisibility(View.GONE);
                        state.isOverRemoveView = false;
                        //IS_OVER_REMOVEVIEW.IS_OVER = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - startTouchX);
                        int deltaY = (int) (event.getRawY() - startTouchY);

                        int maxWidth = Math.max(minWidth, DisplayWidth);
                        int maxHeight = Math.max(minHeight, DisplayHeight);

                        state.layoutParams.width = Math.max(
                                minWidth,
                                Math.min(maxWidth, startWidth + deltaX));

                        state.layoutParams.height = Math.max(
                                minHeight,
                                Math.min(maxHeight, startHeight + deltaY));

                        try {
                            mWindowManager.updateViewLayout(overlayView, state.layoutParams);
                        } catch (Exception ignored) {
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }

                return false;
            }
        };
    }

    private int dpToPx(int dp) {
        return (int) (dp * mContext.getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isPointInArea(int x1, int y1, int x2, int y2, int radius) {
        return x1 >= x2 - radius &&
                x1 <= x2 + radius &&
                y1 >= y2 - radius &&
                y1 <= y2 + radius;
    }

    private static WindowManager.LayoutParams newWindowManagerLayoutParams() {
        WindowManager.LayoutParams params;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.setColorMode(ActivityInfo.COLOR_MODE_DEFAULT);
        }

        return params;
    }

    private static WindowManager.LayoutParams newWindowManagerLayoutParamsForRemoveView() {
        WindowManager.LayoutParams params;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
        }

        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        params.y = 56;
        return params;
    }

    public interface OnRemoveOverlayListener {
        void onRemoveOverlay(View v, boolean isRemovedByUser);
    }

    public final void closeOverlayWindow(View view) {
        OverlayState state = mOverlayStates.remove(view);
        if (state == null) {
            return;
        }

        try {
            mWindowManager.removeView(view);
        } catch (Exception ignored) {
        }

        state.isOverRemoveView = false;
        //IS_OVER_REMOVEVIEW.IS_OVER = false;

        if (mOverlayStates.isEmpty()) {
            mRemoveView.setVisibility(View.GONE);
        }
    }

    private boolean isOverlayTouchingRemoveView(View overlayView) {
        if (overlayView == null || mRemoveView == null) {
            return false;
        }

        int[] overlayLocation = new int[2];
        int[] removeLocation = new int[2];

        overlayView.getLocationOnScreen(overlayLocation);
        mRemoveView.getLocationOnScreen(removeLocation);

        int overlayLeft = overlayLocation[0];
        int overlayTop = overlayLocation[1];
        int overlayRight = overlayLeft + overlayView.getWidth();
        int overlayBottom = overlayTop + overlayView.getHeight();

        int removeLeft = removeLocation[0];
        int removeTop = removeLocation[1];
        int removeRight = removeLeft + mRemoveView.getWidth();
        int removeBottom = removeTop + mRemoveView.getHeight();

        return overlayLeft < removeRight
                && overlayRight > removeLeft
                && overlayTop < removeBottom
                && overlayBottom > removeTop;
    }

}
