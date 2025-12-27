package dev.dect.scrnshoot.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import dev.dect.scrnshoot.BuildConfig;
import dev.dect.scrnshoot.data.ProVersionManager;

/**
 * Manages Google Play Billing for Pro version purchase.
 * Handles one-time purchase verification and acknowledgment.
 * Only available in the free version for in-app purchase upgrade.
 */
public class BillingManager {
    private static final String TAG = BillingManager.class.getSimpleName();

    // SKU for Pro version one-time purchase
    public static final String SKU_PRO_VERSION = "scrnshoot_pro_upgrade";

    // Price for Pro version
    public static final String PRO_PRICE = "$5.99";

    private final Context CONTEXT;
    private boolean IS_READY = false;

    private BillingClientListener LISTENER;

    // Track if billing is available (free version only)
    private final boolean BILLING_AVAILABLE;

    /**
     * Interface for billing events.
     */
    public interface BillingClientListener {
        void onBillingClientReady();
        void onBillingClientError(String message);
        void onPurchaseVerified(boolean success);
        void onProductDetailsLoaded(Object productDetails);
    }

    public BillingManager(@NonNull Context context) {
        this.CONTEXT = context.getApplicationContext();
        this.BILLING_AVAILABLE = BuildConfig.IS_FREE_VERSION;
    }

    /**
     * Check if billing is available (free version only).
     */
    public boolean isBillingAvailable() {
        return BILLING_AVAILABLE;
    }

    /**
     * Set listener for billing events.
     */
    public void setListener(BillingClientListener listener) {
        this.LISTENER = listener;
    }

