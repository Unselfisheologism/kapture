package dev.dect.kapture.data;

import android.content.Context;
import android.content.SharedPreferences;

import dev.dect.kapture.BuildConfig;

/**
 * Manages Pro version detection and license check.
 * Supports both build flavor detection and IAP-based unlock.
 */
public class ProVersionManager {
    private static final String SP_NAME = "kapture_pro";
    private static final String KEY_IS_PRO = "is_pro";
    private static final String KEY_PURCHASE_TOKEN = "purchase_token";

    private static final float ZOOM_FACTOR_MIN = 1.5f;
    private static final float ZOOM_FACTOR_MAX = 3.0f;
    private static final float ZOOM_FACTOR_DEFAULT = 2.0f;

    private static boolean cachedIsPro = false;
    private static boolean cacheInitialized = false;

    /**
     * Check if the current build is the Pro version (build flavor).
     * @return true if this is the pro build
     */
    public static boolean isProBuild() {
        return BuildConfig.IS_PRO_VERSION;
    }

    /**
     * Check if the current build is the Free version (build flavor).
     * @return true if this is the free build
     */
    public static boolean isFreeBuild() {
        return BuildConfig.IS_FREE_VERSION;
    }

    /**
     * Check if user has pro features unlocked (either via build flavor or IAP).
     * @param context Application context
     * @return true if pro features are available
     */
    public static boolean isProVersion(Context context) {
        if (!cacheInitialized) {
            cachedIsPro = loadProStatus(context);
            cacheInitialized = true;
        }
        return cachedIsPro || isProBuild();
    }

    /**
     * Get the actual pro status from storage (for IAP unlock).
     * @param context Application context
     * @return true if pro is unlocked via IAP
     */
    private static boolean loadProStatus(Context context) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IS_PRO, false);
    }

    /**
     * Unlock pro version via IAP purchase.
     * @param context Application context
     * @param purchaseToken The purchase token from Google Play Billing
     * @return true if successfully unlocked
     */
    public static boolean unlockProVersion(Context context, String purchaseToken) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(KEY_IS_PRO, true);
        editor.putString(KEY_PURCHASE_TOKEN, purchaseToken);
        editor.apply();
        cachedIsPro = true;
        cacheInitialized = true;
        return true;
    }

    /**
     * Check if pro was unlocked via IAP (not build flavor).
     * @param context Application context
     * @return true if pro is unlocked via IAP
     */
    public static boolean isUnlockedViaIAP(Context context) {
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_IS_PRO, false);
    }

    /**
     * Get the purchase token if pro was unlocked via IAP.
     * @param context Application context
     * @return purchase token or null if not applicable
     */
    public static String getPurchaseToken(Context context) {
        if (context == null) return null;
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_PURCHASE_TOKEN, null);
    }

    /**
     * Invalidate cache to force re-check of pro status.
     */
    public static void invalidateCache() {
        cacheInitialized = false;
        cachedIsPro = false;
    }

    /**
     * Get the default tap-to-zoom factor.
     * @return zoom factor (e.g., 2.0 for 2x zoom)
     */
    public static float getDefaultZoomFactor() {
        return ZOOM_FACTOR_DEFAULT;
    }

    /**
     * Get the minimum tap-to-zoom factor.
     * @return minimum zoom factor
     */
    public static float getMinZoomFactor() {
        return ZOOM_FACTOR_MIN;
    }

    /**
     * Get the maximum tap-to-zoom factor.
     * @return maximum zoom factor
     */
    public static float getMaxZoomFactor() {
        return ZOOM_FACTOR_MAX;
    }

    /**
     * Check if ads should be shown (only for free version).
     * @param context Application context
     * @return true if ads should be displayed
     */
    public static boolean shouldShowAds(Context context) {
        return isFreeBuild() && !isProVersion(context);
    }

    /**
     * Check if default watermark should be added (only for free version without custom watermark).
     * @param context Application context
     * @return true if default watermark should be added
     */
    public static boolean shouldShowDefaultWatermark(Context context) {
        return isFreeBuild() && !isProVersion(context);
    }
}
