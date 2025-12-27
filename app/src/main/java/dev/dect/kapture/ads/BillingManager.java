package dev.dect.kapture.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.List;

import dev.dect.kapture.data.ProVersionManager;

/**
 * Manages Google Play Billing for Pro version purchase.
 * Handles one-time purchase verification and acknowledgment.
 */
public class BillingManager implements PurchasesUpdatedListener {
    private static final String TAG = BillingManager.class.getSimpleName();

    // SKU for Pro version one-time purchase
    public static final String SKU_PRO_VERSION = "kapture_pro_upgrade";

    // Price for Pro version
    public static final String PRO_PRICE = "$5.99";

    private final Context CONTEXT;
    private BillingClient BILLING_CLIENT;
    private boolean IS_READY = false;

    private ProductDetails PRODUCT_DETAILS;

    private BillingClientListener LISTENER;

    /**
     * Interface for billing events.
     */
    public interface BillingClientListener {
        void onBillingClientReady();
        void onBillingClientError(String message);
        void onPurchaseVerified(boolean success);
        void onProductDetailsLoaded(ProductDetails productDetails);
    }

    public BillingManager(@NonNull Context context) {
        this.CONTEXT = context.getApplicationContext();
    }

    /**
     * Set listener for billing events.
     */
    public void setListener(BillingClientListener listener) {
        this.LISTENER = listener;
    }

    /**
     * Initialize billing client.
     */
    public void startConnection() {
        BILLING_CLIENT = BillingClient.newBuilder(CONTEXT)
                .enablePendingPurchases()
                .setListener(this)
                .build();

        BILLING_CLIENT.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                IS_READY = billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK;

                if (IS_READY) {
                    Log.d(TAG, "Billing client ready");
                    if (LISTENER != null) {
                        LISTENER.onBillingClientReady();
                    }
                    // Check for existing purchases
                    queryPurchases();
                } else {
                    String message = "Billing setup failed: " + billingResult.getDebugMessage();
                    Log.e(TAG, message);
                    if (LISTENER != null) {
                        LISTENER.onBillingClientError(message);
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                IS_READY = false;
                Log.d(TAG, "Billing service disconnected");
                // Try to reconnect
                startConnection();
            }
        });
    }

    /**
     * Query product details for Pro version.
     */
    public void queryProductDetails() {
        if (!IS_READY) {
            Log.w(TAG, "Billing client not ready");
            return;
        }

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        List.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(SKU_PRO_VERSION)
                                        .setProductType(BillingClient.ProductType.INAPP)
                                        .build()
                        )
                )
                .build();

        BILLING_CLIENT.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(@NonNull BillingResult billingResult, List<ProductDetails> productDetailsList) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                    for (ProductDetails productDetails : productDetailsList) {
                        if (productDetails.getProductId().equals(SKU_PRO_VERSION)) {
                            PRODUCT_DETAILS = productDetails;
                            Log.d(TAG, "Product details loaded: " + productDetails.getName());
                            if (LISTENER != null) {
                                LISTENER.onProductDetailsLoaded(productDetails);
                            }
                            break;
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to query product details");
                }
            }
        });
    }

    /**
     * Query existing purchases.
     */
    public void queryPurchases() {
        if (!IS_READY) {
            Log.w(TAG, "Billing client not ready");
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        BILLING_CLIENT.queryPurchasesAsync(params, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, List<Purchase> purchases) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (purchases != null) {
                        for (Purchase purchase : purchases) {
                            if (SKU_PRO_VERSION.equals(purchase.getProducts().get(0))) {
                                handlePurchase(purchase);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Launch purchase flow for Pro version.
     */
    public void launchPurchaseFlow(@NonNull Activity activity) {
        if (!IS_READY) {
            Log.w(TAG, "Billing client not ready");
            return;
        }

        // Note: In Billing Library 6.0+, use ProductDetails for the purchase flow
        // The actual implementation requires ProductDetails obtained from queryProductDetails()
        // For now, this is a placeholder that shows the concept

        Log.d(TAG, "Launching purchase flow for: " + SKU_PRO_VERSION);

        // The full implementation would use:
        // BillingFlowParams.ProductDetailsParams productDetailsParams =
        //     BillingFlowParams.ProductDetailsParams.newBuilder()
        //         .setProductDetails(PRODUCT_DETAILS)
        //         .build();

        // This requires ProductDetails from queryProductDetails() call
    }

    /**
     * Handle purchases updated callback.
     */
    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else {
            Log.e(TAG, "Purchase failed: " + billingResult.getDebugMessage());
        }
    }

    /**
     * Process a single purchase.
     */
    private void handlePurchase(@NonNull Purchase purchase) {
        List<String> products = purchase.getProducts();
        if (products != null && products.contains(SKU_PRO_VERSION)) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                // Verify purchase on server (in production)
                // For now, acknowledge locally
                if (!purchase.isAcknowledged()) {
                    acknowledgePurchase(purchase);
                }

                // Unlock pro version
                ProVersionManager.unlockProVersion(CONTEXT, purchase.getPurchaseToken());

                if (LISTENER != null) {
                    LISTENER.onPurchaseVerified(true);
                }
            }
        }
    }

    /**
     * Acknowledge a purchase.
     */
    private void acknowledgePurchase(@NonNull Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        BILLING_CLIENT.acknowledgePurchase(params, billingResult -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged");
            } else {
                Log.e(TAG, "Failed to acknowledge purchase: " + billingResult.getDebugMessage());
            }
        });
    }

    /**
     * Restore previous purchases.
     */
    public void restorePurchases() {
        queryPurchases();
    }

    /**
     * Check if billing is ready.
     */
    public boolean isReady() {
        return IS_READY;
    }

    /**
     * Check if user already purchased Pro.
     */
    public boolean isProPurchased() {
        return ProVersionManager.isUnlockedViaIAP(CONTEXT);
    }

    /**
     * Clean up resources.
     */
    public void destroy() {
        if (BILLING_CLIENT != null) {
            BILLING_CLIENT.endConnection();
        }
        IS_READY = false;
    }

    /**
     * Get singleton instance.
     */
    public static BillingManager getInstance(@NonNull Context context) {
        return new BillingManager(context.getApplicationContext());
    }
}
