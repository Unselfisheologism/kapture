package dev.dect.kapture.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.FrameLayout;

import dev.dect.kapture.data.KSettings;
import dev.dect.kapture.data.ProVersionManager;

/**
 * Tap-to-Zoom overlay for Pro version.
 * Detects touch events during recording and applies smooth animated zoom
 * centered on the tap location (similar to desktop cursor zoom).
 */
@SuppressLint("ViewConstructor")
public class TapToZoomOverlay extends FrameLayout {
    private static final float ZOOM_ANIMATION_DURATION = 300f; // ms

    private final Context CONTEXT;
    private final KSettings KSETTINGS;
    private final WindowManager WINDOW_MANAGER;

    private Surface MEDIA_RECORDER_SURFACE;
    private float CURRENT_ZOOM = 1.0f;
    private float TARGET_ZOOM = 1.0f;

    private final float SCREEN_CENTER_X;
    private float ZOOM_CENTER_X;
    private float ZOOM_CENTER_Y;

    private final Matrix ZOOM_MATRIX;
    private final GestureDetector GESTURE_DETECTOR;

    private final Runnable ZOOM_ANIMATIONRunnable;
    private long ANIMATION_START_TIME = 0;
    private float ZOOM_START_VALUE = 1.0f;

    private final Handler MAIN_HANDLER;

    private boolean IS_ENABLED = false;

    public TapToZoomOverlay(Context ctx, KSettings ks, WindowManager wm) {
        super(ctx);
        this.CONTEXT = ctx;
        this.KSETTINGS = ks;
        this.WINDOW_MANAGER = wm;

        this.ZOOM_MATRIX = new Matrix();
        this.MAIN_HANDLER = new Handler(Looper.getMainLooper());

        // Get screen dimensions for zoom center calculations
        int[] screenSize = getScreenSize();
        this.SCREEN_CENTER_X = screenSize[0] / 2f;

        this.ZOOM_CENTER_X = SCREEN_CENTER_X;
        this.ZOOM_CENTER_Y = screenSize[1] / 2f;

        this.GESTURE_DETECTOR = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (IS_ENABLED && ProVersionManager.isProVersion(CONTEXT)) {
                    onTap(e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (IS_ENABLED && ProVersionManager.isProVersion(CONTEXT)) {
                    // Double tap resets zoom
                    resetZoom();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (IS_ENABLED && ProVersionManager.isProVersion(CONTEXT)) {
                    // Scroll to pan the zoomed view
                    panZoom(distanceX, distanceY);
                }
                return true;
            }
        });

        this.ZOOM_ANIMATIONRunnable = this::animateZoom;
    }

    private int[] getScreenSize() {
        int[] size = new int[2];
        WINDOW_MANAGER.getDefaultDisplay().getRealSize(new android.graphics.Point());
        android.graphics.Point point = new android.graphics.Point();
        WINDOW_MANAGER.getDefaultDisplay().getRealSize(point);
        size[0] = point.x;
        size[1] = point.y;
        return size;
    }

    /**
     * Enable or disable tap-to-zoom functionality.
     */
    public void setEnabled(boolean enabled) {
        this.IS_ENABLED = enabled && KSETTINGS.isToUseTapToZoom();
        if (!IS_ENABLED) {
            resetZoom();
        }
    }

    /**
     * Handle tap event - apply zoom centered at tap location.
     */
    private void onTap(float tapX, float tapY) {
        ZOOM_CENTER_X = tapX;
        ZOOM_CENTER_Y = tapY;

        // Toggle between normal and zoomed state
        if (CURRENT_ZOOM > 1.0f) {
            resetZoom();
        } else {
            zoomIn();
        }
    }

    /**
     * Zoom in to the target zoom factor.
     */
    private void zoomIn() {
        TARGET_ZOOM = KSETTINGS.getTapToZoomFactor();
        startZoomAnimation();
    }

    /**
     * Reset zoom to normal.
     */
    public void resetZoom() {
        TARGET_ZOOM = 1.0f;
        startZoomAnimation();
    }

    /**
     * Start smooth zoom animation.
     */
    private void startZoomAnimation() {
        ANIMATION_START_TIME = System.currentTimeMillis();
        ZOOM_START_VALUE = CURRENT_ZOOM;
        MAIN_HANDLER.removeCallbacks(ZOOM_ANIMATIONRunnable);
        MAIN_HANDLER.post(ZOOM_ANIMATIONRunnable);
    }

    /**
     * Animation loop for smooth zoom transition.
     */
    private void animateZoom() {
        long elapsed = System.currentTimeMillis() - ANIMATION_START_TIME;
        float progress = Math.min(elapsed / ZOOM_ANIMATION_DURATION, 1.0f);

        // Ease out cubic for smooth animation
        float easeProgress = (float) (1 - Math.pow(1 - progress, 3));

        CURRENT_ZOOM = ZOOM_START_VALUE + (TARGET_ZOOM - ZOOM_START_VALUE) * easeProgress;

        updateSurface();

        if (progress < 1.0f) {
            MAIN_HANDLER.post(ZOOM_ANIMATIONRunnable);
        }
    }

    /**
     * Pan the zoomed view based on scroll distance.
     */
    private void panZoom(float distanceX, float distanceY) {
        // Adjust pan based on current zoom level
        float panFactor = 1.0f / CURRENT_ZOOM;

        // Clamp pan to stay within screen bounds
        float halfScreenWidth = SCREEN_CENTER_X / CURRENT_ZOOM;
        float halfScreenHeight = getScreenSize()[1] / 2f / CURRENT_ZOOM;

        ZOOM_CENTER_X = Math.max(halfScreenWidth, Math.min(getScreenSize()[0] - halfScreenWidth, ZOOM_CENTER_X - distanceX * panFactor));
        ZOOM_CENTER_Y = Math.max(halfScreenHeight, Math.min(getScreenSize()[1] - halfScreenHeight, ZOOM_CENTER_Y - distanceY * panFactor));

        updateSurface();
    }

    /**
     * Update the MediaRecorder surface with zoom transformation.
     */
    private void updateSurface() {
        if (MEDIA_RECORDER_SURFACE == null || !MEDIA_RECORDER_SURFACE.isValid()) {
            return;
        }

        // Apply zoom transformation
        ZOOM_MATRIX.reset();
        ZOOM_MATRIX.postScale(CURRENT_ZOOM, CURRENT_ZOOM, ZOOM_CENTER_X, ZOOM_CENTER_Y);

        try {
            MEDIA_RECORDER_SURFACE.setMatrix(ZOOM_MATRIX);
        } catch (Exception e) {
            // Surface may have been released
        }
    }

    /**
     * Set the MediaRecorder surface for zoom transformations.
     */
    public void setMediaRecorderSurface(Surface surface) {
        this.MEDIA_RECORDER_SURFACE = surface;
        if (IS_ENABLED && CURRENT_ZOOM > 1.0f) {
            updateSurface();
        }
    }

    /**
     * Handle touch events for tap-to-zoom.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (IS_ENABLED) {
            GESTURE_DETECTOR.onTouchEvent(event);
            return true;
        }
        return false;
    }

    /**
     * Clean up resources.
     */
    public void destroy() {
        MAIN_HANDLER.removeCallbacks(ZOOM_ANIMATIONRunnable);
    }

    /**
     * Get current zoom level.
     */
    public float getCurrentZoom() {
        return CURRENT_ZOOM;
    }

    /**
     * Check if zoom is currently active.
     */
    public boolean isZoomed() {
        return CURRENT_ZOOM > 1.0f;
    }
}
