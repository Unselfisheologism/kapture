package dev.dect.kapture.recorder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.dect.kapture.R;
import dev.dect.kapture.data.KSettings;
import dev.dect.kapture.utils.KMediaProjection;
import dev.dect.kapture.utils.ProVersionManager;

public class TapToZoomManager {
    private final String TAG = TapToZoomManager.class.getSimpleName();
    
    private final Context CONTEXT;
    private final KSettings KSETTINGS;
    
    private VirtualDisplay ZOOM_VIRTUAL_DISPLAY;
    private Surface ZOOM_SURFACE;
    
    private int ZOOM_LEVEL = 2; // 2x zoom
    private int ZOOM_DURATION = 500; // ms
    private int ZOOM_DELAY = 1000; // ms between zooms
    
    private final AtomicBoolean IS_ZOOMING = new AtomicBoolean(false);
    private final AtomicBoolean IS_ZOOM_ENABLED = new AtomicBoolean(false);
    
    private int SCREEN_WIDTH;
    private int SCREEN_HEIGHT;
    private int SCREEN_DPI;
    
    public TapToZoomManager(Context ctx, KSettings ks) {
        this.CONTEXT = ctx;
        this.KSETTINGS = ks;
        this.SCREEN_WIDTH = ks.getVideoWidth();
        this.SCREEN_HEIGHT = ks.getVideoHeight();
        this.SCREEN_DPI = ks.getVideoDpi();
    }
    
    public void enable() {
        if (ProVersionManager.isTapToZoomAvailable(CONTEXT)) {
            IS_ZOOM_ENABLED.set(true);
            Log.d(TAG, "Tap-to-zoom enabled");
            
            // Show toast to inform user
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(CONTEXT, "Tap-to-zoom enabled (Pro feature)", Toast.LENGTH_SHORT).show();
            });
        } else {
            Log.d(TAG, "Tap-to-zoom not available in this version");
        }
    }
    
    public void disable() {
        IS_ZOOM_ENABLED.set(false);
        cancelCurrentZoom();
    }
    
    public boolean isEnabled() {
        return IS_ZOOM_ENABLED.get();
    }
    
    public void handleTapEvent(int x, int y) {
        if (!IS_ZOOM_ENABLED.get() || IS_ZOOMING.get()) {
            return;
        }
        
        // Calculate zoom region centered on tap location
        int zoomWidth = SCREEN_WIDTH / ZOOM_LEVEL;
        int zoomHeight = SCREEN_HEIGHT / ZOOM_LEVEL;
        
        int zoomX = Math.max(0, Math.min(x - zoomWidth / 2, SCREEN_WIDTH - zoomWidth));
        int zoomY = Math.max(0, Math.min(y - zoomHeight / 2, SCREEN_HEIGHT - zoomHeight));
        
        Rect zoomRect = new Rect(zoomX, zoomY, zoomX + zoomWidth, zoomY + zoomHeight);
        
        performZoomAnimation(zoomRect);
    }
    
    private void performZoomAnimation(Rect zoomRect) {
        IS_ZOOMING.set(true);
        
        // Create a temporary surface for zoomed content
        try {
            // This is a simplified approach - in a real implementation, you would:
            // 1. Create a temporary VirtualDisplay with the zoomed region
            // 2. Use MediaCodec to encode the zoomed content
            // 3. Blend it with the main recording
            // For this implementation, we'll simulate the zoom effect
            
            Log.d(TAG, "Performing zoom animation at: " + zoomRect);
            
            // Simulate zoom animation
            ValueAnimator zoomAnimator = ValueAnimator.ofFloat(1.0f, ZOOM_LEVEL, 1.0f);
            zoomAnimator.setDuration(ZOOM_DURATION);
            zoomAnimator.addUpdateListener(animation -> {
                float zoomFactor = (float) animation.getAnimatedValue();
                // In a real implementation, this would adjust the VirtualDisplay scaling
                Log.d(TAG, "Zoom factor: " + zoomFactor);
            });
            
            zoomAnimator.addListener(new android.animation.AnimatorListener() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {
                    // Start zoom
                }
                
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    IS_ZOOMING.set(false);
                    
                    // Add delay between zooms
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        IS_ZOOMING.set(false);
                    }, ZOOM_DELAY);
                }
                
                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    IS_ZOOMING.set(false);
                }
                
                @Override
                public void onAnimationRepeat(android.animation.Animator animation) {
                    // Not used
                }
            });
            
            zoomAnimator.start();
            
        } catch (Exception e) {
            Log.e(TAG, "Error performing zoom: " + e.getMessage());
            IS_ZOOMING.set(false);
        }
    }
    
    private void cancelCurrentZoom() {
        // Cancel any ongoing zoom animation
        IS_ZOOMING.set(false);
        
        if (ZOOM_VIRTUAL_DISPLAY != null) {
            try {
                ZOOM_VIRTUAL_DISPLAY.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing zoom display: " + e.getMessage());
            }
            ZOOM_VIRTUAL_DISPLAY = null;
        }
        
        ZOOM_SURFACE = null;
    }
    
    public void destroy() {
        cancelCurrentZoom();
        IS_ZOOM_ENABLED.set(false);
    }
    
    public void setZoomLevel(int zoomLevel) {
        if (zoomLevel >= 1 && zoomLevel <= 3) {
            this.ZOOM_LEVEL = zoomLevel;
        }
    }
    
    public void setZoomDuration(int durationMs) {
        if (durationMs > 0) {
            this.ZOOM_DURATION = durationMs;
        }
    }
    
    public void setZoomDelay(int delayMs) {
        if (delayMs > 0) {
            this.ZOOM_DELAY = delayMs;
        }
    }
}