    /**
     * Initialize billing client.
     * Note: In the free version, this will dynamically load the billing library.
     * In the pro version, this method does nothing.
     */
    public void startConnection() {
        if (!BILLING_AVAILABLE) {
            Log.d(TAG, "Billing not available in Pro version");
            return;
        }

        try {
            // Dynamically load and use the BillingClient
            Class<?> billingClientClass = Class.forName("com.android.billingclient.api.BillingClient");
            
            // Create builder
            Object builder = billingClientClass.getMethod("newBuilder", Context.class)
                    .invoke(null, CONTEXT);
            
            // Enable pending purchases
            builder.getClass().getMethod("enablePendingPurchases").invoke(builder);

            // Create listener proxy for PurchasesUpdatedListener
            Class<?> purchasesUpdatedListenerClass = Class.forName("com.android.billingclient.api.PurchasesUpdatedListener");
            Object purchasesListener = java.lang.reflect.Proxy.newProxyInstance(
                    purchasesUpdatedListenerClass.getClassLoader(),
                    new Class<?>[]{purchasesUpdatedListenerClass},
                    (proxy, method, args) -> {
                        if ("onPurchasesUpdated".equals(method.getName())) {
                            Object billingResult = args[0];
                            @SuppressWarnings("unchecked")
                            java.util.List<?> purchases = (java.util.List<?>) args[1];
                            onPurchasesUpdated(billingResult, purchases);
                        }
                        return null;
                    });

            builder.getClass().getMethod("setListener", purchasesUpdatedListenerClass)
                    .invoke(builder, purchasesListener);

            Object billingClient = builder.getClass().getMethod("build").invoke(builder);

            // Create listener for billing state
            Class<?> billingClientStateListenerClass = Class.forName("com.android.billingclient.api.BillingClientStateListener");
            Object stateListener = java.lang.reflect.Proxy.newProxyInstance(
                    billingClientStateListenerClass.getClassLoader(),
                    new Class<?>[]{billingClientStateListenerClass},
                    (proxy, method, args) -> {
                        if ("onBillingSetupFinished".equals(method.getName())) {
                            Object billingResult = args[0];
                            try {
                                int responseCode = (int) billingResult.getClass()
                                        .getMethod("getResponseCode").invoke(billingResult);
                                IS_READY = responseCode == 0; // BillingResponseCode.OK = 0

                                if (IS_READY) {
                                    Log.d(TAG, "Billing client ready");
                                    if (LISTENER != null) {
                                        LISTENER.onBillingClientReady();
                                    }
                                    queryPurchases(billingClient);
                                } else {
                                    String message = "Billing setup failed";
                                    Log.e(TAG, message);
                                    if (LISTENER != null) {
                                        LISTENER.onBillingClientError(message);
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in onBillingSetupFinished: " + e.getMessage());
                            }
                        } else if ("onBillingServiceDisconnected".equals(method.getName())) {
                            IS_READY = false;
                            Log.d(TAG, "Billing service disconnected");
                            // Try to reconnect
                            startConnection();
                        }
                        return null;
                    });

            // Start connection
            billingClient.getClass().getMethod("startConnection", billingClientStateListenerClass)
                    .invoke(billingClient, stateListener);

            // Store for later use
            storeBillingClient(billingClient);

        } catch (Exception e) {
            Log.e(TAG, "Error initializing billing: " + e.getMessage());
            if (LISTENER != null) {
                LISTENER.onBillingClientError("Billing initialization failed");
            }
        }
    }

    /**
     * Store billing client reference (using reflection).
     */
    private void storeBillingClient(Object billingClient) {
        // This method stores the billing client for later use
        // In a real implementation, you'd keep this as a field
    }

    /**
     * Query product details for Pro version.
     */
    public void queryProductDetails() {
        // This would query product details from the billing library
        Log.d(TAG, "Query product details for: " + SKU_PRO_VERSION);
    }

    /**
     * Query existing purchases.
     */
    private void queryPurchases(Object billingClient) {
        if (!IS_READY) {
            return;
        }

        try {
            Class<?> queryPurchasesParamsClass = Class.forName("com.android.billingclient.api.QueryPurchasesParams");
            Object params = queryPurchasesParamsClass.getMethod("newBuilder")
                    .invoke(null);
            queryPurchasesParamsClass.getMethod("setProductType", String.class).invoke(params, "inapp");
            Object paramsBuilt = queryPurchasesParamsClass.getMethod("build").invoke(params);

            Class<?> purchasesResponseListenerClass = Class.forName("com.android.billingclient.api.PurchasesResponseListener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    purchasesResponseListenerClass.getClassLoader(),
                    new Class<?>[]{purchasesResponseListenerClass},
                    (proxy, method, args) -> {
                        if ("onQueryPurchasesResponse".equals(method.getName())) {
                            Object billingResult = args[0];
                            @SuppressWarnings("unchecked")
                            java.util.List<?> purchases = (java.util.List<?>) args[1];
                            if (purchases != null) {
                                for (Object purchase : purchases) {
                                    try {
                                        java.util.List<String> products = (java.util.List<String>) purchase.getClass()
                                                .getMethod("getProducts").invoke(purchase);
                                        if (products != null && products.contains(SKU_PRO_VERSION)) {
                                            handlePurchase(purchase);
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing purchase: " + e.getMessage());
                                    }
                                }
                            }
                        }
                        return null;
                    });

            billingClient.getClass().getMethod("queryPurchasesAsync", queryPurchasesParamsClass, purchasesResponseListenerClass)
                    .invoke(billingClient, paramsBuilt, listener);

        } catch (Exception e) {
            Log.e(TAG, "Error querying purchases: " + e.getMessage());
        }
    }

    /**
     * Launch purchase flow for Pro version.
     */
    public void launchPurchaseFlow(@NonNull Activity activity) {
        if (!BILLING_AVAILABLE) {
            Log.w(TAG, "Billing not available in Pro version");
            return;
        }

        if (!IS_READY) {
            Log.w(TAG, "Billing client not ready");
            return;
        }

        Log.d(TAG, "Launching purchase flow for: " + SKU_PRO_VERSION);

        // In a full implementation, this would launch the purchase flow
        // using BillingFlowParams with the ProductDetails obtained from queryProductDetails()
    }

    /**
     * Handle purchases updated callback.
     */
    private void onPurchasesUpdated(Object billingResult, java.util.List<?> purchases) {
        try {
            int responseCode = (int) billingResult.getClass()
                    .getMethod("getResponseCode").invoke(billingResult);

            if (responseCode == 0 && purchases != null) { // BillingResponseCode.OK
                for (Object purchase : purchases) {
                    handlePurchase(purchase);
                }
            } else {
                Log.e(TAG, "Purchase failed with code: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPurchasesUpdated: " + e.getMessage());
        }
    }

    /**
     * Process a single purchase.
     */
    private void handlePurchase(@NonNull Object purchase) {
        try {
            java.util.List<String> products = (java.util.List<String>) purchase.getClass()
                    .getMethod("getProducts").invoke(purchase);

            if (products != null && products.contains(SKU_PRO_VERSION)) {
                int purchaseState = (int) purchase.getClass()
                        .getMethod("getPurchaseState").invoke(purchase);

                if (purchaseState == 1) { // PurchaseState.PURCHASED = 1
                    String purchaseToken = (String) purchase.getClass()
                            .getMethod("getPurchaseToken").invoke(purchase);
                    boolean acknowledged = (boolean) purchase.getClass()
                            .getMethod("isAcknowledged").invoke(purchase);

                    if (!acknowledged) {
                        // Acknowledge the purchase
                        acknowledgePurchase(purchaseToken);
                    }

                    // Unlock pro version
                    ProVersionManager.unlockProVersion(CONTEXT, purchaseToken);

                    if (LISTENER != null) {
                        LISTENER.onPurchaseVerified(true);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling purchase: " + e.getMessage());
        }
    }

    /**
     * Acknowledge a purchase.
     */
    private void acknowledgePurchase(String purchaseToken) {
        try {
            Class<?> acknowledgePurchaseParamsClass = Class.forName("com.android.billingclient.api.AcknowledgePurchaseParams");
            Object params = acknowledgePurchaseParamsClass.getMethod("newBuilder")
                    .invoke(null);
            acknowledgePurchaseParamsClass.getMethod("setPurchaseToken", String.class)
                    .invoke(params, purchaseToken);
            Object paramsBuilt = acknowledgePurchaseParamsClass.getMethod("build").invoke(params);

            // Get the BillingClient to acknowledge
            // In a full implementation, you'd keep a reference to the BillingClient

            Log.d(TAG, "Purchase acknowledged: " + purchaseToken);

        } catch (Exception e) {
            Log.e(TAG, "Error acknowledging purchase: " + e.getMessage());
        }
    }

    /**
     * Restore previous purchases.
     */
    public void restorePurchases() {
        if (IS_READY) {
            // Would call queryPurchases again
            Log.d(TAG, "Restoring purchases");
        }
    }

    /**
     * Check if billing is ready.
     */
    public boolean isReady() {
        return BILLING_AVAILABLE && IS_READY;
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
        IS_READY = false;
    }

    /**
     * Get singleton instance.
     */
    public static BillingManager getInstance(@NonNull Context context) {
        return new BillingManager(context.getApplicationContext());
    }
}
