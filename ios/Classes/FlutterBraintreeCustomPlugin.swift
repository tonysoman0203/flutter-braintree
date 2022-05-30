import Flutter
import UIKit
import Braintree
import BraintreeDropIn

public class FlutterBraintreeCustomPlugin: BaseFlutterBraintreePlugin, FlutterPlugin, BTViewControllerPresentingDelegate {
    
    private var completionBlock: FlutterResult!
    private var authorization: String?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_braintree.custom", binaryMessenger: registrar.messenger())
        
        let instance = FlutterBraintreeCustomPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        guard !isHandlingResult else {
            returnAlreadyOpenError(result: result)
            return
        }
        
        isHandlingResult = true
        
        authorization = getAuthorization(call: call)
        
        guard authorization != nil else {
            returnAuthorizationMissingError(result: result)
            isHandlingResult = false
            return
        }
        
        if call.method == "requestApplePayNonce" {
            guard let request = dict(for: "request", in: call) else {
                isHandlingResult = false
                return
            }
            
            setupApplePay(request: request, flutterResult: result)
        } else if call.method == "requestPaypalNonce" {
            let client = BTAPIClient(authorization: authorization!)
            let driver = BTPayPalDriver(apiClient: client!)
            
            guard let requestInfo = dict(for: "request", in: call) else {
                isHandlingResult = false
                return
            }
            
            if let amount = requestInfo["amount"] as? String {
                let paypalRequest = BTPayPalCheckoutRequest(amount: amount)
                paypalRequest.currencyCode = requestInfo["currencyCode"] as? String
                paypalRequest.displayName = requestInfo["displayName"] as? String
                paypalRequest.billingAgreementDescription = requestInfo["billingAgreementDescription"] as? String
                if let intent = requestInfo["payPalPaymentIntent"] as? String {
                    switch intent {
                    case "order":
                        paypalRequest.intent = BTPayPalRequestIntent.order
                    case "sale":
                        paypalRequest.intent = BTPayPalRequestIntent.sale
                    default:
                        paypalRequest.intent = BTPayPalRequestIntent.authorize
                    }
                }
                if let userAction = requestInfo["payPalPaymentUserAction"] as? String {
                    switch userAction {
                    case "commit":
                        paypalRequest.userAction = BTPayPalRequestUserAction.commit
                    default:
                        paypalRequest.userAction = BTPayPalRequestUserAction.default
                    }
                }
                driver.tokenizePayPalAccount(with: paypalRequest) { (nonce, error) in
                    self.handleResult(nonce: nonce, error: error, flutterResult: result)
                    self.isHandlingResult = false
                }
            } else {
                let paypalRequest = BTPayPalVaultRequest()
                paypalRequest.displayName = requestInfo["displayName"] as? String
                paypalRequest.billingAgreementDescription = requestInfo["billingAgreementDescription"] as? String
                
                driver.tokenizePayPalAccount(with: paypalRequest) { (nonce, error) in
                    self.handleResult(nonce: nonce, error: error, flutterResult: result)
                    self.isHandlingResult = false
                }
            }
            
        } else if call.method == "tokenizeCreditCard" {
            let client = BTAPIClient(authorization: authorization!)
            let cardClient = BTCardClient(apiClient: client!)
            
            guard let cardRequestInfo = dict(for: "request", in: call) else {return}
            
            let card = BTCard()
            card.number = cardRequestInfo["cardNumber"] as? String
            card.expirationMonth = cardRequestInfo["expirationMonth"] as? String
            card.expirationYear = cardRequestInfo["expirationYear"] as? String
            card.cvv = cardRequestInfo["cvv"] as? String
            card.cardholderName = cardRequestInfo["cardholderName"] as? String
            
            cardClient.tokenizeCard(card) { (nonce, error) in
                self.handleResult(nonce: nonce, error: error, flutterResult: result)
                self.isHandlingResult = false
            }
        } else {
            result(FlutterMethodNotImplemented)
            self.isHandlingResult = false
        }
    }
    
    private func handleResult(nonce: BTPaymentMethodNonce?, error: Error?, flutterResult: FlutterResult) {
        if error != nil {
            returnBraintreeError(result: flutterResult, error: error!)
        } else if nonce == nil {
            flutterResult(nil)
        } else {
            flutterResult(buildPaymentNonceDict(nonce: nonce));
        }
    }
    
    public func paymentDriver(_ driver: Any, requestsPresentationOf viewController: UIViewController) {
        
    }
    
    public func paymentDriver(_ driver: Any, requestsDismissalOf viewController: UIViewController) {
        
    }
    
    private func handleApplePayResult(_ result: BTPaymentMethodNonce, flutterResult: FlutterResult) {
        flutterResult(["paymentMethodNonce": buildPaymentNonceDict(nonce: result)])
    }
    
    private func setupApplePay(request: [String: Any], flutterResult: FlutterResult) {
        let paymentRequest = PKPaymentRequest()
        paymentRequest.supportedNetworks = [.visa, .masterCard, .amex, .discover]
        paymentRequest.merchantCapabilities = .capability3DS
        paymentRequest.countryCode = request["countryCode"] as! String
        paymentRequest.currencyCode = request["currencyCode"] as! String
        paymentRequest.merchantIdentifier = request["merchantIdentifier"] as! String
        
        guard let paymentSummaryItems = makePaymentSummaryItems(from: request) else {
            return;
        }
        paymentRequest.paymentSummaryItems = paymentSummaryItems;

        guard let applePayController = PKPaymentAuthorizationViewController(paymentRequest: paymentRequest) else {
            return
        }
        
        applePayController.delegate = self
        
        UIApplication.shared.keyWindow?.rootViewController?.present(applePayController, animated: true, completion: nil)
    }
}

