package com.example.flutter_braintree;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.braintreepayments.api.CardNonce;
import com.braintreepayments.api.DropInActivity;
import com.braintreepayments.api.DropInClient;
import com.braintreepayments.api.DropInListener;
import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.braintreepayments.api.UserCanceledException;

import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.util.HashMap;

public class FlutterBraintreeDropIn implements FlutterPlugin, ActivityAware, MethodCallHandler, DropInListener {
  private static final int DROP_IN_REQUEST_CODE = 0x1337;

  private Activity activity;
  private Result activeResult;
  private DropInClient dropInClient;

  /** Plugin registration. */
  @SuppressWarnings("deprecation")
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_braintree.drop_in");
    FlutterBraintreeDropIn dropIn = new FlutterBraintreeDropIn();
    dropIn.activity = registrar.activity();
    channel.setMethodCallHandler(dropIn);
  }

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_braintree.drop_in");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(FlutterPluginBinding binding) {

  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    activity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    activity = binding.getActivity();
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("start")) {
      String clientToken = call.argument("clientToken");
      String tokenizationKey = call.argument("tokenizationKey");
      // DropInClient can also be instantiated with a tokenization key
      if (clientToken != null)
        dropInClient = new DropInClient((FragmentActivity) activity, clientToken);
      else if (tokenizationKey != null)
        dropInClient = new DropInClient((FragmentActivity) activity, tokenizationKey);
      dropInClient.setListener(this);

      DropInRequest dropInRequest = new DropInRequest();
      dropInRequest.setMaskCardNumber((Boolean) call.argument("maskCardNumber"));
      dropInRequest.setVaultManagerEnabled((Boolean) call.argument("vaultManagerEnabled"));
      ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
      threeDSecureRequest.setAmount((String) call.argument("amount"));
      dropInRequest.setThreeDSecureRequest(threeDSecureRequest);

      readGooglePaymentParameters(dropInRequest, call);
      readPayPalParameters(dropInRequest, call);

      if (!((Boolean) call.argument("venmoEnabled")))
        dropInRequest.setVenmoDisabled(true);
      if (!((Boolean) call.argument("cardEnabled")))
        dropInRequest.setCardDisabled(true);
      if (!((Boolean) call.argument("paypalEnabled")))
        dropInRequest.setPayPalDisabled(true);

      if (activeResult != null) {
        result.error("drop_in_already_running", "Cannot launch another Drop-in activity while one is already running.", null);
        return;
      }
      this.activeResult = result;
      dropInClient.launchDropIn(dropInRequest);
    } else {
      result.notImplemented();
    }
  }

  private static void readGooglePaymentParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("googlePaymentRequest");
    if (arg == null) {
      dropInRequest.setGooglePayDisabled(true);
      return;
    }
    String currencyCode = (String) arg.get("currencyCode");
    String environment = (String) arg.get("environment");
    String totalPrice = (String) arg.get("totalPrice");

    GooglePayRequest googlePayRequest = new GooglePayRequest();
    googlePayRequest.setBillingAddressRequired((Boolean) arg.get("billingAddressRequired"));
    googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
                    .setTotalPrice(totalPrice)
                    .setCurrencyCode(currencyCode)
                    .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                    .build());
    googlePayRequest.setEnvironment(environment);
    if (arg.get("googleMerchantID") != null) {
      googlePayRequest.setGoogleMerchantId((String) arg.get("googleMerchantID"));
    }
    dropInRequest.setGooglePayRequest(googlePayRequest);
  }

  private static void readPayPalParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("paypalRequest");
    if (arg == null) {
      dropInRequest.setPayPalDisabled(true);
      return;
    }
    String amount = (String) arg.get("amount");
    if (amount != null) {
      PayPalCheckoutRequest paypalRequest = new PayPalCheckoutRequest(amount);
      paypalRequest.setDisplayName((String) arg.get("displayName"));
      paypalRequest.setBillingAgreementDescription((String) arg.get("billingAgreementDescription"));
      dropInRequest.setPayPalRequest(paypalRequest);
    }
  }

  /**
   * the function to verify the card is three-D secure or not
   * @param braintreeNonce
   */
  private void checkLiabilityShifted(DropInResult dropInResult, PaymentMethodNonce braintreeNonce) {
    CardNonce cardNonce = (CardNonce)braintreeNonce;

    boolean liabilityShifted = cardNonce.getThreeDSecureInfo().isLiabilityShifted();
    boolean liabilityShiftPossible = cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible();
    HashMap<String, Object> result = new HashMap<String, Object>();

    HashMap<String, Object> nonceResult = new HashMap<String, Object>();
    nonceResult.put("nonce", cardNonce.getString());
    nonceResult.put("isDefault", cardNonce.isDefault());
    nonceResult.put("liabilityShifted", liabilityShifted);
    nonceResult.put("liabilityShiftPossible", liabilityShiftPossible);

    result.put("paymentMethodNonce", nonceResult);
    result.put("deviceData", dropInResult.getDeviceData());


    activeResult.success(result);
  }

  @Override
  public void onDropInSuccess(@NonNull DropInResult dropInResult) {
    PaymentMethodNonce paymentMethodNonce = dropInResult.getPaymentMethodNonce();
    checkLiabilityShifted(dropInResult, paymentMethodNonce);
  }

  @Override
  public void onDropInFailure(@NonNull Exception error) {
    if (error instanceof UserCanceledException) {
      // the user canceled
      activeResult.success(null);
    } else {
      // handle error
      activeResult.error("braintree_error", error.getMessage(), null);
    }
  }
}