package com.gym.app

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.AudioServicesPlaySystemSoundWithCompletion
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.exit
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.time.Clock

/** iPhone versions of the platform services (see [AppPlatform]). */
@OptIn(ExperimentalForeignApi::class)
class IosAppPlatform : AppPlatform {

    // ---- windows ----

    @Suppress("UNCHECKED_CAST")
    private fun windows(): List<UIWindow> = UIApplication.sharedApplication.windows as List<UIWindow>

    /** The screen currently on top, to present pickers and share sheets from. */
    private fun topController(): UIViewController? {
        var vc = (windows().firstOrNull { it.isKeyWindow() } ?: windows().firstOrNull())?.rootViewController
        while (vc?.presentedViewController != null) vc = vc.presentedViewController
        return vc
    }

    // ---- photos: the system photo picker (no permission needed) or the camera ----

    /** Pickers only hold their delegate weakly, so it's kept here while a picker is open. */
    private var photoDelegate: NSObject? = null

    override suspend fun pickPhoto(fromCamera: Boolean): ByteArray? {
        val top = topController() ?: return null
        val image: UIImage? = suspendCancellableCoroutine { cont ->
            if (fromCamera) {
                val source = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                if (!UIImagePickerController.isSourceTypeAvailable(source)) { cont.resume(null); return@suspendCancellableCoroutine }
                val delegate = CameraDelegate(cont) { photoDelegate = null }
                photoDelegate = delegate
                val picker = UIImagePickerController().apply {
                    sourceType = source
                    this.delegate = delegate
                }
                top.presentViewController(picker, animated = true, completion = null)
            } else {
                val config = PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                }
                val delegate = LibraryDelegate(cont) { photoDelegate = null }
                photoDelegate = delegate
                val picker = PHPickerViewController(configuration = config).apply { this.delegate = delegate }
                top.presentViewController(picker, animated = true, completion = null)
            }
        }
        return image?.toSmallJpeg()
    }

    private class CameraDelegate(
        private val cont: CancellableContinuation<UIImage?>,
        private val done: () -> Unit,
    ) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            finish(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, completion = null)
            finish(null)
        }

        private fun finish(image: UIImage?) {
            done()
            if (cont.isActive) cont.resume(image)
        }
    }

    private class LibraryDelegate(
        private val cont: CancellableContinuation<UIImage?>,
        private val done: () -> Unit,
    ) : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val result = didFinishPicking.firstOrNull() as? PHPickerResult
            if (result == null) { finish(null); return }
            // Raw image data (JPEG, HEIC, …); UIImage reads it and keeps the photo's rotation.
            result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                val image = data?.let { UIImage.imageWithData(it) }
                dispatch_async(dispatch_get_main_queue()) { finish(image) }
            }
        }

        private fun finish(image: UIImage?) {
            done()
            if (cont.isActive) cont.resume(image)
        }
    }

    /** Scales to at most 800 px on the longest side (upright) and encodes as JPEG, like on Android. */
    private fun UIImage.toSmallJpeg(maxSide: Double = 800.0): ByteArray? {
        val (w, h) = size.useContents { width to height }
        if (w <= 0.0 || h <= 0.0) return null
        val scale = minOf(1.0, maxSide / maxOf(w, h))
        val (tw, th) = (w * scale) to (h * scale)
        val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(tw, th), format = format)
        val small = renderer.imageWithActions { drawInRect(CGRectMake(0.0, 0.0, tw, th)) }
        return UIImageJPEGRepresentation(small, 0.75)?.toByteArray()
    }

    private fun NSData.toByteArray(): ByteArray {
        val out = ByteArray(length.toInt())
        if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }

    // ---- rest timer: a "Rest over" notification when the app is in the background ----

    private var askedForNotifications = false

    override fun setRestAlarm(endsAt: Long?) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(REST_ID))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(REST_ID))
        if (endsAt == null) return
        // Ask once, the first time a rest starts. While the app is open iOS doesn't show it;
        // the in-app timer buzzes instead.
        if (!askedForNotifications) {
            askedForNotifications = true
            center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
        }
        val seconds = (endsAt - Clock.System.now().toEpochMilliseconds()) / 1000.0
        if (seconds < 1.0) return
        val content = UNMutableNotificationContent().apply {
            setTitle("Rest over")
            setBody("Next set!")
            setSound(UNNotificationSound.defaultSound)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(REST_ID, content, trigger), withCompletionHandler = null)
    }

    override fun vibrate() {
        // Two buzzes, like Android's buzz-buzz.
        AudioServicesPlaySystemSoundWithCompletion(kSystemSoundID_Vibrate) { AudioServicesPlaySystemSound(kSystemSoundID_Vibrate) }
    }

    // ---- screen, sharing ----

    override fun keepScreenOn(on: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = on
    }

    override fun shareText(text: String) {
        val top = topController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        sheet.popoverPresentationController?.sourceView = top.view // needed on iPad
        top.presentViewController(sheet, animated = true, completion = null)
    }

    // ---- text shared into the app (needs a share extension; not there yet) ----

    private val shared = MutableStateFlow<String?>(null)
    override val sharedText: StateFlow<String?> = shared
    override fun consumeSharedText() {
        shared.value = null
    }

    // ---- per-phone settings and light/dark ----

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun loadSetting(key: String): String? = defaults.stringForKey(SETTINGS_PREFIX + key)

    override fun saveSetting(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(SETTINGS_PREFIX + key)
        else defaults.setObject(value, forKey = SETTINGS_PREFIX + key)
    }

    override fun setDarkSystemBars(dark: Boolean) {
        // Matching the window's style gives the right status bar, and share sheets and pickers to match.
        val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        windows().forEach { it.overrideUserInterfaceStyle = style }
    }

    // ---- sign-out ----

    @OptIn(BetaInteropApi::class)
    override suspend fun clearLocalDataAndRestart() {
        // As on Android, the offline cache can only be cleared on a stopped Firestore, which can't be
        // restarted in the same process. iOS apps can't relaunch themselves, so it simply closes;
        // opening it again shows the sign-in screen.
        runCatching {
            Firebase.firestore.terminate()
            Firebase.firestore.clearPersistence()
        }
        exit(0)
    }

    private companion object {
        const val REST_ID = "rest"
        const val SETTINGS_PREFIX = "gym_"
    }
}
