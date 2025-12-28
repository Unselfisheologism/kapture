package dev.dect.scrnshoot.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dect.scrnshoot.R;
import dev.dect.scrnshoot.data.KSettings;
import dev.dect.scrnshoot.data.ProVersionManager;

/**
 * Applies default (Free) and custom (Pro) watermarks to exported videos.
 */
public class WatermarkProcessor {
    private static final String TAG = WatermarkProcessor.class.getSimpleName();

    public static final String DEFAULT_WATERMARK_TEXT = "Recorded with Scrnshoot";

    private static final long EXPORT_TIMEOUT_MS = 10 * 60 * 1000; // 10 min

    /**
     * Applies the correct watermark for the current build/settings.
     *
     * - Pro build or IAP unlocked: no default watermark. If custom watermark is enabled, applies it.
     * - Free build (not unlocked): applies default watermark.
     */
    public static boolean applyWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull KSettings ks, @NonNull Context context) {
        if (ks.isToUseCustomWatermark()) {
            final String imagePath = ks.getCustomWatermarkImagePath();
            if (imagePath != null && new File(imagePath).exists()) {
                return addImageWatermark(inputPath, outputPath, imagePath, ks.getCustomWatermarkPosition(), ks.getCustomWatermarkOpacity(), context);
            }

            return addTextWatermark(inputPath, outputPath, ks.getCustomWatermarkText(), ks.getCustomWatermarkPosition(), ks.getCustomWatermarkOpacity(), ks.getCustomWatermarkSize(), context);
        }

        return addDefaultWatermark(inputPath, outputPath, ks, context);
    }

    /**
     * Add default watermark to video file (Free build only).
     */
    public static boolean addDefaultWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull KSettings ks, @NonNull Context context) {
        if (!ProVersionManager.shouldShowDefaultWatermark(context)) {
            return copyFile(inputPath, outputPath);
        }

        String text;
        try {
            text = context.getString(R.string.default_watermark_text);
        } catch (Exception ignore) {
            text = DEFAULT_WATERMARK_TEXT;
        }

        return addTextWatermark(inputPath, outputPath, text, Gravity.BOTTOM | Gravity.END, 80, 16, context);
    }

    /**
     * Add custom text watermark to video file (Pro only).
     */
    public static boolean addCustomTextWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull KSettings ks, @NonNull Context context) {
        if (!ks.isToUseCustomWatermark()) {
            return copyFile(inputPath, outputPath);
        }

        return addTextWatermark(inputPath, outputPath, ks.getCustomWatermarkText(), ks.getCustomWatermarkPosition(), ks.getCustomWatermarkOpacity(), ks.getCustomWatermarkSize(), context);
    }

    @OptIn(markerClass = UnstableApi.class)
    private static boolean addTextWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull String text, int position, int opacity, int sizeSp, @NonNull Context context) {
        if (text.trim().isEmpty()) {
            return copyFile(inputPath, outputPath);
        }

        final VideoSize size = getVideoSize(inputPath);
        if (size.width <= 0 || size.height <= 0) {
            return copyFile(inputPath, outputPath);
        }

        final Bitmap overlayBitmap = createFullFrameTextOverlayBitmap(text, size.width, size.height, position, opacity, sizeSp, context);
        return exportWithOverlay(inputPath, outputPath, overlayBitmap, context);
    }

    @OptIn(markerClass = UnstableApi.class)
    private static boolean addImageWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull String imagePath, int position, int opacity, @NonNull Context context) {
        final VideoSize size = getVideoSize(inputPath);
        if (size.width <= 0 || size.height <= 0) {
            return copyFile(inputPath, outputPath);
        }

        final Bitmap image = BitmapFactory.decodeFile(imagePath);
        if (image == null) {
            return copyFile(inputPath, outputPath);
        }

        final Bitmap overlayBitmap = createFullFrameImageOverlayBitmap(image, size.width, size.height, position, opacity);
        return exportWithOverlay(inputPath, outputPath, overlayBitmap, context);
    }

    @OptIn(markerClass = UnstableApi.class)
    private static boolean exportWithOverlay(@NonNull String inputPath, @NonNull String outputPath, @NonNull Bitmap overlayBitmap, @NonNull Context context) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            if (new File(outputPath).exists()) {
                // Transformer can fail if the file already exists on some devices.
                // Ensure we always start fresh.
                //noinspection ResultOfMethodCallIgnored
                new File(outputPath).delete();
            }

            final Transformer transformer = new Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build();

            transformer.addListener(new Transformer.Listener() {
                @Override
                public void onCompleted(Composition composition, ExportResult exportResult) {
                    success.set(true);
                    latch.countDown();
                }

                @Override
                public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                    Log.e(TAG, "exportWithOverlay failed: " + exportException.getMessage());
                    latch.countDown();
                }
            });

            final BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
            final OverlayEffect overlayEffect = new OverlayEffect(ImmutableList.of(bitmapOverlay));

            @SuppressWarnings("rawtypes")
            final Effects effects = new Effects(new ArrayList(), ImmutableList.of(overlayEffect));

            final EditedMediaItem editedVideo = new EditedMediaItem.Builder(MediaItem.fromUri(inputPath))
                .setEffects(effects)
                .build();

            final Composition composition = new Composition.Builder(new EditedMediaItemSequence(editedVideo)).build();

            transformer.start(composition, outputPath);

            // Wait for export.
            //noinspection ResultOfMethodCallIgnored
            latch.await(EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            if (!success.get()) {
                return copyFile(inputPath, outputPath);
            }

            return new File(outputPath).exists() && new File(outputPath).length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "exportWithOverlay exception: " + e.getMessage());
            return copyFile(inputPath, outputPath);
        } finally {
            try {
                if (!overlayBitmap.isRecycled()) {
                    overlayBitmap.recycle();
                }
            } catch (Exception ignore) {}
        }
    }

    private static Bitmap createFullFrameTextOverlayBitmap(@NonNull String text, int videoWidth, int videoHeight, int position, int opacity, int sizeSp, @NonNull Context context) {
        final Bitmap bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        final float textSizePx = sizeSp * context.getResources().getDisplayMetrics().scaledDensity;

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTextSize(textSizePx);
        paint.setShadowLayer(3f, 2f, 2f, Color.BLACK);
        paint.setAlpha((int) (255 * (Math.max(0, Math.min(100, opacity)) / 100f)));

        final Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        final float margin = Math.max(16f, videoWidth * 0.02f);

        float x;
        if ((position & Gravity.END) != 0) {
            x = videoWidth - bounds.width() - margin;
        } else if ((position & Gravity.CENTER_HORIZONTAL) != 0 || (position & Gravity.CENTER) != 0) {
            x = (videoWidth - bounds.width()) / 2f;
        } else {
            x = margin;
        }

        float y;
        if ((position & Gravity.BOTTOM) != 0) {
            y = videoHeight - margin;
        } else if ((position & Gravity.CENTER_VERTICAL) != 0 || (position & Gravity.CENTER) != 0) {
            y = (videoHeight + bounds.height()) / 2f;
        } else {
            y = bounds.height() + margin;
        }

        canvas.drawText(text, x, y, paint);

        return bitmap;
    }

    private static Bitmap createFullFrameImageOverlayBitmap(@NonNull Bitmap image, int videoWidth, int videoHeight, int position, int opacity) {
        final Bitmap bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        final float margin = Math.max(16f, videoWidth * 0.02f);

        final float maxWidth = videoWidth * 0.25f;
        final float maxHeight = videoHeight * 0.25f;
        final float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());

        final int drawW = Math.max(1, (int) (image.getWidth() * scale));
        final int drawH = Math.max(1, (int) (image.getHeight() * scale));

        float left;
        if ((position & Gravity.END) != 0) {
            left = videoWidth - drawW - margin;
        } else if ((position & Gravity.CENTER_HORIZONTAL) != 0 || (position & Gravity.CENTER) != 0) {
            left = (videoWidth - drawW) / 2f;
        } else {
            left = margin;
        }

        float top;
        if ((position & Gravity.BOTTOM) != 0) {
            top = videoHeight - drawH - margin;
        } else if ((position & Gravity.CENTER_VERTICAL) != 0 || (position & Gravity.CENTER) != 0) {
            top = (videoHeight - drawH) / 2f;
        } else {
            top = margin;
        }

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha((int) (255 * (Math.max(0, Math.min(100, opacity)) / 100f)));

        final Rect dst = new Rect((int) left, (int) top, (int) left + drawW, (int) top + drawH);
        canvas.drawBitmap(image, null, dst, paint);

        return bitmap;
    }

    private static boolean copyFile(@NonNull String sourcePath, @NonNull String destPath) {
        try {
            final File source = new File(sourcePath);
            final File dest = new File(destPath);

            if (!source.exists()) {
                return false;
            }

            if (source.getAbsolutePath().equals(dest.getAbsolutePath())) {
                return true;
            }

            KFile.copyFile(source, dest);
            return dest.exists() && dest.length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "copyFile: " + e.getMessage());
            return false;
        }
    }

    public static boolean hasWatermark(@NonNull String videoPath) {
        File file = new File(videoPath);
        return file.exists() && file.length() > 0;
    }

    private static VideoSize getVideoSize(@NonNull String inputPath) {
        final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(inputPath);

            final String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            final String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            int width = widthStr != null ? Integer.parseInt(widthStr) : -1;
            int height = heightStr != null ? Integer.parseInt(heightStr) : -1;

            if (width > 0 && height > 0) {
                return new VideoSize(width, height);
            }
        } catch (Exception e) {
            Log.e(TAG, "getVideoSize: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {}
        }

        return new VideoSize(-1, -1);
    }

    private static class VideoSize {
        final int width;
        final int height;

        VideoSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
