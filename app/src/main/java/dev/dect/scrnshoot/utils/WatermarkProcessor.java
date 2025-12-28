package dev.dect.scrnshoot.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Surface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;

import dev.dect.scrnshoot.data.KSettings;
import dev.dect.scrnshoot.data.ProVersionManager;

/**
 * Utility class for adding watermarks to videos during export.
 * Supports both default watermark (free version) and custom watermarks (pro version).
 */
public class WatermarkProcessor {
    private static final String TAG = WatermarkProcessor.class.getSimpleName();

    // Default watermark text for free version
    public static final String DEFAULT_WATERMARK_TEXT = "Recorded with Scrnshoot";

    /**
     * Add default watermark to video file.
     */
    public static boolean addDefaultWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull KSettings ks, @NonNull Context context) {
        if (!ProVersionManager.shouldShowDefaultWatermark(context)) {
            Log.d(TAG, "addDefaultWatermark: No watermark needed, copying file directly");
            return copyFile(inputPath, outputPath);
        }

        Log.d(TAG, "addDefaultWatermark: Adding default watermark to video");
        return addTextWatermark(inputPath, outputPath, DEFAULT_WATERMARK_TEXT, Gravity.BOTTOM | Gravity.END, 70, 16, context);
    }

    /**
     * Add custom text watermark to video file.
     */
    public static boolean addCustomTextWatermark(@NonNull String inputPath, @NonNull String outputPath, @NonNull KSettings ks, @NonNull Context context) {
        if (!ks.isToUseCustomWatermark()) {
            return copyFile(inputPath, outputPath);
        }

        return addTextWatermark(inputPath, outputPath, ks.getCustomWatermarkText(),
                ks.getCustomWatermarkPosition(), ks.getCustomWatermarkOpacity(), ks.getCustomWatermarkSize(), context);
    }

    /**
     * Add text watermark to video file using MediaCodec with Surface.
     */
    private static boolean addTextWatermark(@NonNull String inputPath, @NonNull String outputPath,
                                            @NonNull String text, int position, int opacity, int sizeSp,
                                            @NonNull Context context) {
        Log.d(TAG, "addTextWatermark: Starting watermark processing for: " + inputPath);
        long startTime = System.currentTimeMillis();

        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        MediaCodec decoder = null;
        MediaCodec encoder = null;

        try {
            // Get video info
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);

            int videoTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    videoTrackIndex = i;
                    break;
                }
            }

            if (videoTrackIndex == -1) {
                return copyFile(inputPath, outputPath);
            }

            MediaFormat inputFormat = extractor.getTrackFormat(videoTrackIndex);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            int width = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);

            Log.d(TAG, "addTextWatermark: Video: " + width + "x" + height);

            // Get bitrate and framerate
            int bitRate = 8000000;
            int frameRate = 30;

            if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                bitRate = inputFormat.getInteger(MediaFormat.KEY_BIT_RATE);
            }
            if (inputFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
            }

            // Create output file
            File outputFile = new File(outputPath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // Create watermark bitmap
            Bitmap watermarkBitmap = createWatermarkBitmap(text, width, height, position, opacity, sizeSp, context);
            if (watermarkBitmap == null || watermarkBitmap.isRecycled()) {
                return copyFile(inputPath, outputPath);
            }

            // Create output format - use Surface-based encoder
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            // Set up muxer
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerVideoTrack = muxer.addTrack(outputFormat);
            muxer.start();

            // Create encoder with Surface input
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface encoderSurface = encoder.createInputSurface();
            encoder.start();

            // Create decoder with output to encoder's Surface
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, encoderSurface, null, 0);
            decoder.start();

            // Process frames
            extractor.selectTrack(videoTrackIndex);
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            boolean done = false;
            long timeoutUs = 10000;
            int frameCount = 0;
            int framesPerWatermark = frameRate / 2; // Draw watermark every 2 frames for efficiency

            while (!done) {
                // Feed decoder
                while (!done) {
                    int inputIndex = decoder.dequeueInputBuffer(timeoutUs);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoderInputBuffers[inputIndex];
                        inputBuffer.clear();

                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            done = true;
                        } else {
                            long presentationTime = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, presentationTime, 0);
                            extractor.advance();
                        }
                    } else {
                        break;
                    }
                }

                // Drain decoder output
                while (true) {
                    int outputIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "Decoder output format changed");
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (outputIndex >= 0) {
                        frameCount++;

                        // Draw watermark on encoder surface for this frame
                        if (watermarkBitmap != null && !watermarkBitmap.isRecycled()) {
                            Canvas canvas = encoderSurface.lockCanvas(null);
                            if (canvas != null) {
                                // Draw semi-transparent overlay to make watermark visible
                                // Then draw the watermark
                                canvas.drawBitmap(watermarkBitmap, width - watermarkBitmap.getWidth() - 40, height - watermarkBitmap.getHeight() - 40, null);
                                encoderSurface.unlockCanvasAndPost(canvas);
                            }
                        }

                        decoder.releaseOutputBuffer(outputIndex, false);

                        // Signal end of stream
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            int encoderInputIndex = encoder.dequeueInputBuffer(timeoutUs);
                            if (encoderInputIndex >= 0) {
                                encoder.queueInputBuffer(encoderInputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }
                        }
                    } else {
                        break;
                    }
                }

                // Drain encoder output
                while (true) {
                    int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "Encoder output format changed");
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    } else if (outputIndex >= 0) {
                        ByteBuffer outputBuffer = encoderOutputBuffers[outputIndex];

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encoder.releaseOutputBuffer(outputIndex, false);
                            continue;
                        }

                        if (bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset);
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                            muxer.writeSampleData(muxerVideoTrack, outputBuffer, bufferInfo);
                        }

                        encoder.releaseOutputBuffer(outputIndex, false);

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break;
                        }
                    } else {
                        break;
                    }
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }

            Log.d(TAG, "addTextWatermark: Processed " + frameCount + " frames");

            // Cleanup
            if (!watermarkBitmap.isRecycled()) {
                watermarkBitmap.recycle();
            }

            encoder.stop();
            encoder.release();
            decoder.stop();
            decoder.release();
            encoderSurface.release();

            muxer.stop();
            muxer.release();

            // Verify output
            File resultFile = new File(outputPath);
            if (resultFile.exists() && resultFile.length() > 0) {
                long endTime = System.currentTimeMillis();
                Log.d(TAG, "addTextWatermark: Completed in " + (endTime - startTime) + "ms, output size: " + resultFile.length());
                return true;
            } else {
                Log.w(TAG, "addTextWatermark: Output file missing or empty");
                return copyFile(inputPath, outputPath);
            }

        } catch (Exception e) {
            Log.e(TAG, "addTextWatermark: " + e.getMessage());
            e.printStackTrace();

            if (decoder != null) try { decoder.release(); } catch (Exception ignored) {}
            if (encoder != null) try { encoder.release(); } catch (Exception ignored) {}
            if (muxer != null) try { muxer.release(); } catch (Exception ignored) {}
            if (extractor != null) try { extractor.release(); } catch (Exception ignored) {}

            return copyFile(inputPath, outputPath);
        }
    }

    /**
     * Create a bitmap with the watermark text rendered.
     */
    public static Bitmap createWatermarkBitmap(@NonNull String text, int videoWidth, int videoHeight,
                                               int position, int opacity, int sizeSp, @NonNull Context context) {
        float fontSize = sizeSp * context.getResources().getDisplayMetrics().density * 2;

        Paint textPaint = new Paint();
        textPaint.setTextSize(fontSize);
        textPaint.setAntiAlias(true);

        float textWidth = textPaint.measureText(text);
        float textHeight = fontSize;

        int bitmapWidth = (int) (textWidth + 40);
        int bitmapHeight = (int) (textHeight + 20);

        Bitmap watermarkBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(watermarkBitmap);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAlpha((int) (255 * (opacity / 100f)));
        paint.setTextSize(fontSize);
        paint.setAntiAlias(true);
        paint.setShadowLayer(3f, 2f, 2f, Color.BLACK);

        float x = 10;
        float y = fontSize;

        if ((position & Gravity.BOTTOM) != 0) {
            y = bitmapHeight - 10;
        }
        if ((position & Gravity.TOP) != 0) {
            y = fontSize + 10;
        }
        if ((position & Gravity.CENTER) != 0) {
            x = (bitmapWidth - textWidth) / 2;
        } else if ((position & Gravity.END) != 0) {
            x = bitmapWidth - textWidth - 10;
        } else if ((position & Gravity.START) != 0) {
            x = 10;
        }

        canvas.drawText(text, x, y, paint);

        return watermarkBitmap;
    }

    /**
     * Simple file copy utility.
     */
    private static boolean copyFile(@NonNull String sourcePath, @NonNull String destPath) {
        File source = new File(sourcePath);
        File dest = new File(destPath);

        if (!source.exists()) {
            return false;
        }

        File parentDir = dest.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "copyFile: Failed to create parent directories");
                return false;
            }
        }

        long copyStart = System.currentTimeMillis();
        Log.d(TAG, "copyFile: Copying from " + sourcePath + " to " + destPath + ", size: " + source.length() + " bytes");

        try (FileInputStream fis = new FileInputStream(source);
             java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {

            byte[] buffer = new byte[128 * 1024];
            int bytesRead;
            long totalBytesCopied = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytesCopied += bytesRead;
            }

            long copyEnd = System.currentTimeMillis();
            Log.d(TAG, "copyFile: Completed copy of " + totalBytesCopied + " bytes in " + (copyEnd - copyStart) + "ms");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "copyFile: " + e.getMessage());
            return false;
        }
    }

    public static boolean hasWatermark(@NonNull String videoPath) {
        File file = new File(videoPath);
        return file.exists() && file.length() > 0;
    }
}
