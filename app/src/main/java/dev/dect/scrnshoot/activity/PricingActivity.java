package dev.dect.scrnshoot.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import dev.dect.scrnshoot.R;
import dev.dect.scrnshoot.ads.BillingManager;
import dev.dect.scrnshoot.data.ProVersionManager;

/**
 * Pricing activity for the free version.
 * Shows upgrade options and handles Pro purchase flow.
 */
@SuppressLint("SetTextI18n")
public class PricingActivity extends AppCompatActivity {

    private static final String SKU_PRO_UPGRADE = "scrnshoot_pro_upgrade";

    private BillingManager BILLING_MANAGER;

    private boolean IS_PURCHASE_IN_PROGRESS = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pricing);

        initVariables();

        initListeners();

        init();
    }

    private void initVariables() {
        BILLING_MANAGER = BillingManager.getInstance(this);
    }

    private void initListeners() {
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        findViewById(R.id.btnUpgrade).setOnClickListener(v -> {
            if (BILLING_MANAGER.isReady()) {
                launchPurchaseFlow();
            } else {
                // Fallback to Play Store
                openPlayStore();
            }
        });

        findViewById(R.id.btnRestore).setOnClickListener(v -> {
            if (BILLING_MANAGER.isReady()) {
                BILLING_MANAGER.restorePurchases();
            } else {
                Toast.makeText(this, R.string.toast_error_billing_not_available, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void init() {
        // Check if already pro
        if (ProVersionManager.isProVersion(this)) {
            showAlreadyProState();
            return;
        }

        // Initialize billing
        if (BILLING_MANAGER.isBillingAvailable()) {
            BILLING_MANAGER.setListener(new BillingManager.BillingClientListener() {
                @Override
                public void onBillingClientReady() {
                    runOnUiThread(() -> {
                        AppCompatButton btnUpgrade = findViewById(R.id.btnUpgrade);
                        btnUpgrade.setEnabled(true);
                        btnUpgrade.setText(R.string.pricing_btn_upgrade);
                    });
                    BILLING_MANAGER.queryProductDetails();
                }

                @Override
                public void onBillingClientError(String message) {
                    runOnUiThread(() -> {
                        // Billing not available, use Play Store fallback
                        AppCompatButton btnUpgrade = findViewById(R.id.btnUpgrade);
                        btnUpgrade.setEnabled(true);
                        btnUpgrade.setText(R.string.pricing_btn_get_pro);
                    });
                }

                @Override
                public void onPurchaseVerified(boolean success) {
                    if (success) {
                        runOnUiThread(() -> {
                            ProVersionManager.invalidateCache();
                            showAlreadyProState();
                        });
                    }
                }

                @Override
                public void onProductDetailsLoaded(Object productDetails) {
                    // Product details loaded, ready for purchase
                }
            });
            BILLING_MANAGER.startConnection();
        } else {
            // Billing not available (shouldn't happen in free version)
            AppCompatButton btnUpgrade = findViewById(R.id.btnUpgrade);
            btnUpgrade.setEnabled(true);
            btnUpgrade.setText(R.string.pricing_btn_get_pro);
        }

        // Update pro features list
        updateProFeaturesList();
    }

    private void updateProFeaturesList() {
        LinearLayout featuresContainer = findViewById(R.id.featuresContainer);

        // Add feature items
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_no_ads));
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_custom_watermark));
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_tap_to_zoom));
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_no_time_limit));
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_priority_support));
        addFeatureItem(featuresContainer, getString(R.string.pricing_feature_all_future_features));
    }

    private void addFeatureItem(LinearLayout container, String text) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_pricing_feature, container, false);

        TextView textView = itemView.findViewById(R.id.featureText);

        textView.setText(text);

        container.addView(itemView);
    }

    private void showAlreadyProState() {
        ScrollView contentScrollView = findViewById(R.id.contentScrollView);
        LinearLayout alreadyProView = findViewById(R.id.alreadyProView);
        LinearLayout upgradeView = findViewById(R.id.upgradeView);
        View btnUpgrade = findViewById(R.id.btnUpgrade);
        View btnRestore = findViewById(R.id.btnRestore);

        contentScrollView.setVisibility(View.GONE);
        alreadyProView.setVisibility(View.VISIBLE);
        upgradeView.setVisibility(View.GONE);
        btnUpgrade.setVisibility(View.GONE);
        btnRestore.setVisibility(View.GONE);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
    }

    private void launchPurchaseFlow() {
        if (IS_PURCHASE_IN_PROGRESS) {
            return;
        }

        IS_PURCHASE_IN_PROGRESS = true;

        BILLING_MANAGER.launchPurchaseFlow(this);

        // Reset flag after a delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            () -> IS_PURCHASE_IN_PROGRESS = false,
            5000
        );
    }

    private void openPlayStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (BILLING_MANAGER != null) {
            BILLING_MANAGER.destroy();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BillingManager.REQUEST_CODE_PURCHASE) {
            if (resultCode == Activity.RESULT_OK) {
                // Purchase might have been completed
                // The BillingManager listener will handle the verification
            }
        }
    }

    /**
     * Open the pricing activity.
     */
    public static void open(Context context) {
        Intent intent = new Intent(context, PricingActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }
}
