import UIKit
import WidgetKit
import SoundCloudUi

@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        IosWidgetBridge.shared.reload = {
            WidgetCenter.shared.reloadAllTimelines()
        }
        SoundCloudWidgetCommands.handler = { name in IosWidgetBridge.shared.command(name: name) }
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewController()
        window.makeKeyAndVisible()
        self.window = window
        if let url = launchOptions?[.url] as? URL {
            IosLinks.shared.open(url: url.absoluteString)
        }
        return true
    }

    func applicationWillTerminate(_ application: UIApplication) {
        IosWidgetBridge.shared.markStopped()
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        IosLinks.shared.open(url: url.absoluteString)
        return true
    }

    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        guard let url = userActivity.webpageURL else { return false }
        IosLinks.shared.open(url: url.absoluteString)
        return true
    }
}
