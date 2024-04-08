package com.example.flutter_braintree;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.braintreepayments.api.BraintreeClient;
import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.GooglePayCardNonce;
import com.braintreepayments.api.GooglePayClient;
import com.braintreepayments.api.GooglePayListener;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalAccountNonce;
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

public class FlutterBraintreeCustom extends AppCompatActivity implements ThreeDSecureListener, GooglePayListener {
    private BraintreeClient mBraintreeClient;
    private ThreeDSecureClient threeDSecureClient;
    private GooglePayClient googlePayClient;
//    private PayPalClient payPalClient;
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

    private void performThreeDSecureValidation(final PaymentMethodNonce paymentMethodNonce) {
        final ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
        threeDSecureRequest.setAmount(mTotalPrice);
        threeDSecureRequest.setNonce(paymentMethodNonce.getString());
        threeDSecureClient.performVerification(this, threeDSecureRequest, (threeDSecureResult, error) -> {
            if (threeDSecureResult != null) {
                threeDSecureClient.continuePerformVerification(FlutterBraintreeCustom.this, threeDSecureRequest, threeDSecureResult);
            } else {
                error.printStackTrace();
                Log.e(TAG, error.getMessage());
                Intent result = new Intent();
                result.putExtra("error", error);
                setResult(2, result);
                finish();
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

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onResume() {
        super.onResume();
    }


//    public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
//        HashMap<String, Object> nonceMap = new HashMap<String, Object>();
//        nonceMap.put("nonce", paymentMethodNonce.getString());
//        nonceMap.put("isDefault", paymentMethodNonce.isDefault());
//        if (paymentMethodNonce instanceof PayPalAccountNonce) {
//            PayPalAccountNonce paypalAccountNonce = (PayPalAccountNonce) paymentMethodNonce;
//            nonceMap.put("paypalPayerId", paypalAccountNonce.getPayerId());
//            nonceMap.put("typeLabel", "PayPal");
//            nonceMap.put("description", paypalAccountNonce.getEmail());
//        }else if(paymentMethodNonce instanceof CardNonce){
//            CardNonce cardNonce = (CardNonce) paymentMethodNonce;
//            nonceMap.put("typeLabel", cardNonce.getCardType());
//            nonceMap.put("description", "ending in ••" + cardNonce.getLastTwo());
//        }
//        Intent result = new Intent();
//        result.putExtra("type", "paymentMethodNonce");
//        result.putExtra("paymentMethodNonce", nonceMap);
//        setResult(RESULT_OK, result);
//        finish();
//    }

    @Override
    public void onGooglePaySuccess(@NonNull PaymentMethodNonce paymentMethodNonce) {
        // send nonce to server
        if (paymentMethodNonce instanceof GooglePayCardNonce) {
            GooglePayCardNonce googlePayCardNonce = (GooglePayCardNonce) paymentMethodNonce;
            Log.d(TAG,"googlePayCardNonce.isNetworkTokenized() " +googlePayCardNonce.isNetworkTokenized());
            if (!googlePayCardNonce.isNetworkTokenized()) {
                performThreeDSecureValidation(paymentMethodNonce);
            } else {
                // skip 3DS2 Secure if Google Pay network tokenized
                HashMap<String, Object> nonceMap = new HashMap<String, Object>();
                nonceMap.put("nonce", paymentMethodNonce.getString());
                nonceMap.put("isDefault", paymentMethodNonce.isDefault());
                Intent result = new Intent();
                result.putExtra("type", "paymentMethodNonce");
                result.putExtra("paymentMethodNonce", nonceMap);
                setResult(RESULT_OK, result);
                finish();
            }
        }
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
}