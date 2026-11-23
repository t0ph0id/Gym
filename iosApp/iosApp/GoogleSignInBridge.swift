import UIKit
import FirebaseCore
import GoogleSignIn
import ComposeApp

/// Google sign-in for the shared code (see IosGoogleSignIn in composeApp's iosMain).
final class GoogleSignInBridge: NSObject, IosGoogleSignIn {

    func signIn(onResult: @escaping (String?, String?, String?) -> Void) {
        // The iOS client ID is in GoogleService-Info.plist once Google sign-in is enabled in Firebase.
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            onResult(nil, nil, "Google sign-in isn't set up for iPhone. Download GoogleService-Info.plist again from Firebase.")
            return
        }
        guard let presenter = Self.topViewController() else {
            onResult(nil, nil, "Google sign-in didn't work. Please try again.")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error = error as NSError? {
                if error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue {
                    onResult(nil, nil, nil) // backed out of the account picker
                } else {
                    onResult(nil, nil, "Google sign-in didn't work. Check your connection and try again.")
                }
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                onResult(nil, nil, "Google sign-in didn't work. Please try again.")
                return
            }
            onResult(idToken, user.accessToken.tokenString, nil)
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
