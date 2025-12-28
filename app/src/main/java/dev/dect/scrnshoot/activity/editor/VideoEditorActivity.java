package dev.dect.scrnshoot.activity.editor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.collect.ImmutableList;

import dev.dect.scrnshoot.R;
import dev.dect.scrnshoot.data.KSettings;
import dev.dect.scrnshoot.data.KSharedPreferences;
import dev.dect.scrnshoot.data.ProVersionManager;
import dev.dect.scrnshoot.model.editor.OverlayItem;
import dev.dect.scrnshoot.model.editor.OverlayType;
import dev.dect.scrnshoot.popup.DialogPopup;
import dev.dect.scrnshoot.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.dect.scrnshoot.utils.KFile;

/**
 * Simple video editor with overlay support and timeline editing.
 * Allows adding text, images, and watermarks to videos with per-overlay timing.
 */
@SuppressLint("SetTextI18n")
public class VideoEditorActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "video_path";

    private static final int COLOR_DEFAULT = Color.WHITE;
    private static final int SIZE_DEFAULT = 24;
    private static final int POSITION_DEFAULT = android.view.Gravity.END | android.view.Gravity.BOTTOM;
    private static final int OPACITY_DEFAULT = 80;

    private static final long EXPORT_TIMEOUT_MS = 10 * 60 * 1000; // 10 min

    private VideoView VIDEO_VIEW;
    private ViewGroup OVERLAY_CONTAINER;
    private SeekBar SEEK_BAR;
    private TextView TEXT_CURRENT_TIME, TEXT_TOTAL_TIME;
    private LinearLayout TIMELINE_CONTAINER;
    private RecyclerView OVERLAYS_LIST;
    private LinearLayout CONTROLS_CONTAINER;

    private String VIDEO_PATH;
    private long VIDEO_DURATION = 0;
    private long VIDEO_POSITION = 0;

    private final Handler HANDLER = new Handler(Looper.getMainLooper());
    private boolean IS_SEEKING = false;
    private boolean IS_PLAYING = false;

    private OverlaysAdapter OVERLAYS_ADAPTER;
    private final List<OverlayItem> OVERLAYS = new ArrayList<>();

    private KSettings KSETTINGS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if pro version
        if (!ProVersionManager.isProVersion(this)) {
            Toast.makeText(this, R.string.pro_feature_custom_watermark, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_video_editor);

        initVariables();

        initListeners();

        init();
    }

    private void initVariables() {
        VIDEO_PATH = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if (VIDEO_PATH == null || !new File(VIDEO_PATH).exists()) {
            Toast.makeText(this, R.string.toast_error_file_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        KSETTINGS = new KSettings(this);
    }

    private void initListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPlayPause).setOnClickListener(v -> togglePlayPause());

        findViewById(R.id.btnAddOverlay).setOnClickListener(v -> showAddOverlayDialog());

        findViewById(R.id.btnExport).setOnClickListener(v -> exportVideo());

        SEEK_BAR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    VIDEO_POSITION = (long) ((float) progress / 100f * VIDEO_DURATION);
                    updateTimeDisplay();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                IS_SEEKING = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                IS_SEEKING = false;
                if (VIDEO_VIEW.isPlaying()) {
                    VIDEO_VIEW.seekTo((int) VIDEO_POSITION);
                } else {
                    VIDEO_VIEW.seekTo((int) VIDEO_POSITION);
                }
                updateOverlaysVisibility();
            }
        });

        VIDEO_VIEW.setOnPreparedListener(mp -> {
            VIDEO_DURATION = mp.getDuration();
            SEEK_BAR.setMax(100);
            updateTimeDisplay();
            updateOverlaysVisibility();
        });

        VIDEO_VIEW.setOnCompletionListener(mp -> {
            IS_PLAYING = false;
            updatePlayPauseButton();
        });
    }

    private void init() {
        VIDEO_VIEW = findViewById(R.id.videoView);
        OVERLAY_CONTAINER = findViewById(R.id.overlayContainer);
        SEEK_BAR = findViewById(R.id.seekBar);
        TEXT_CURRENT_TIME = findViewById(R.id.textCurrentTime);
        TEXT_TOTAL_TIME = findViewById(R.id.textTotalTime);
        TIMELINE_CONTAINER = findViewById(R.id.timelineContainer);
        OVERLAYS_LIST = findViewById(R.id.overlaysList);
        CONTROLS_CONTAINER = findViewById(R.id.controlsContainer);

        // Setup video
        VIDEO_VIEW.setVideoPath(VIDEO_PATH);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(VIDEO_VIEW);
        VIDEO_VIEW.setMediaController(mc);

        // Setup overlays list
        OVERLAYS_ADAPTER = new OverlaysAdapter(this, OVERLAYS, this::editOverlay, this::deleteOverlay);
        OVERLAYS_LIST.setLayoutManager(new LinearLayoutManager(this));
        OVERLAYS_LIST.setAdapter(OVERLAYS_ADAPTER);

        // Start position update
        startPositionUpdates();
    }

    private void startPositionUpdates() {
        HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!IS_SEEKING && VIDEO_VIEW.isPlaying()) {
                    VIDEO_POSITION = VIDEO_VIEW.getCurrentPosition();
                    int progress = (int) ((float) VIDEO_POSITION / VIDEO_DURATION * 100f);
                    SEEK_BAR.setProgress(progress);
                    updateTimeDisplay();
                    updateOverlaysVisibility();
                }
                HANDLER.postDelayed(this, 100);
            }
        }, 100);
    }

    private void updateTimeDisplay() {
        TEXT_CURRENT_TIME.setText(formatTime(VIDEO_POSITION));
        TEXT_TOTAL_TIME.setText(formatTime(VIDEO_DURATION));
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void togglePlayPause() {
        if (IS_PLAYING) {
            VIDEO_VIEW.pause();
            IS_PLAYING = false;
        } else {
            VIDEO_VIEW.start();
            IS_PLAYING = true;
        }
        updatePlayPauseButton();
    }

    private void updatePlayPauseButton() {
        ImageButton btn = findViewById(R.id.btnPlayPause);
        btn.setImageResource(IS_PLAYING ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void updateOverlaysVisibility() {
        OVERLAY_CONTAINER.removeAllViews();

        for (OverlayItem overlay : OVERLAYS) {
            if (VIDEO_POSITION >= overlay.getStartTime() && VIDEO_POSITION <= overlay.getEndTime()) {
                addOverlayView(overlay);
            }
        }
    }

    private void addOverlayView(OverlayItem overlay) {
        View overlayView = createOverlayView(overlay);
        if (overlayView != null) {
            OVERLAY_CONTAINER.addView(overlayView);
        }
    }

    private View createOverlayView(OverlayItem overlay) {
        if (overlay.getType() == OverlayType.TEXT) {
            return createTextOverlayView(overlay);
        } else if (overlay.getType() == OverlayType.IMAGE || overlay.getType() == OverlayType.WATERMARK) {
            return createImageOverlayView(overlay);
        }
        return null;
    }

    private View createTextOverlayView(OverlayItem overlay) {
        TextView textView = new TextView(this);
        textView.setText(overlay.getText());
        textView.setTextSize(overlay.getSize());
        textView.setTextColor(overlay.getColor());
        textView.setAlpha(overlay.getOpacity() / 100f);

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textView.setLayoutParams(params);

        // Position
        int gravity = overlay.getPosition();
        if ((gravity & android.view.Gravity.CENTER) != 0) {
            textView.setGravity(android.view.Gravity.CENTER);
        } else if ((gravity & android.view.Gravity.END) != 0) {
            textView.setGravity(android.view.Gravity.END | android.view.Gravity.BOTTOM);
        } else if ((gravity & android.view.Gravity.START) != 0) {
            textView.setGravity(android.view.Gravity.START | android.view.Gravity.TOP);
        } else {
            textView.setGravity(android.view.Gravity.END | android.view.Gravity.BOTTOM);
        }

        // Add margin
        int margin = Utils.Converter.dpToPx(this, 16);
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) params).setMargins(margin, margin, margin, margin);
        }

        return textView;
    }

    private View createImageOverlayView(OverlayItem overlay) {
        ImageView imageView = new ImageView(this);

        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(overlay.getImagePath());
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setAlpha(overlay.getOpacity() / 100f);

                // Scale image
                int targetWidth = bitmap.getWidth() / 4;
                int targetHeight = (int) (targetWidth * (float) bitmap.getHeight() / bitmap.getWidth());

                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(targetWidth, targetHeight);
                imageView.setLayoutParams(params);
            }
        } catch (Exception e) {
            return null;
        }

        return imageView;
    }

    private void showAddOverlayDialog() {
        new DialogPopup(
            this,
            R.string.editor_btn_add_overlay,
            R.string.editor_btn_add_overlay,
            R.string.editor_overlay_text,
            () -> showAddTextOverlayDialog(),
            R.string.editor_overlay_image,
            () -> showAddImageOverlayDialog(),
            false,
            false,
            false
        ).show();
    }

    private void showAddTextOverlayDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_overlay_text_settings, null);

        EditText editText = view.findViewById(R.id.editText);
        SeekBar sizeSeekBar = view.findViewById(R.id.seekBarSize);
        SeekBar opacitySeekBar = view.findViewById(R.id.seekBarOpacity);
        View colorPreview = view.findViewById(R.id.colorPreview);

        editText.setText("");
        sizeSeekBar.setProgress(SIZE_DEFAULT);
        opacitySeekBar.setProgress(OPACITY_DEFAULT);
        colorPreview.setBackgroundColor(COLOR_DEFAULT);

        new DialogPopup(
            this,
            R.string.editor_overlay_text,
            "",
            R.string.popup_btn_ok,
            () -> {
                String overlayText = editText.getText().toString();
                if (overlayText.isEmpty()) {
                    Toast.makeText(this, R.string.toast_error_generic, Toast.LENGTH_SHORT).show();
                    return;
                }

                addTextOverlay(
                    overlayText,
                    sizeSeekBar.getProgress(),
                    opacitySeekBar.getProgress(),
                    COLOR_DEFAULT,
                    POSITION_DEFAULT
                );
            },
            R.string.popup_btn_cancel,
            null,
            false,
            false,
            false
        ).show();
    }

    private void showAddImageOverlayDialog() {
        Intent intent = new Intent(this, dev.dect.scrnshoot.activity.FilePickerActivity.class);
        intent.putExtra(dev.dect.scrnshoot.activity.FilePickerActivity.INTENT_TYPE,
            dev.dect.scrnshoot.activity.FilePickerActivity.TYPE_IMAGE);
        startActivityForResult(intent, REQUEST_CODE_IMAGE_PICKER);
    }

    private static final int REQUEST_CODE_IMAGE_PICKER = 100;

    private void addTextOverlay(String text, int size, int opacity, int color, int position) {
        OverlayItem overlay = new OverlayItem(
            UUID.randomUUID().toString(),
            OverlayType.TEXT,
            0, // Start at beginning
            VIDEO_DURATION > 0 ? VIDEO_DURATION : 5000, // Default 5 seconds or full video
            text,
            null,
            size,
            opacity,
            color,
            position
        );
        OVERLAYS.add(overlay);
        OVERLAYS_ADAPTER.notifyDataSetChanged();
        updateOverlaysVisibility();
    }

    private void addImageOverlay(String imagePath) {
        OverlayItem overlay = new OverlayItem(
            UUID.randomUUID().toString(),
            OverlayType.IMAGE,
            0,
            VIDEO_DURATION > 0 ? VIDEO_DURATION : 5000,
            null,
            imagePath,
            SIZE_DEFAULT,
            OPACITY_DEFAULT,
            COLOR_DEFAULT,
            POSITION_DEFAULT
        );
        OVERLAYS.add(overlay);
        OVERLAYS_ADAPTER.notifyDataSetChanged();
        updateOverlaysVisibility();
    }

    private void editOverlay(OverlayItem overlay) {
        // For simplicity, delete and re-add
        deleteOverlay(overlay);

        if (overlay.getType() == OverlayType.TEXT) {
            showAddTextOverlayDialog();
        }
    }

    private void deleteOverlay(OverlayItem overlay) {
        OVERLAYS.remove(overlay);
        OVERLAYS_ADAPTER.notifyDataSetChanged();
        updateOverlaysVisibility();
    }

    private void exportVideo() {
        if (OVERLAYS.isEmpty()) {
            Toast.makeText(this, R.string.editor_no_overlays, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.notification_processing_message, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean ok = exportVideoWithOverlays();

            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, R.string.editor_export_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.toast_error_generic, Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }).start();
    }

    private boolean exportVideoWithOverlays() {
        final File input = new File(VIDEO_PATH);

        final VideoSize videoSize = getVideoSize(VIDEO_PATH);
        if (videoSize.width <= 0 || videoSize.height <= 0) {
            return false;
        }

        final Bitmap overlayBitmap = Bitmap.createBitmap(videoSize.width, videoSize.height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(overlayBitmap);

        for (OverlayItem overlay : OVERLAYS) {
            if (overlay.getType() == OverlayType.TEXT || overlay.getType() == OverlayType.WATERMARK) {
                drawTextOverlay(canvas, overlay, videoSize.width, videoSize.height);
            } else if (overlay.getType() == OverlayType.IMAGE) {
                drawImageOverlay(canvas, overlay, videoSize.width, videoSize.height);
            }
        }

        File output = new File(input.getParentFile(), KFile.removeFileExtension(input.getName()) + "_edited." + KFile.getFileExtension(input));
        output = KFile.renameIfNecessary(output);

        return exportWithOverlayBitmap(VIDEO_PATH, output.getAbsolutePath(), overlayBitmap);
    }

    @OptIn(markerClass = UnstableApi.class)
    private boolean exportWithOverlayBitmap(@NonNull String inputPath, @NonNull String outputPath, @NonNull Bitmap overlayBitmap) {
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            if (new File(outputPath).exists()) {
                //noinspection ResultOfMethodCallIgnored
                new File(outputPath).delete();
            }

            final Transformer transformer = new Transformer.Builder(this)
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
                    latch.countDown();
                }
            });

            final BitmapOverlay bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap);
            final OverlayEffect overlayEffect = new OverlayEffect(ImmutableList.of(bitmapOverlay));
            final Effects effects = new Effects(ImmutableList.of(overlayEffect), ImmutableList.of());

            final EditedMediaItem editedVideo = new EditedMediaItem.Builder(MediaItem.fromUri(inputPath))
                .setEffects(effects)
                .build();

            final Composition composition = new Composition.Builder(new EditedMediaItemSequence(editedVideo)).build();

            transformer.start(composition, outputPath);

            //noinspection ResultOfMethodCallIgnored
            latch.await(EXPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            return success.get() && new File(outputPath).exists() && new File(outputPath).length() > 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (!overlayBitmap.isRecycled()) {
                    overlayBitmap.recycle();
                }
            } catch (Exception ignore) {}
        }
    }

    private void drawTextOverlay(@NonNull Canvas canvas, @NonNull OverlayItem overlay, int videoWidth, int videoHeight) {
        final String text = overlay.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        final float textSizePx = overlay.getSize() * getResources().getDisplayMetrics().scaledDensity;

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(overlay.getColor());
        paint.setTextSize(textSizePx);
        paint.setShadowLayer(3f, 2f, 2f, Color.BLACK);
        paint.setAlpha((int) (255 * (Math.max(0, Math.min(100, overlay.getOpacity())) / 100f)));

        final Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        final float margin = Math.max(16f, videoWidth * 0.02f);

        float x;
        if ((overlay.getPosition() & Gravity.END) != 0) {
            x = videoWidth - bounds.width() - margin;
        } else if ((overlay.getPosition() & Gravity.CENTER_HORIZONTAL) != 0 || (overlay.getPosition() & Gravity.CENTER) != 0) {
            x = (videoWidth - bounds.width()) / 2f;
        } else {
            x = margin;
        }

        float y;
        if ((overlay.getPosition() & Gravity.BOTTOM) != 0) {
            y = videoHeight - margin;
        } else if ((overlay.getPosition() & Gravity.CENTER_VERTICAL) != 0 || (overlay.getPosition() & Gravity.CENTER) != 0) {
            y = (videoHeight + bounds.height()) / 2f;
        } else {
            y = bounds.height() + margin;
        }

        canvas.drawText(text, x, y, paint);
    }

    private void drawImageOverlay(@NonNull Canvas canvas, @NonNull OverlayItem overlay, int videoWidth, int videoHeight) {
        final String path = overlay.getImagePath();
        if (path == null || !(new File(path).exists())) {
            return;
        }

        final Bitmap image = BitmapFactory.decodeFile(path);
        if (image == null) {
            return;
        }

        final float margin = Math.max(16f, videoWidth * 0.02f);

        final float maxWidth = videoWidth * 0.30f;
        final float maxHeight = videoHeight * 0.30f;
        final float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());

        final int drawW = Math.max(1, (int) (image.getWidth() * scale));
        final int drawH = Math.max(1, (int) (image.getHeight() * scale));

        float left;
        if ((overlay.getPosition() & Gravity.END) != 0) {
            left = videoWidth - drawW - margin;
        } else if ((overlay.getPosition() & Gravity.CENTER_HORIZONTAL) != 0 || (overlay.getPosition() & Gravity.CENTER) != 0) {
            left = (videoWidth - drawW) / 2f;
        } else {
            left = margin;
        }

        float top;
        if ((overlay.getPosition() & Gravity.BOTTOM) != 0) {
            top = videoHeight - drawH - margin;
        } else if ((overlay.getPosition() & Gravity.CENTER_VERTICAL) != 0 || (overlay.getPosition() & Gravity.CENTER) != 0) {
            top = (videoHeight - drawH) / 2f;
        } else {
            top = margin;
        }

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setAlpha((int) (255 * (Math.max(0, Math.min(100, overlay.getOpacity())) / 100f)));

        final Rect dst = new Rect((int) left, (int) top, (int) left + drawW, (int) top + drawH);
        canvas.drawBitmap(image, null, dst, paint);
    }

    private VideoSize getVideoSize(@NonNull String inputPath) {
        final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(inputPath);
            final String widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            final String heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

            int width = widthStr != null ? Integer.parseInt(widthStr) : -1;
            int height = heightStr != null ? Integer.parseInt(heightStr) : -1;

            return new VideoSize(width, height);
        } catch (Exception e) {
            return new VideoSize(-1, -1);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignore) {}
        }
    }

    private static class VideoSize {
        final int width;
        final int height;

        VideoSize(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMAGE_PICKER && resultCode == Activity.RESULT_OK) {
            String imagePath = data.getStringExtra(dev.dect.scrnshoot.activity.FilePickerActivity.INTENT_PATH);
            if (imagePath != null) {
                addImageOverlay(imagePath);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        HANDLER.removeCallbacksAndMessages(null);
    }

    /**
     * Open video editor for a video file.
     */
    public static void open(@NonNull Context context, @NonNull String videoPath) {
        Intent intent = new Intent(context, VideoEditorActivity.class);
        intent.putExtra(EXTRA_VIDEO_PATH, videoPath);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /**
     * Adapter for the overlays list.
     */
    private static class OverlaysAdapter extends RecyclerView.Adapter<OverlaysAdapter.ViewHolder> {
        private final Context context;
        private final List<OverlayItem> overlays;
        private final OnOverlayAction editCallback;
        private final OnOverlayAction deleteCallback;

        interface OnOverlayAction {
            void onAction(OverlayItem overlay);
        }

        OverlaysAdapter(Context context, List<OverlayItem> overlays, OnOverlayAction editCallback, OnOverlayAction deleteCallback) {
            this.context = context;
            this.overlays = overlays;
            this.editCallback = editCallback;
            this.deleteCallback = deleteCallback;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_overlay, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OverlayItem overlay = overlays.get(position);
            holder.bind(overlay);
        }

        @Override
        public int getItemCount() {
            return overlays.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textType;
            private final TextView textInfo;
            private final ImageButton btnEdit;
            private final ImageButton btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textType = itemView.findViewById(R.id.textType);
                textInfo = itemView.findViewById(R.id.textInfo);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
            }

            void bind(OverlayItem overlay) {
                String typeName = "";
                switch (overlay.getType()) {
                    case TEXT:
                        typeName = context.getString(R.string.editor_overlay_text);
                        textInfo.setText(overlay.getText());
                        break;
                    case IMAGE:
                        typeName = context.getString(R.string.editor_overlay_image);
                        String path = overlay.getImagePath();
                        textInfo.setText(path != null ? path.substring(Math.max(0, path.lastIndexOf('/') + 1)) : "");
                        break;
                    case WATERMARK:
                        typeName = context.getString(R.string.editor_overlay_watermark);
                        textInfo.setText(overlay.getText());
                        break;
                }
                textType.setText(typeName);

                btnEdit.setOnClickListener(v -> editCallback.onAction(overlay));
                btnDelete.setOnClickListener(v -> deleteCallback.onAction(overlay));
            }
        }
    }
}
