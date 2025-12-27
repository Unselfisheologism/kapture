package dev.dect.scrnshoot.ads;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import dev.dect.scrnshoot.R;
import dev.dect.scrnshoot.data.ProVersionManager;

/**
 * AdManager handles ad display for the free version.
 * Supports banner ads and interstitial ads using Media.net/Prebid Mobile SDK.
 */
public class AdManager {
    private static final String TAG = AdManager.class.getSimpleName();

    private static final String EXPORT_COUNTER_KEY = "export_counter";
    private static final int MAX_EXPORTS_BEFORE_INTERSTITIAL = 3;

    private final Context CONTEXT;

    private View BANNER_AD_VIEW;
    private LinearLayout BANNER_CONTAINER;

    private int EXPORT_COUNTER = 0;

    private boolean IS_INTERSTITIAL_LOADED = false;

    public AdManager(@NonNull Context context) {
        this.CONTEXT = context;
        this.EXPORT_COUNTER = getExportCounter();
    }

    /**
     * Check if ads should be shown.
     */
    public boolean shouldShowAds() {
        return ProVersionManager.shouldShowAds(CONTEXT);
    }

    /**
     * Static check if ads should be shown (for use before instance creation).
     */
    public static boolean shouldShowAdsStatic(Context ctx) {
        return ProVersionManager.shouldShowAds(ctx);
    }

    /**
     * Initialize banner ad container.
     * @param container The parent layout to add banner ads to
     */
    public void initializeBannerAd(@NonNull LinearLayout container) {
        if (!shouldShowAds() || BANNER_AD_VIEW != null) {
            return;
        }

        this.BANNER_CONTAINER = container;

        // Create banner ad container
        FrameLayout bannerFrame = new FrameLayout(CONTEXT);
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bannerParams.gravity = Gravity.BOTTOM;
        bannerFrame.setLayoutParams(bannerParams);

        // TODO: Initialize Media.net/Prebid Mobile SDK banner
        // For now, show a placeholder that can be replaced with actual SDK integration
        View placeholder = new View(CONTEXT);
        placeholder.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                CONTEXT.getResources().getDimensionPixelSize(R.dimen.ad_banner_height)
        ));
        placeholder.setBackgroundColor(CONTEXT.getColor(R.color.ad_banner_placeholder));
        placeholder.setVisibility(View.GONE); // Hide until SDK is integrated

        bannerFrame.addView(placeholder);
        container.addView(bannerFrame);

        this.BANNER_AD_VIEW = bannerFrame;
    }

    /**
     * Load and show banner ads.
     */
    public void loadBannerAd() {
        if (!shouldShowAds() || BANNER_AD_VIEW == null) {
            return;
        }

        // TODO: Load banner ad using Media.net/Prebid Mobile SDK
        // Example implementation:
        // PrebidMobile.initializeSdk(CONTEXT, "YOUR_CONFIG_ID");
        // BannerAdView bannerAdView = new BannerAdView(CONTEXT);
        // bannerAdView.setAdUnitId("YOUR_BANNER_UNIT_ID");
        // bannerAdView.setAdSize(new AdSize(320, 50));
        // PrebidMobile.fetchDemand(bannerAdView, new OnCompleteListener() {
        //     @Override
        //     public void onComplete(ResultCode resultCode) {
        //         if (resultCode == ResultCode.SUCCESS) {
        //             bannerAdView.loadAd();
        //         }
        //     }
        // });
    }

    /**
     * Hide banner ads.
     */
    public void hideBannerAd() {
        if (BANNER_AD_VIEW != null) {
            BANNER_AD_VIEW.setVisibility(View.GONE);
        }
    }

    /**
     * Show banner ads.
     */
    public void showBannerAd() {
        if (shouldShowAds() && BANNER_AD_VIEW != null) {
            BANNER_AD_VIEW.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Pre-load interstitial ad.
     */
    public void preloadInterstitialAd() {
        if (!shouldShowAds()) {
            return;
        }

        // TODO: Pre-load interstitial ad using Media.net/Prebid Mobile SDK
        // Example implementation:
        // InterstitialAd interstitialAd = new InterstitialAd(CONTEXT);
        // interstitialAd.setAdUnitId("YOUR_INTERSTITIAL_UNIT_ID");
        // interstitialAd.loadAd(new AdRequest.Builder().build());
        // interstitialAd.setAdListener(new AdListener() {
        //     @Override
        //     public void onAdLoaded() {
        //         IS_INTERSTITIAL_LOADED = true;
        //     }
        // });
    }

    /**
     * Check if interstitial ad should be shown after export.
     * @return true if interstitial should be shown
     */
    public boolean shouldShowInterstitialAfterExport() {
        if (!shouldShowAds()) {
            return false;
        }

        EXPORT_COUNTER++;
        updateExportCounter();

        if (EXPORT_COUNTER >= MAX_EXPORTS_BEFORE_INTERSTITIAL) {
            EXPORT_COUNTER = 0;
            updateExportCounter();
            return true;
        }

        return false;
    }

    /**
     * Show interstitial ad if available.
     * @return true if ad was shown
     */
    public boolean showInterstitialAd() {
        if (!shouldShowAds() || !IS_INTERSTITIAL_LOADED) {
            return false;
        }

        // TODO: Show interstitial ad
        // Example implementation:
        // if (interstitialAd != null && interstitialAd.isLoaded()) {
        //     interstitialAd.show();
        //     IS_INTERSTITIAL_LOADED = false;
        //     preloadInterstitialAd();
        //     return true;
        // }

        return false;
    }

    /**
     * Called after export to potentially show interstitial ad.
     */
    public void onExportComplete() {
        if (shouldShowInterstitialAfterExport()) {
            showInterstitialAd();
        }
    }

    /**
     * Get the current export counter.
     */
    private int getExportCounter() {
        SharedPreferences sp = CONTEXT.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
        return sp.getInt(EXPORT_COUNTER_KEY, 0);
    }

    /**
     * Update the export counter.
     */
    private void updateExportCounter() {
        SharedPreferences sp = CONTEXT.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE);
        sp.edit().putInt(EXPORT_COUNTER_KEY, EXPORT_COUNTER).apply();
    }

    /**
     * Reset export counter (useful for testing).
     */
    public void resetExportCounter() {
        EXPORT_COUNTER = 0;
        updateExportCounter();
    }

    /**
     * Destroy ad resources.
     */
    public void destroy() {
        if (BANNER_AD_VIEW != null) {
            BANNER_AD_VIEW.setVisibility(View.GONE);
        }
    }

    /**
     * Get singleton instance.
     */
    public static AdManager getInstance(@NonNull Context context) {
        return new AdManager(context.getApplicationContext());
    }
}
