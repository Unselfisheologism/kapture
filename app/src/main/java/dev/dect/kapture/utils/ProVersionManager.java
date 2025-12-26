package dev.dect.kapture.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import dev.dect.kapture.BuildConfig;

public class ProVersionManager {
    
    public static boolean isProVersion(Context context) {
        // Check if this is the pro build flavor
        return BuildConfig.FLAVOR.equals("pro");
    }
    
    public static boolean isFreeVersion(Context context) {
        return !isProVersion(context);
    }
    
    public static boolean isTapToZoomAvailable(Context context) {
        return isProVersion(context);
    }
    
    public static boolean isCustomWatermarkAvailable(Context context) {
        return isProVersion(context);
    }
    
    public static boolean shouldShowAds(Context context) {
        return isFreeVersion(context);
    }
    
    public static boolean shouldAddDefaultWatermark(Context context) {
        return isFreeVersion(context);
    }
}