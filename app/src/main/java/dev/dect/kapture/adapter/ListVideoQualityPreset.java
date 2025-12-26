package dev.dect.kapture.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Consumer;

import dev.dect.kapture.R;
import dev.dect.kapture.data.Constants;
import dev.dect.kapture.data.KSettings;
import dev.dect.kapture.data.KSharedPreferences;

public class ListVideoQualityPreset extends RecyclerView.Adapter<ListVideoQualityPreset.ViewHolder> {
    
    private final Context CONTEXT;
    private final String TITLE;
    private final String SUBTITLE;
    private final int ICON;
    
    private int selectedPreset = KSettings.VIDEO_QUALITY_PRESET_CUSTOM;
    
    public ListVideoQualityPreset(Context ctx, String title, String subtitle, int icon) {
        this.CONTEXT = ctx;
        this.TITLE = title;
        this.SUBTITLE = subtitle;
        this.ICON = icon;
        
        // Load current preset from settings
        loadCurrentPreset();
    }
    
    private void loadCurrentPreset() {
        SharedPreferences sp = KSharedPreferences.getActiveProfileSp(CONTEXT);
        selectedPreset = sp.getInt(Constants.Sp.Profile.VIDEO_QUALITY_PRESET, KSettings.VIDEO_QUALITY_PRESET_CUSTOM);
    }
    
    private void savePreset(int preset) {
        selectedPreset = preset;
        SharedPreferences.Editor editor = KSharedPreferences.getActiveProfileSp(CONTEXT).edit();
        editor.putInt(Constants.Sp.Profile.VIDEO_QUALITY_PRESET, preset);
        editor.apply();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(CONTEXT).inflate(R.layout.list_button_subtext, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.TITLE.setText(TITLE);
        holder.SUBTITLE.setText(getCurrentPresetDescription());
        
        if(ICON != 0) {
            holder.ICON.setImageResource(ICON);
            holder.ICON.setVisibility(View.VISIBLE);
        } else {
            holder.ICON.setVisibility(View.GONE);
        }
        
        holder.itemView.setOnClickListener((v) -> {
            showPresetSelectionMenu(v);
        });
    }
    
    private String getCurrentPresetDescription() {
        return KSettings.getQualityPresetName(selectedPreset) + " quality preset";
    }
    
    private void showPresetSelectionMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(CONTEXT, anchor);
        
        popupMenu.getMenu().add(Menu.NONE, KSettings.VIDEO_QUALITY_PRESET_LOW, Menu.NONE, "Low (720p 30fps, 4Mbps)");
        popupMenu.getMenu().add(Menu.NONE, KSettings.VIDEO_QUALITY_PRESET_MEDIUM, Menu.NONE, "Medium (1080p 30fps, 8Mbps)");
        popupMenu.getMenu().add(Menu.NONE, KSettings.VIDEO_QUALITY_PRESET_HIGH, Menu.NONE, "High (1080p 60fps, 12Mbps)");
        popupMenu.getMenu().add(Menu.NONE, KSettings.VIDEO_QUALITY_PRESET_CUSTOM, Menu.NONE, "Custom (current settings)");
        
        popupMenu.setOnMenuItemClickListener(item -> {
            int preset = item.getItemId();
            savePreset(preset);
            
            // Apply preset values
            applyPresetValues(preset);
            
            notifyDataSetChanged();
            return true;
        });
        
        popupMenu.show();
    }
    
    private void applyPresetValues(int preset) {
        int[] presetValues = KSettings.getQualityPresetValues(preset);
        if (presetValues != null) {
            // Apply the preset values to settings
            SharedPreferences.Editor editor = KSharedPreferences.getActiveProfileSp(CONTEXT).edit();
            
            // Resolution
            int resolution = presetValues[0];
            for (int i = 0; i < KSettings.VIDEO_RESOLUTIONS.length; i++) {
                if (KSettings.VIDEO_RESOLUTIONS[i] == resolution) {
                    editor.putInt(Constants.Sp.Profile.VIDEO_RESOLUTION, resolution);
                    break;
                }
            }
            
            // Frame rate
            int frameRate = presetValues[1];
            editor.putInt(Constants.Sp.Profile.VIDEO_FRAME_RATE, frameRate);
            
            // Bitrate
            int bitrate = presetValues[2];
            editor.putInt(Constants.Sp.Profile.VIDEO_QUALITY_bitRate, bitrate);
            
            editor.apply();
            
            // Show toast to inform user
            Toast.makeText(CONTEXT, "Applied " + KSettings.getQualityPresetName(preset) + " preset", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public int getItemCount() {
        return 1;
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView TITLE;
        private final TextView SUBTITLE;
        private final androidx.appcompat.widget.AppCompatImageView ICON;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            TITLE = itemView.findViewById(R.id.title);
            SUBTITLE = itemView.findViewById(R.id.subtitle);
            ICON = itemView.findViewById(R.id.icon);
        }
    }
}