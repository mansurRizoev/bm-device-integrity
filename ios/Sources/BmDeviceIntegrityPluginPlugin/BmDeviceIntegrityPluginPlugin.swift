import Foundation
import Capacitor

@objc(BmDeviceIntegrityPluginPlugin)
public class BmDeviceIntegrityPluginPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "BmDeviceIntegrityPluginPlugin"
    public let jsName = "BmDeviceIntegrityPlugin"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "isSupported", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestIntegrityToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "prepareStandardIntegrity", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestStandardIntegrityToken", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "generateKey", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "attestKey", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "generateAssertion", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = BmDeviceIntegrityPlugin()

    @objc func isSupported(_ call: CAPPluginCall) {
        let ok = implementation.isSupported
        var result: [String: Any] = ["supported": ok, "platform": "ios"]
        if !ok { result["reason"] = "App Attest is not supported on this device (simulator or unsupported hardware)." }
        call.resolve(result)
    }

    @objc func requestIntegrityToken(_ call: CAPPluginCall) {
        call.unavailable("Play Integrity is only available on Android.")
    }

    @objc func prepareStandardIntegrity(_ call: CAPPluginCall) {
        call.unavailable("Play Integrity is only available on Android.")
    }

    @objc func requestStandardIntegrityToken(_ call: CAPPluginCall) {
        call.unavailable("Play Integrity is only available on Android.")
    }

    @objc func generateKey(_ call: CAPPluginCall) {
        guard implementation.isSupported else { return call.reject("App Attest not supported", "APP_ATTEST_UNSUPPORTED") }
        implementation.generateKey { result in
            switch result {
            case .success(let keyId): call.resolve(["keyId": keyId])
            case .failure(let error): call.reject(error.localizedDescription, BmDeviceIntegrityPlugin.errorCode(error), error)
            }
        }
    }

    @objc func attestKey(_ call: CAPPluginCall) {
        guard let keyId = call.getString("keyId"), !keyId.isEmpty,
              let challenge = call.getString("challenge"), !challenge.isEmpty else {
            return call.reject("keyId and challenge are required", "INVALID_ARGUMENT")
        }
        implementation.attestKey(keyId: keyId, challenge: challenge) { result in
            switch result {
            case .success(let attestation): call.resolve(["attestation": attestation, "keyId": keyId])
            case .failure(let error): call.reject(error.localizedDescription, BmDeviceIntegrityPlugin.errorCode(error), error)
            }
        }
    }

    @objc func generateAssertion(_ call: CAPPluginCall) {
        guard let keyId = call.getString("keyId"), !keyId.isEmpty,
              let clientData = call.getString("clientData"), !clientData.isEmpty else {
            return call.reject("keyId and clientData are required", "INVALID_ARGUMENT")
        }
        implementation.generateAssertion(keyId: keyId, clientData: clientData) { result in
            switch result {
            case .success(let assertion): call.resolve(["assertion": assertion])
            case .failure(let error): call.reject(error.localizedDescription, BmDeviceIntegrityPlugin.errorCode(error), error)
            }
        }
    }
}
