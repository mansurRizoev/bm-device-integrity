import Foundation
import DeviceCheck
import CryptoKit

/// Thin wrapper over Apple App Attest (DCAppAttestService).
/// Verification of attestation/assertion happens ONLY on the backend.
@objc public class BmDeviceIntegrityPlugin: NSObject {
    private let service = DCAppAttestService.shared

    @objc public var isSupported: Bool { service.isSupported }

    /// SHA-256 of the UTF-8 bytes of `string`. The backend must compute the same hash.
    static func clientDataHash(_ string: String) -> Data {
        Data(SHA256.hash(data: Data(string.utf8)))
    }

    public func generateKey(completion: @escaping (Result<String, Error>) -> Void) {
        service.generateKey { keyId, error in
            if let keyId = keyId { completion(.success(keyId)) } else { completion(.failure(error ?? IntegrityError.unknown)) }
        }
    }

    public func attestKey(keyId: String, challenge: String, completion: @escaping (Result<String, Error>) -> Void) {
        service.attestKey(keyId, clientDataHash: Self.clientDataHash(challenge)) { data, error in
            if let data = data { completion(.success(data.base64EncodedString())) } else { completion(.failure(error ?? IntegrityError.unknown)) }
        }
    }

    public func generateAssertion(keyId: String, clientData: String, completion: @escaping (Result<String, Error>) -> Void) {
        service.generateAssertion(keyId, clientDataHash: Self.clientDataHash(clientData)) { data, error in
            if let data = data { completion(.success(data.base64EncodedString())) } else { completion(.failure(error ?? IntegrityError.unknown)) }
        }
    }

    enum IntegrityError: Error { case unknown }

    /// Stable error codes for JS. `APP_ATTEST_INVALID_KEY` means the key can no longer be used
    /// (already attested, restored from a backup onto another device, removed): the client must
    /// drop the stored `keyId` and generate a new key.
    static func errorCode(_ error: Error) -> String {
        let nsError = error as NSError
        guard nsError.domain == DCErrorDomain, let code = DCError.Code(rawValue: nsError.code) else {
            return "APP_ATTEST_ERROR"
        }
        switch code {
        case .invalidKey: return "APP_ATTEST_INVALID_KEY"
        case .invalidInput: return "APP_ATTEST_INVALID_INPUT"
        case .featureUnsupported: return "APP_ATTEST_UNSUPPORTED"
        case .serverUnavailable: return "APP_ATTEST_SERVER_UNAVAILABLE"
        case .unknownSystemFailure: return "APP_ATTEST_SYSTEM_FAILURE"
        @unknown default: return "APP_ATTEST_ERROR"
        }
    }
}
