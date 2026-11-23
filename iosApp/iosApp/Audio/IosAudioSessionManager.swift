import Foundation
import AVFoundation

final class IosAudioSessionManager: NSObject {
    static let shared = IosAudioSessionManager()

    private override init() {
        super.init()
        setupNotificationObservers()
    }

    func configureAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playback,
                mode: .default,
                policy: .longFormAudio,
                options: [.allowAirPlay, .allowBluetooth, .allowBluetoothA2DP]
            )
            try session.setActive(true)
            print("[PRVN Audio] AVAudioSession successfully configured for background playback.")
        } catch {
            print("[PRVN Audio] Failed to configure AVAudioSession: \(error.localizedDescription)")
        }
    }

    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleInterruption),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )

        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleRouteChange),
            name: AVAudioSession.routeChangeNotification,
            object: nil
        )
    }

    @objc private func handleInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }

        switch type {
        case .began:
            print("[PRVN Audio] Audio interruption began (e.g. phone call)")
        case .ended:
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else { return }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) {
                print("[PRVN Audio] Audio interruption ended — resuming playback")
                try? AVAudioSession.sharedInstance().setActive(true)
            }
        @unknown default:
            break
        }
    }

    @objc private func handleRouteChange(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }

        switch reason {
        case .oldDeviceUnavailable:
            print("[PRVN Audio] Headphones/Bluetooth disconnected — pausing playback")
        case .newDeviceAvailable:
            print("[PRVN Audio] New audio output device connected")
        default:
            break
        }
    }
}
