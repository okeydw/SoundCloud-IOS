import Foundation
import AppIntents

public enum SoundCloudWidgetCommands {
    public static var handler: ((String) -> Void)?

    static func run(_ name: String) async {
        await MainActor.run { handler?(name) }
    }
}

@available(iOS 17.0, *)
struct SoundCloudToggleIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Play / Pause"
    static let isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        await SoundCloudWidgetCommands.run("toggle")
        return .result()
    }
}

@available(iOS 17.0, *)
struct SoundCloudNextIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Next track"
    static let isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        await SoundCloudWidgetCommands.run("next")
        return .result()
    }
}

@available(iOS 17.0, *)
struct SoundCloudPreviousIntent: AudioPlaybackIntent {
    static let title: LocalizedStringResource = "Previous track"
    static let isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        await SoundCloudWidgetCommands.run("previous")
        return .result()
    }
}
