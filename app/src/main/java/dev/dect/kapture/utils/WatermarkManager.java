package dev.dect.kapture.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;

import androidx.media3.common.MediaItem;
import androidx.media3.effect.BitmapOverlay;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.TextOverlay;

import java.io.File;

public class WatermarkManager {
    
    public static final String DEFAULT_WATERMARK_TEXT = "Recorded with Kapture";
    public static final int DEFAULT_WATERMARK_SIZE = 32;
    public static final int DEFAULT_WATERMARK_COLOR = Color.WHITE;
    public static final int DEFAULT_WATERMARK_OPACITY = 180; // 70% opacity
    public static final int DEFAULT_WATERMARK_PADDING = 20;
    
    public static Bitmap createDefaultWatermarkBitmap(Context context) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(DEFAULT_WATERMARK_COLOR);
        paint.setAlpha(DEFAULT_WATERMARK_OPACITY);
        paint.setTextSize(DEFAULT_WATERMARK_SIZE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        
        // Measure text width
        float textWidth = paint.measureText(DEFAULT_WATERMARK_TEXT);
        
        // Create bitmap with padding
        int width = (int) (textWidth + DEFAULT_WATERMARK_PADDING * 2);
        int height = (int) (DEFAULT_WATERMARK_SIZE + DEFAULT_WATERMARK_PADDING * 2);
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw text
        canvas.drawText(DEFAULT_WATERMARK_TEXT, DEFAULT_WATERMARK_PADDING, DEFAULT_WATERMARK_SIZE + DEFAULT_WATERMARK_PADDING, paint);
        
        return bitmap;
    }
    
    public static Bitmap createCustomWatermarkBitmap(Context context, String text, int size, int color, int opacity, String fontPath) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setAlpha(opacity);
        paint.setTextSize(size);
        
        // Load custom font if available
        if (fontPath != null && !fontPath.isEmpty()) {
            try {
                Typeface typeface = Typeface.createFromFile(fontPath);
                paint.setTypeface(typeface);
            } catch (Exception e) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
            }
        } else {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
        }
        
        paint.setTextAlign(Paint.Align.LEFT);
        
        // Measure text width
        float textWidth = paint.measureText(text);
        
        // Create bitmap with padding
        int width = (int) (textWidth + DEFAULT_WATERMARK_PADDING * 2);
        int height = (int) (size + DEFAULT_WATERMARK_PADDING * 2);
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Draw text
        canvas.drawText(text, DEFAULT_WATERMARK_PADDING, size + DEFAULT_WATERMARK_PADDING, paint);
        
        return bitmap;
    }
    
    public static BitmapOverlay createDefaultWatermarkOverlay(Context context) {
        Bitmap watermarkBitmap = createDefaultWatermarkBitmap(context);
        
        OverlaySettings overlaySettings = new OverlaySettings.Builder()
            .setPosition(OverlaySettings.POSITION_BOTTOM_RIGHT)
            .setPadding(DEFAULT_WATERMARK_PADDING)
            .build();
        
        return new BitmapOverlay(watermarkBitmap, overlaySettings);
    }
    
    public static BitmapOverlay createCustomWatermarkOverlay(Context context, String text, int size, int color, int opacity, String fontPath, int position) {
        Bitmap watermarkBitmap = createCustomWatermarkBitmap(context, text, size, color, opacity, fontPath);
        
        OverlaySettings overlaySettings = new OverlaySettings.Builder()
            .setPosition(position)
            .setPadding(DEFAULT_WATERMARK_PADDING)
            .build();
        
        return new BitmapOverlay(watermarkBitmap, overlaySettings);
    }
}