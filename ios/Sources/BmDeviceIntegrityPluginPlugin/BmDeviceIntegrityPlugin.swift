import Foundation

@objc public class BmDeviceIntegrityPlugin: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
