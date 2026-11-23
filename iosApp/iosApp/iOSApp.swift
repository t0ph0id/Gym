import SwiftUI
import FirebaseCore
import GoogleSignIn
import ComposeApp

/// The iPhone app is a thin wrapper: all screens and logic are the shared Kotlin code in composeApp.
@main
struct iOSApp: App {
    private let firebaseReady: Bool

    init() {
        // GoogleService-Info.plist is copied in at build time only if it's there (it isn't in git).
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            firebaseReady = true
        } else {
            firebaseReady = false
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView(firebaseReady: firebaseReady)
                .ignoresSafeArea() // the shared UI handles the notch, home bar and keyboard itself
                .onOpenURL { url in _ = GIDSignIn.sharedInstance.handle(url) }
        }
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    let firebaseReady: Bool

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(firebaseReady: firebaseReady, googleSignIn: GoogleSignInBridge())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
