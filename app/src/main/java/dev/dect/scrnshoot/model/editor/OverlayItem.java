package dev.dect.scrnshoot.model.editor;

/**
 * Represents an overlay that can be added to a video.
 * Contains all properties needed for rendering and timing.
 */
public class OverlayItem {
    private final String id;
    private final OverlayType type;
    private final long startTime;
    private final long endTime;
    private final String text;
    private final String imagePath;
    private final int size;
    private final int opacity;
    private final int color;
    private final int position;

    public OverlayItem(String id, OverlayType type, long startTime, long endTime,
                       String text, String imagePath, int size, int opacity, int color, int position) {
        this.id = id;
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.imagePath = imagePath;
        this.size = size;
        this.opacity = opacity;
        this.color = color;
        this.position = position;
    }

    public String getId() {
        return id;
    }

    public OverlayType getType() {
        return type;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public String getText() {
        return text;
    }

    public String getImagePath() {
        return imagePath;
    }

    public int getSize() {
        return size;
    }

    public int getOpacity() {
        return opacity;
    }

    public int getColor() {
        return color;
    }

    public int getPosition() {
        return position;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    /**
     * Check if this overlay is visible at the given time.
     */
    public boolean isVisibleAt(long time) {
        return time >= startTime && time <= endTime;
    }
}