// MARK: PKPaymentAuthorizationViewControllerDelegate
extension FlutterBraintreeCustomPlugin: PKPaymentAuthorizationViewControllerDelegate {
    public func paymentAuthorizationViewControllerDidFinish(_ controller: PKPaymentAuthorizationViewController) {
        controller.dismiss(animated: true, completion: nil)
    }
    
    @available(iOS 11.0, *)
    public func paymentAuthorizationViewController(_ controller: PKPaymentAuthorizationViewController, didAuthorizePayment payment: PKPayment, handler completion: @escaping (PKPaymentAuthorizationResult) -> Void) {
        guard let apiClient = BTAPIClient(authorization: authorization!) else { return }
        let applePayClient = BTApplePayClient(apiClient: apiClient)
        
        applePayClient.tokenizeApplePay(payment) { (tokenizedPaymentMethod, error) in
            guard let paymentMethod = tokenizedPaymentMethod, error == nil else {
                completion(PKPaymentAuthorizationResult(status: .failure, errors: nil))
                return
            }
            
            print(paymentMethod.nonce)
            self.handleApplePayResult(paymentMethod, flutterResult: self.completionBlock)
            completion(PKPaymentAuthorizationResult(status: .success, errors: nil))
        }
    }

    public func paymentAuthorizationViewController(_ controller: PKPaymentAuthorizationViewController, didAuthorizePayment payment: PKPayment, completion: @escaping (PKPaymentAuthorizationStatus) -> Void) {
        guard let apiClient = BTAPIClient(authorization: authorization!) else { return }
        let applePayClient = BTApplePayClient(apiClient: apiClient)
        
        applePayClient.tokenizeApplePay(payment) { (tokenizedPaymentMethod, error) in
            guard let paymentMethod = tokenizedPaymentMethod, error == nil else {
                completion(.failure)
                return
            }
            
            print(paymentMethod.nonce)
            self.handleApplePayResult(paymentMethod, flutterResult: self.completionBlock)
            completion(.success)
        }
    }
}
