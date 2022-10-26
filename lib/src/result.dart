class BraintreeDropInResult {
  const BraintreeDropInResult({
    required this.paymentMethodNonce,
    required this.deviceData,
  });

  factory BraintreeDropInResult.fromJson(dynamic source) {
    return BraintreeDropInResult(
      paymentMethodNonce:
          BraintreePaymentMethodNonce.fromJson(source['paymentMethodNonce']),
      deviceData: source['deviceData'],
    );
  }

  /// The payment method nonce containing all relevant information for the payment.
  final BraintreePaymentMethodNonce paymentMethodNonce;

  /// String of device data. `null`, if `collectDeviceData` was set to false.
  final String? deviceData;
}

class BraintreePaymentMethodNonce {
  const BraintreePaymentMethodNonce({
    required this.nonce,
    required this.typeLabel,
    required this.description,
    required this.isDefault,
    this.paypalPayerId,
    this.liabilityShifted = false,
    this.liabilityShiftPossible = false
  });

  factory BraintreePaymentMethodNonce.fromJson(dynamic source) {
    print(source);
    return BraintreePaymentMethodNonce(
      nonce: source['nonce'],
      typeLabel: source['typeLabel'],
      description: source['description'],
      isDefault: source['isDefault'],
      paypalPayerId: source['paypalPayerId'],
    );
  }

  /// The nonce generated for this payment method by the Braintree gateway. The nonce will represent
  /// this PaymentMethod for the purposes of creating transactions and other monetary actions.
  final String nonce;

  /// The type of this PaymentMethod for displaying to a customer, e.g. 'Visa'. Can be used for displaying appropriate logos, etc.
  final String typeLabel;

  /// The description of this PaymentMethod for displaying to a customer, e.g. 'Visa ending in...'.
  final String description;

  /// True if this payment method is the default for the current customer, false otherwise.
  final bool isDefault;

  /// PayPal payer id if requesting for paypal nonce
  final String? paypalPayerId;

  /// liabilityShifted indicates that 3D Secure worked and authentication succeeded.
  /// This will also be true if the issuing bank does not support 3D Secure,
  /// but the payment method does. In both cases, the liability for fraud has been shifted to the bank.
  /// You should go on creating a transaction using the new nonce
  final bool? liabilityShifted;

  /// liabilityShiftPossible indicates that the payment method was eligible for 3D Secure.
  /// If liabilityShifted is false, then the user failed 3D Secure authentication.
  /// In this situation, the card brands recommend asking the user for another form of payment.
  /// However, if you have server-side risk assessment processes that allow for it,
  /// you can still use the new nonce to create a transaction.
  /// If you want to use a nonce that did not pass 3D Secure authentication,
  /// you need to set the required option to false in your server integration
  final bool? liabilityShiftPossible;
}
