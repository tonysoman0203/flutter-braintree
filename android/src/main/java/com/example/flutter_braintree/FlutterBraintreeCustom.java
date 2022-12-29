package com.example.flutter_braintree;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.CardClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.CardTokenizeCallback;
import com.braintreepayments.api.GooglePayCardNonce;
import com.braintreepayments.api.GooglePayClient;
import com.braintreepayments.api.GooglePayListener;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalAccountNonce;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.PayPalClient;
import com.braintreepayments.api.PayPalListener;
import com.braintreepayments.api.PayPalPaymentIntent;
import com.braintreepayments.api.PayPalVaultRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureClient;
import com.braintreepayments.api.ThreeDSecureListener;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.ThreeDSecureResult;
import com.braintreepayments.api.ThreeDSecureResultCallback;
import com.braintreepayments.api.UserCanceledException;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;


import java.util.HashMap;


public class FlutterBraintreeCustom extends AppCompatActivity implements PayPalListener, ThreeDSecureListener, GooglePayListener {
    private BraintreeClient mBraintreeClient;
    private ThreeDSecureClient threeDSecureClient;
    private GooglePayClient googlePayClient;
    private PayPalClient payPalClient;
    private String mTotalPrice;
    private static final String TAG = FlutterBraintreeCustom.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            String authorization = intent.getStringExtra("authorization");
            Log.d(TAG, "authorization = "+authorization);
            if (authorization == null) {
                throw new Exception("Missing Authorization");
            }
            mBraintreeClient = new BraintreeClient(this, authorization);
            threeDSecureClient = new ThreeDSecureClient(this, mBraintreeClient);
            threeDSecureClient.setListener(this);
            String type = intent.getStringExtra("type");
            Log.d(TAG, "type = "+type);
            switch (type) {
                case "tokenizeCreditCard":
                    tokenizeCreditCard();
                    break;
                case "requestPaypalNonce":
                    requestPaypalNonce();
                    break;
                case "requestGooglePayNonce":
                    requestGooglePayNonce();
                    break;
                default:
                    throw new Exception("Invalid request type: " + type);
            }
        } catch (Exception e) {
            Intent result = new Intent();
            result.putExtra("error", e);
            setResult(2, result);
            finish();
        }
    }

    protected void tokenizeCreditCard() {
        Intent intent = getIntent();
        Card card = new Card();
        card.setNumber(intent.getStringExtra("cardNumber"));
        card.setExpirationMonth(intent.getStringExtra("expirationMonth"));
        card.setExpirationYear(intent.getStringExtra("expirationYear"));
        card.setCvv(intent.getStringExtra("cvv"));
        card.setShouldValidate(false);
        card.setCardholderName(intent.getStringExtra("cardholderName"));

        CardClient cardClient = new CardClient(new BraintreeClient(this, intent.getStringExtra("authorization")));
        cardClient.tokenize(card, new CardTokenizeCallback() {
            @Override
            public void onResult(@Nullable com.braintreepayments.api.CardNonce cardNonce, @Nullable Exception error) {

            }
        });
    }

    protected void requestPaypalNonce() {
        payPalClient = new PayPalClient(this, mBraintreeClient);
        payPalClient.setListener(this);

        Intent intent = getIntent();
        String paypalIntent;
        switch (intent.getStringExtra("payPalPaymentIntent")) {
            case PayPalPaymentIntent.ORDER:
                paypalIntent = PayPalPaymentIntent.ORDER;
                break;
            case PayPalPaymentIntent.SALE:
                paypalIntent = PayPalPaymentIntent.SALE;
                break;
            default:
                paypalIntent = PayPalPaymentIntent.AUTHORIZE;
                break;
        }

        if (intent.getStringExtra("amount") == null) {
            // Vault flow
            PayPalVaultRequest payPalVaultRequest = new PayPalVaultRequest();
            payPalVaultRequest.setBillingAgreementDescription(intent.getStringExtra("billingAgreementDescription"));

            payPalClient.tokenizePayPalAccount(this, payPalVaultRequest);
        } else {
            // Checkout flow
            PayPalCheckoutRequest payPalCheckoutRequest = new PayPalCheckoutRequest(intent.getStringExtra("amount"));
            payPalCheckoutRequest.setCurrencyCode(intent.getStringExtra("currencyCode"));
            payPalCheckoutRequest.setIntent(paypalIntent);

            payPalClient.tokenizePayPalAccount(this, payPalCheckoutRequest);
        }
    }

    protected void requestGooglePayNonce() {
        Intent intent = getIntent();
        googlePayClient = new GooglePayClient(this, mBraintreeClient);
        googlePayClient.setListener(this);

        mTotalPrice = intent.getStringExtra("totalPrice");
        Log.d(TAG, "mTotalPrice = "+mTotalPrice);
        if (mTotalPrice == null) return;

        String currencyCode = intent.getStringExtra("currencyCode");
        Log.d(TAG, "currencyCode = "+currencyCode);
        if (currencyCode == null) return;

        String environment = intent.getStringExtra("environment");
        Log.d(TAG, "environment = "+environment);

        GooglePayRequest googlePayRequest = new GooglePayRequest();
        googlePayRequest.setBillingAddressRequired(intent.getBooleanExtra("billingAddressRequired", false));
        googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(mTotalPrice)
                        .setCurrencyCode(currencyCode)
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .build());
        googlePayRequest.setEnvironment(environment);
        googlePayClient.requestPayment(this, googlePayRequest);
    }

    @Override
    protected void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        // required if your activity's launch mode is "singleTop", "singleTask", or "singleInstance"
        setIntent(newIntent);
    }

    @Override
    public void onPayPalSuccess(@NonNull PayPalAccountNonce payPalAccountNonce) {
        // send nonce to server
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", payPalAccountNonce.getString());
        nonceMap.put("isDefault", payPalAccountNonce.isDefault());
        nonceMap.put("paypalPayerId", payPalAccountNonce.getPayerId());
        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onPayPalFailure(@NonNull Exception error) {
        if (error instanceof UserCanceledException) {
            // user canceled
            setResult(RESULT_CANCELED);
            finish();
        } else {
            // handle error
            Intent result = new Intent();
            result.putExtra("error", error);
            setResult(2, result);
            finish();
        }
    }

    @Override
    public void onGooglePaySuccess(@NonNull PaymentMethodNonce paymentMethodNonce) {
        // send nonce to server
        performThreeDSecureValidation(paymentMethodNonce);
    }

    @Override
    public void onGooglePayFailure(@NonNull Exception error) {
        if (error instanceof UserCanceledException) {
            // user canceled
            setResult(RESULT_CANCELED);
            finish();
        } else {
            // handle error
            Intent result = new Intent();
            result.putExtra("error", error);
            setResult(2, result);
            finish();
        }
    }

    @Override
    public void onThreeDSecureSuccess(@NonNull ThreeDSecureResult threeDSecureResult) {
        if (threeDSecureResult.getTokenizedCard() != null) {
            checkLiabilityShifted(threeDSecureResult.getTokenizedCard());
        }
    }

    @Override
    public void onThreeDSecureFailure(@NonNull Exception error) {
        if (error instanceof UserCanceledException) {
            // user canceled
            setResult(RESULT_CANCELED);
            finish();
        } else {
            // handle error
            Intent result = new Intent();
            result.putExtra("error", error);
            setResult(2, result);
            finish();
        }
    }

    private void performThreeDSecureValidation(final PaymentMethodNonce cardNonce) {
        final ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.setAmount(mTotalPrice);
        threeDSecureRequest.setNonce(cardNonce.getString());
        threeDSecureClient.performVerification(this, threeDSecureRequest, new ThreeDSecureResultCallback() {
            @Override
            public void onResult(@Nullable ThreeDSecureResult threeDSecureResult, @Nullable Exception error) {
                if (threeDSecureResult != null) {
                    // examine lookup response (if necessary), then continue verification
                    threeDSecureClient.continuePerformVerification(FlutterBraintreeCustom.this,
                            threeDSecureRequest,
                            threeDSecureResult
                    );
                } else {
                    // handle error
                    error.printStackTrace();
                    Log.e("onResult", error.getMessage());
                }
            }
        });
    }

    /**
     * the function to verify the card is three-D secure or not
     *
     * @param paymentMethodNonce
     */
    private void checkLiabilityShifted(PaymentMethodNonce paymentMethodNonce) {
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        boolean liabilityShifted;
        boolean liabilityShiftPossible;
        if (paymentMethodNonce instanceof CardNonce) {
            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
            liabilityShifted = cardNonce.getThreeDSecureInfo().isLiabilityShifted();
            liabilityShiftPossible = cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible();
            nonceMap.put("liabilityShifted", liabilityShifted);
            nonceMap.put("liabilityShiftPossible", liabilityShiftPossible);
        }

        nonceMap.put("nonce", paymentMethodNonce.getString());
        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
        Intent result = new Intent();
        result.putExtra("type", "paymentMethodNonce");
        result.putExtra("paymentMethodNonce", nonceMap);
        setResult(RESULT_OK, result);
        finish();
    }
}
