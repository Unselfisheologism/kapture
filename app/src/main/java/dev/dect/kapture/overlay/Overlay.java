package dev.dect.kapture.overlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Surface;
import android.view.WindowManager;

import dev.dect.kapture.data.KSettings;
import dev.dect.kapture.recorder.ScreenMicRecorder;

@SuppressLint("InflateParams")
public class Overlay {
    private final MenuOverlay MENU_OVERLAY;

    private final CameraOverlay CAMERA_OVERLAY;

    private final TextOverlay TEXT_OVERLAY;

    private final ImageOverlay IMAGE_OVERLAY;

    private ScreenMicRecorder SCREEN_RECORDER;

    public Overlay(Context ctx, KSettings ks) {
        final WindowManager windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);

        this.CAMERA_OVERLAY = new CameraOverlay(ctx, ks, windowManager);

        this.MENU_OVERLAY = new MenuOverlay(ctx, ks, windowManager, CAMERA_OVERLAY);
        this.TEXT_OVERLAY = new TextOverlay(ctx, ks, windowManager);
        this.IMAGE_OVERLAY = new ImageOverlay(ctx, ks, windowManager);
    }

    public void render() {
        MENU_OVERLAY.render();

        CAMERA_OVERLAY.render();

        TEXT_OVERLAY.render();

        IMAGE_OVERLAY.render();
    }

    public void destroy() {
        MENU_OVERLAY.destroy();

        CAMERA_OVERLAY.destroy();

        TEXT_OVERLAY.destroy();

        IMAGE_OVERLAY.destroy();
    }

    public void setMediaRecorderSurface(Surface s) {
        MENU_OVERLAY.setMediaRecorderSurface(s);
    }

    public void setScreenRecorder(ScreenMicRecorder recorder) {
        this.SCREEN_RECORDER = recorder;
        MENU_OVERLAY.setScreenRecorder(recorder);
    }

    public void refreshRecordingState() {
        MENU_OVERLAY.refreshRecordingState();
    }
}
