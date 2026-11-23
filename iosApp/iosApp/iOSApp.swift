import SwiftUI
import AVFoundation
import MediaPlayer

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea()
                .onAppear {
                    IosAudioSessionManager.shared.configureAudioSession()
                    NowPlayingManager.shared.setupRemoteCommands()
                }
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        IosAudioSessionManager.shared.configureAudioSession()
        return true
    }
}
