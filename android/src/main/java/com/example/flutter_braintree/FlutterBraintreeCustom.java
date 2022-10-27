package com.example.flutter_braintree;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.ThreeDSecure;
import com.braintreepayments.api.dropin.DropInResult;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.ThreeDSecureLookupListener;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.models.CardNonce;
import com.braintreepayments.api.models.GooglePaymentCardNonce;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.braintreepayments.api.models.PayPalRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.models.PayPalAccountNonce;
import com.braintreepayments.api.models.ThreeDSecureLookup;
import com.braintreepayments.api.models.ThreeDSecureRequest;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import org.json.JSONArray;

import java.util.HashMap;

import io.flutter.plugin.common.MethodCall;

public class FlutterBraintreeCustom extends AppCompatActivity implements PaymentMethodNonceCreatedListener, BraintreeCancelListener, BraintreeErrorListener {
    private BraintreeFragment braintreeFragment;
    private String mTotalPrice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            braintreeFragment = BraintreeFragment.newInstance(this, intent.getStringExtra("authorization"));
            braintreeFragment.addListener(this);

            String type = intent.getStringExtra("type");
            if (type.equals("tokenizeCreditCard")) {
                tokenizeCreditCard();
            } else if (type.equals("requestPaypalNonce")) {
                requestPaypalNonce();
            } else if (type.equals("requestGooglePayNonce")) {
                requestGooglePayNonce();
            } else {
                throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            Intent result = new Intent();
            result.putExtra("error", e);
            setResult(2, result);
            finish();
            return;
        }
    }

    protected void tokenizeCreditCard() {
        Intent intent = getIntent();
        CardBuilder builder = new CardBuilder()
                .cardNumber(intent.getStringExtra("cardNumber"))
                .expirationMonth(intent.getStringExtra("expirationMonth"))
                .expirationYear(intent.getStringExtra("expirationYear"))
                .cvv(intent.getStringExtra("cvv"))
                .validate(false)
                .cardholderName(intent.getStringExtra("cardholderName"));
        Card.tokenize(braintreeFragment, builder);
    }

    protected void requestPaypalNonce() {
        Intent intent = getIntent();
        String paypalIntent;
        switch (intent.getStringExtra("payPalPaymentIntent")) {
            case PayPalRequest.INTENT_ORDER:
                paypalIntent = PayPalRequest.INTENT_ORDER;
                break;
            case PayPalRequest.INTENT_SALE:
                paypalIntent = PayPalRequest.INTENT_SALE;
                break;
            default:
                paypalIntent = PayPalRequest.INTENT_AUTHORIZE;
                break;
        }
        String payPalPaymentUserAction = PayPalRequest.USER_ACTION_DEFAULT;
        if (PayPalRequest.USER_ACTION_COMMIT.equals(intent.getStringExtra("payPalPaymentUserAction"))) {
            payPalPaymentUserAction = PayPalRequest.USER_ACTION_COMMIT;
        }
        PayPalRequest request = new PayPalRequest(intent.getStringExtra("amount"))
                .currencyCode(intent.getStringExtra("currencyCode"))
                .displayName(intent.getStringExtra("displayName"))
                .billingAgreementDescription(intent.getStringExtra("billingAgreementDescription"))
                .intent(paypalIntent)
                .userAction(payPalPaymentUserAction);


        if (intent.getStringExtra("amount") == null) {
            // Vault flow
            PayPal.requestBillingAgreement(braintreeFragment, request);
        } else {
            // Checkout flow
            PayPal.requestOneTimePayment(braintreeFragment, request);
        }
    }

    protected void requestGooglePayNonce() {
        Intent intent = getIntent();
        mTotalPrice = intent.getStringExtra("totalPrice");
        String currencyCode = intent.getStringExtra("currencyCode");
        String environment = intent.getStringExtra("environment");
        GooglePaymentRequest googlePaymentRequest = new GooglePaymentRequest();
        googlePaymentRequest
                .billingAddressRequired(intent.getBooleanExtra("billingAddressRequired", false))
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(mTotalPrice)
                        .setCurrencyCode(currencyCode)
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .build())
                .environment(environment);
        if (intent.getStringExtra("googleMerchantID") != null) {
            googlePaymentRequest.googleMerchantId(intent.getStringExtra("googleMerchantID"));
        }
        GooglePayment.requestPayment(braintreeFragment, googlePaymentRequest);
    }

    @Override
    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", paymentMethodNonce.getNonce());
        nonceMap.put("typeLabel", paymentMethodNonce.getTypeLabel());
        nonceMap.put("description", paymentMethodNonce.getDescription());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());

        if (paymentMethodNonce instanceof PayPalAccountNonce) {
            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
        } else if (paymentMethodNonce instanceof GooglePaymentCardNonce) {
            ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest()
                    .amount(mTotalPrice)
                    .nonce(paymentMethodNonce.getNonce())
                    .versionRequested(ThreeDSecureRequest.VERSION_2);

            ThreeDSecure.performVerification(braintreeFragment, threeDSecureRequest, new ThreeDSecureLookupListener() {
                @Override
                public void onLookupComplete(ThreeDSecureRequest threeDSecureRequest, ThreeDSecureLookup threeDSecureLookup) {
                    ThreeDSecure.continuePerformVerification(braintreeFragment, threeDSecureRequest, threeDSecureLookup);
                }
            });
        } else {
            checkLiabilityShifted(paymentMethodNonce);
        }
    }

    /**
     * the function to verify the card is three-D secure or not
     * @param paymentMethodNonce
     */
    private void checkLiabilityShifted(PaymentMethodNonce paymentMethodNonce) {
        CardNonce cardNonce = (CardNonce)paymentMethodNonce;

        boolean liabilityShifted = cardNonce.getThreeDSecureInfo().isLiabilityShifted();
        boolean liabilityShiftPossible = cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible();
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", paymentMethodNonce.getNonce());
        nonceMap.put("typeLabel", paymentMethodNonce.getTypeLabel());
        nonceMap.put("description", paymentMethodNonce.getDescription());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
        nonceMap.put("liabilityShifted", liabilityShifted);
        nonceMap.put("liabilityShiftPossible", liabilityShiftPossible);

        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onCancel(int requestCode) {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onError(Exception error) {
        Intent result = new Intent();
        result.putExtra("error", error);
        setResult(2, result);
        finish();
    }
}
