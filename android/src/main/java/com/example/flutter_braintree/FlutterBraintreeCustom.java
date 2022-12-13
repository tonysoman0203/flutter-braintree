package com.example.flutter_braintree;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.CardClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.CardTokenizeCallback;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flutter_braintree_custom);
        try {
            Intent intent = getIntent();
            mBraintreeClient = new BraintreeClient(this, intent.getStringExtra("authorization"));
            threeDSecureClient = new ThreeDSecureClient(this, mBraintreeClient);
            threeDSecureClient.setListener(this);
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
        String currencyCode = intent.getStringExtra("currencyCode");
        String environment = intent.getStringExtra("environment");
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

    private void performThreeDSecureValidation(PaymentMethodNonce cardNonce) {
        final ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.setAmount(mTotalPrice);
        threeDSecureRequest.setNonce(cardNonce.getString());

        threeDSecureClient.performVerification(this, threeDSecureRequest, new ThreeDSecureResultCallback() {
            @Override
            public void onResult(@Nullable ThreeDSecureResult threeDSecureResult, @Nullable Exception error) {
                threeDSecureClient.continuePerformVerification(FlutterBraintreeCustom.this,
                        threeDSecureRequest,
                        threeDSecureResult
                );
            }
        });
    }

    /**
     * the function to verify the card is three-D secure or not
     *
     * @param paymentMethodNonce
     */
    private void checkLiabilityShifted(PaymentMethodNonce paymentMethodNonce) {
        CardNonce cardNonce = (CardNonce) paymentMethodNonce;

        boolean liabilityShifted = cardNonce.getThreeDSecureInfo().isLiabilityShifted();
        boolean liabilityShiftPossible = cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible();
        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
        nonceMap.put("nonce", paymentMethodNonce.getString());
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
    public void onThreeDSecureSuccess(@NonNull ThreeDSecureResult threeDSecureResult) {
        checkLiabilityShifted(threeDSecureResult.getTokenizedCard());
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
}
