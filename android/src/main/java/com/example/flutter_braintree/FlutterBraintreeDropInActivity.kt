package com.example.flutter_braintree

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.braintreepayments.api.*
import com.google.android.gms.wallet.TransactionInfo
import com.google.android.gms.wallet.WalletConstants


class FlutterBraintreeDropInActivity : AppCompatActivity(), DropInListener {
    private lateinit var dropInClient: DropInClient
    private lateinit var button: Button

    companion object {
        val TAG = Companion::class.java.simpleName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flutter_braintree_drop_in)

        // DropInClient can also be instantiated with a tokenization key
        val authorization = intent.extras?.getString("token")

        Log.d(TAG, authorization ?: "")

        intent.extras?.apply {
            dropInClient = DropInClient(this@FlutterBraintreeDropInActivity, authorization)
            val dropInRequest = DropInRequest()
            val isMaskCardNumber = getBoolean("maskCardNumber")
            val vaultManagerEnabled = getBoolean("vaultManagerEnabled")
            val venmoEnabled = getBoolean("venmoEnabled")
            val cardEnabled = getBoolean("cardEnabled")
            val paypalEnabled = getBoolean("paypalEnabled")

            Log.d(TAG, "isMaskCardNumber = $isMaskCardNumber")
            Log.d(TAG, "vaultManagerEnabled = $vaultManagerEnabled")
            Log.d(TAG, "venmoEnabled = $venmoEnabled")
            Log.d(TAG, "cardEnabled = $cardEnabled")
            Log.d(TAG, "paypalEnabled = $paypalEnabled")

            dropInRequest.maskCardNumber = isMaskCardNumber
            dropInRequest.isVaultManagerEnabled = vaultManagerEnabled
            dropInRequest.isVenmoDisabled = !venmoEnabled
            dropInRequest.isCardDisabled = !cardEnabled
            dropInRequest.isPayPalDisabled = !paypalEnabled


            val threeDSecureRequest = ThreeDSecureRequest()
            threeDSecureRequest.versionRequested = ThreeDSecureRequest.VERSION_2;
            threeDSecureRequest.amount = getString("amount") as String
            dropInRequest.threeDSecureRequest = threeDSecureRequest

            if (getBoolean("googlePaymentRequest")) {
                readGooglePaymentParameters(dropInRequest)
            } else {
                dropInRequest.isGooglePayDisabled = true
            }

            if (getBoolean("paypalRequest")) {
                readPayPalParameters(dropInRequest)
            } else {
                dropInRequest.isPayPalDisabled = true
            }

            dropInClient.setListener(this@FlutterBraintreeDropInActivity)

            button = findViewById<Button>(R.id.button).apply {
                setOnClickListener {
                    dropInClient.launchDropIn(dropInRequest)
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                button.performClick()
            }, 1000L)

        }
    }

    private fun readGooglePaymentParameters(dropInRequest: DropInRequest) {
        val googlePayRequest = GooglePayRequest().apply {
            isBillingAddressRequired = intent.extras?.getBoolean("billingAddressRequired") ?: false
            transactionInfo = TransactionInfo.newBuilder()
                .setTotalPrice(intent.extras?.getString("totalPrice") ?: "")
                .setCurrencyCode(intent.extras?.getString("currencyCode") ?: "")
                .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                .build()
            environment = intent.extras?.getString("environment")
        }
        dropInRequest.googlePayRequest = googlePayRequest
    }

    private fun readPayPalParameters(dropInRequest: DropInRequest) {
        val amount = intent.extras?.getString("amount")
        amount?.let {
            val paypalRequest = PayPalCheckoutRequest(it)
            paypalRequest.displayName = intent.extras?.getString("displayName")
            paypalRequest.billingAgreementDescription =
                intent.extras?.getString("billingAgreementDescription")
            dropInRequest.payPalRequest = paypalRequest
        }
    }

    /**
     * the function to verify the card is three-D secure or not
     * @param braintreeNonce
     */
    private fun checkLiabilityShifted(dropInResult: DropInResult) {
        val cardNonce = dropInResult.paymentMethodNonce as CardNonce
        val liabilityShifted = cardNonce.threeDSecureInfo.isLiabilityShifted
        val liabilityShiftPossible = cardNonce.threeDSecureInfo.isLiabilityShiftPossible

        val intent = Intent()
        intent.putExtra("nonce", dropInResult.paymentMethodNonce?.string)
        intent.putExtra("isDefault", dropInResult.paymentMethodNonce?.isDefault)
        intent.putExtra("liabilityShifted", liabilityShifted)
        intent.putExtra("liabilityShiftPossible", liabilityShiftPossible)
        intent.putExtra("deviceData", dropInResult.deviceData)

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onDropInSuccess(dropInResult: DropInResult) {
        Log.d(TAG, "onDropInSuccess")
        checkLiabilityShifted(dropInResult)
    }

    override fun onDropInFailure(error: Exception) {
        error.message?.let { Log.d(TAG, it) }
        val intent = Intent()
        intent.putExtra("error", error.message)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}