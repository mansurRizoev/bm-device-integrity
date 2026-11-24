import XCTest
import DeviceCheck
@testable import BmDeviceIntegrityPluginPlugin

class BmDeviceIntegrityPluginTests: XCTestCase {
    func testInvalidKeyErrorIsMappedToDedicatedCode() {
        let error = NSError(domain: DCErrorDomain, code: DCError.Code.invalidKey.rawValue)
        XCTAssertEqual(BmDeviceIntegrityPlugin.errorCode(error), "APP_ATTEST_INVALID_KEY")
    }

    func testForeignErrorFallsBackToGenericCode() {
        let error = NSError(domain: NSURLErrorDomain, code: -1)
        XCTAssertEqual(BmDeviceIntegrityPlugin.errorCode(error), "APP_ATTEST_ERROR")
    }

    func testClientDataHashIsSha256OfUtf8() {
        // SHA-256("abc")
        let expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        let hash = BmDeviceIntegrityPlugin.clientDataHash("abc")
        XCTAssertEqual(hash.map { String(format: "%02x", $0) }.joined(), expected)
    }
}
