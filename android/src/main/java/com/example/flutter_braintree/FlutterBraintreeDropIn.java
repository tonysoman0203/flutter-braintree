package com.example.flutter_braintree;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;

import com.braintreepayments.api.DropInClient;

import java.util.HashMap;

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


import androidx.annotation.Nullable;

import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.PayPalRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.io.Serializable;
import java.util.HashMap;

public class FlutterBraintreeDropIn  implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener, Serializable {
  private static final int DROP_IN_REQUEST_CODE = 0x1337;
  public static final int DROP_IN_ERROR_CODE = 0x1335;

  private Activity activity;
  private Result activeResult;

  private DropInClient dropInClient;

  /** Plugin registration. */
  @SuppressWarnings("deprecation")
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "flutter_braintree.drop_in");
    FlutterBraintreeDropIn dropIn = new FlutterBraintreeDropIn();
    dropIn.activity = registrar.activity();
    registrar.addActivityResultListener(dropIn);
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
    binding.addActivityResultListener(this);
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
      ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
      String token = "";
      if (clientToken != null)
        token = clientToken;
      else if (tokenizationKey != null)
        token = tokenizationKey;

      // For best results with 3ds 2.0, provide as many additional elements as possible.
      HashMap<String, String> billingAddress = call.argument("billingAddress");
      if(billingAddress != null){
        ThreeDSecurePostalAddress address = new ThreeDSecurePostalAddress();
        address.setGivenName(billingAddress.get("givenName")); // ASCII-printable characters required, else will throw a validation error
        address.setSurname(billingAddress.get("surname")); // ASCII-printable characters required, else will throw a validation error
        address.setPhoneNumber(billingAddress.get("phoneNumber"));
        address.setStreetAddress(billingAddress.get("streetAddress"));
        address.setExtendedAddress(billingAddress.get("extendedAddress"));
        address.setLocality(billingAddress.get("locality"));
        address.setRegion(billingAddress.get("region"));
        address.setPostalCode(billingAddress.get("postalCode"));
        address.setCountryCodeAlpha2(billingAddress.get("countryCodeAlpha2"));

        ThreeDSecureAdditionalInformation additionalInformation = new ThreeDSecureAdditionalInformation();
        additionalInformation.setShippingAddress(address);
        threeDSecureRequest.setBillingAddress(address);
        threeDSecureRequest.setAdditionalInformation(additionalInformation);
      }



      threeDSecureRequest.setAmount((String) call.argument("amount"));
      String email = call.argument("email");
      if(email != null){
        threeDSecureRequest.setEmail(email);
      }

      threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);


      DropInRequest dropInRequest = new DropInRequest();

      dropInRequest.setVaultManagerEnabled((Boolean) call.argument("vaultManagerEnabled"));
      dropInRequest.setThreeDSecureRequest(threeDSecureRequest);
      dropInRequest.setMaskCardNumber((Boolean) call.argument("maskCardNumber"));


      //.collectDeviceData((Boolean) call.argument("collectDeviceData"))
      // .requestThreeDSecureVerification((Boolean) call.argument("requestThreeDSecureVerification"))

      readGooglePaymentParameters(dropInRequest, call);
//      readPayPalParameters(dropInRequest, call);

      if (!((Boolean) call.argument("venmoEnabled")))
        dropInRequest.setVenmoDisabled(true);
      if (!((Boolean) call.argument("cardEnabled")))
        dropInRequest.setCardDisabled(true);
      if (!((Boolean) call.argument("paypalEnabled")))
        dropInRequest.setPayPalDisabled(true);

      if (this.activeResult != null) {
        result.error("drop_in_already_running", "Cannot launch another Drop-in activity while one is already running.", null);
        return;
      }
      this.activeResult = result;
      Intent intent = new Intent(activity, DropInActivity.class);
      intent.putExtra("token", token);
      intent.putExtra("dropInRequest", dropInRequest);
      this.activity.startActivityForResult(intent, DROP_IN_REQUEST_CODE);
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
    GooglePayRequest googlePaymentRequest = new GooglePayRequest();
    googlePaymentRequest.setTransactionInfo(TransactionInfo.newBuilder()
                    .setTotalPrice((String) arg.get("totalPrice"))
                    .setCurrencyCode((String) arg.get("currencyCode"))
                    .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                    .build());
    googlePaymentRequest.setBillingAddressRequired((Boolean) arg.get("billingAddressRequired"));
//    googlePaymentRequest.setGoogleMerchantId((String) arg.get("merchantID"));
    dropInRequest.setGooglePayRequest(googlePaymentRequest);
  }

//  private static void readPayPalParameters(DropInRequest dropInRequest, MethodCall call) {
//    HashMap<String, Object> arg = call.argument("paypalRequest");
//    if (arg == null) {
//      dropInRequest.setPayPalDisabled(true);
//      return;
//    }
//    String amount = (String) arg.get("amount");
//    PayPalRequest paypalRequest = amount == null ? new PayPalRequest() : new PayPalRequest(amount);
//    paypalRequest.currencyCode((String) arg.get("currencyCode"))
//            .displayName((String) arg.get("displayName"))
//            .billingAgreementDescription((String) arg.get("billingAgreementDescription"));
//    dropInRequest.paypalRequest(paypalRequest);
//  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (this.activeResult == null)
      return false;
    if (requestCode == DROP_IN_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_OK) {
        String nonce = data.getStringExtra("nonce");
        boolean isDefault = data.getBooleanExtra("isDefault", false);
        boolean liabilityShifted = data.getBooleanExtra("liabilityShifted", false);
        boolean liabilityShiftPossible = data.getBooleanExtra("liabilityShiftPossible", false);
        String deviceData = data.getStringExtra("deviceData");

        HashMap<String, Object> result = new HashMap<String, Object>();
        HashMap<String, Object> nonceResult = new HashMap<String, Object>();
        nonceResult.put("nonce", nonce);
        nonceResult.put("isDefault", isDefault);
        nonceResult.put("liabilityShifted", liabilityShifted);
        nonceResult.put("liabilityShiftPossible", liabilityShiftPossible);
        result.put("paymentMethodNonce", nonceResult);
        result.put("deviceData", deviceData);

        activeResult.success(result);

      } else if (resultCode == Activity.RESULT_CANCELED) {
        activeResult.success(null);
      } else {
        Log.d("DropIn", data.getStringExtra("error"));
        activeResult.error("braintree_error", data.getStringExtra("error"), null);
      }
      activeResult = null;
      return true;
    }
    return false;
  }
}