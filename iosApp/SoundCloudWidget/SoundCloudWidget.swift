import SwiftUI
import AppIntents
import UIKit
import WidgetKit


struct NowPlayingEntry: TimelineEntry {
    let date: Date
    let title: String
    let artist: String
    let playing: Bool
    let art: UIImage?
    let accent: Color
    let idle: String
    let open: String

    var hasTrack: Bool { !title.isEmpty }

    static let placeholder = NowPlayingEntry(
        date: Date(), title: "SoundCloud", artist: " ", playing: false, art: nil,
        accent: Color(red: 1, green: 0.33, blue: 0), idle: "", open: ""
    )
}

enum SharedStore {
    static var group: String {
        let host = Bundle(url: Bundle.main.bundleURL.deletingLastPathComponent().deletingLastPathComponent())
        for bundle in [Bundle.main, host].compactMap({ $0 }) {
            if let alt = (bundle.object(forInfoDictionaryKey: "ALTAppGroups") as? [String])?.first { return alt }
        }
        for bundle in [Bundle.main, host].compactMap({ $0 }) {
            if let own = bundle.object(forInfoDictionaryKey: "SoundCloudAppGroup") as? String { return own }
        }
        return "group.com.scd.ios"
    }

    static func load(artSide: CGFloat) -> NowPlayingEntry {
        let d = UserDefaults(suiteName: group)
        let title = d?.string(forKey: "title") ?? ""
        var image: UIImage?
        if let name = d?.string(forKey: "art"), !name.isEmpty,
           let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) {
            let path = dir.appendingPathComponent(name).path
            image = UIImage(contentsOfFile: path)?.preparingThumbnail(of: CGSize(width: artSide, height: artSide))
        }
        let argb = UInt32(truncatingIfNeeded: d?.integer(forKey: "accent") ?? 0)
        let accent = argb == 0
            ? Color(red: 1, green: 0.33, blue: 0)
            : Color(red: Double((argb >> 16) & 0xFF) / 255, green: Double((argb >> 8) & 0xFF) / 255, blue: Double(argb & 0xFF) / 255)
        return NowPlayingEntry(
            date: Date(),
            title: title,
            artist: d?.string(forKey: "artist") ?? "",
            playing: d?.bool(forKey: "playing") ?? false,
            art: image,
            accent: accent,
            idle: d?.string(forKey: "idle") ?? NSLocalizedString("widget_idle", comment: ""),
            open: d?.string(forKey: "open") ?? NSLocalizedString("widget_open", comment: "")
        )
    }
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> NowPlayingEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (NowPlayingEntry) -> Void) {
        completion(SharedStore.load(artSide: side(context.family)))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<NowPlayingEntry>) -> Void) {
        completion(Timeline(entries: [SharedStore.load(artSide: side(context.family))], policy: .never))
    }

    private func side(_ family: WidgetFamily) -> CGFloat {
        switch family {
        case .systemLarge: return 600
        case .systemMedium: return 320
        default: return 200
        }
    }
}


private let bgTop = Color(red: 0.125, green: 0.125, blue: 0.153)
private let bgBottom = Color(red: 0.071, green: 0.071, blue: 0.086)

private extension View {
    @ViewBuilder func widgetChrome() -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(for: .widget) {
                LinearGradient(colors: [bgTop, bgBottom], startPoint: .top, endPoint: .bottom)
            }
        } else {
            padding(14).background(LinearGradient(colors: [bgTop, bgBottom], startPoint: .top, endPoint: .bottom))
        }
    }
}

private func onAccentColor(_ accent: Color) -> Color {
    var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
    UIColor(accent).getRed(&r, green: &g, blue: &b, alpha: &a)
    return (0.299 * r + 0.587 * g + 0.114 * b) > 0.62 ? .black : .white
}

struct ArtView: View {
    let image: UIImage?
    let corner: CGFloat

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: corner, style: .continuous).fill(Color.white.opacity(0.12))
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "music.note").font(.system(size: 22, weight: .semibold)).foregroundColor(.white.opacity(0.7))
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
    }
}

struct ControlButton: View {
    enum Kind { case toggle, next, previous }

    let kind: Kind
    let playing: Bool
    let size: CGFloat
    let accent: Color

    private var symbol: String {
        switch kind {
        case .toggle: return playing ? "pause.fill" : "play.fill"
        case .next: return "forward.fill"
        case .previous: return "backward.fill"
        }
    }

    private var label: some View {
        let primary = kind == .toggle
        return Image(systemName: symbol)
            .font(.system(size: size * (primary ? 0.42 : 0.36), weight: .bold))
            .foregroundColor(primary ? onAccentColor(accent) : .white)
            .frame(width: size, height: size)
            .background(Circle().fill(primary ? accent : Color.white.opacity(0.14)))
            .contentShape(Circle())
    }

    var body: some View {
        if #available(iOS 17.0, *) {
            switch kind {
            case .toggle: Button(intent: SoundCloudToggleIntent()) { label }.buttonStyle(.plain)
            case .next: Button(intent: SoundCloudNextIntent()) { label }.buttonStyle(.plain)
            case .previous: Button(intent: SoundCloudPreviousIntent()) { label }.buttonStyle(.plain)
            }
        } else {
            let name: String = kind == .toggle ? "toggle" : (kind == .next ? "next" : "previous")
            Link(destination: URL(string: "scd://widget/\(name)")!) { label }
        }
    }
}

struct TitleBlock: View {
    let entry: NowPlayingEntry
    let titleSize: CGFloat
    let lines: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(entry.hasTrack ? entry.title : entry.idle)
                .font(.system(size: titleSize, weight: .bold))
                .foregroundColor(.white)
                .lineLimit(lines)
                .minimumScaleFactor(0.85)
            Text(entry.hasTrack ? entry.artist : entry.open)
                .font(.system(size: titleSize - 2.5))
                .foregroundColor(.white.opacity(0.7))
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
    }
}


struct SmallView: View {
    let entry: NowPlayingEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                ArtView(image: entry.hasTrack ? entry.art : nil, corner: 12).frame(width: 56, height: 56)
                Spacer(minLength: 4)
                if entry.hasTrack {
                    ControlButton(kind: .toggle, playing: entry.playing, size: 40, accent: entry.accent)
                }
            }
            Spacer(minLength: 6)
            TitleBlock(entry: entry, titleSize: 14, lines: 2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

struct MediumView: View {
    let entry: NowPlayingEntry

    var body: some View {
        HStack(spacing: 14) {
            GeometryReader { geo in
                ArtView(image: entry.hasTrack ? entry.art : nil, corner: 16)
                    .frame(width: geo.size.height, height: geo.size.height)
            }
            .aspectRatio(1, contentMode: .fit)
            VStack(alignment: .leading, spacing: 0) {
                TitleBlock(entry: entry, titleSize: 16, lines: 2)
                Spacer(minLength: 6)
                if entry.hasTrack {
                    HStack(spacing: 12) {
                        ControlButton(kind: .previous, playing: entry.playing, size: 36, accent: entry.accent)
                        ControlButton(kind: .toggle, playing: entry.playing, size: 44, accent: entry.accent)
                        ControlButton(kind: .next, playing: entry.playing, size: 36, accent: entry.accent)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

struct LargeView: View {
    let entry: NowPlayingEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            GeometryReader { geo in
                let side = min(geo.size.width, geo.size.height)
                ArtView(image: entry.hasTrack ? entry.art : nil, corner: 20)
                    .frame(width: side, height: side)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            TitleBlock(entry: entry, titleSize: 18, lines: 2)
            if entry.hasTrack {
                HStack(spacing: 22) {
                    Spacer(minLength: 0)
                    ControlButton(kind: .previous, playing: entry.playing, size: 44, accent: entry.accent)
                    ControlButton(kind: .toggle, playing: entry.playing, size: 56, accent: entry.accent)
                    ControlButton(kind: .next, playing: entry.playing, size: 44, accent: entry.accent)
                    Spacer(minLength: 0)
                }
            }
        }
    }
}

struct SoundCloudWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: NowPlayingEntry

    var body: some View {
        Group {
            switch family {
            case .systemMedium: MediumView(entry: entry)
            case .systemLarge: LargeView(entry: entry)
            default: SmallView(entry: entry)
            }
        }
        .widgetChrome()
        .widgetURL(URL(string: "scd://widget/open"))
    }
}

struct SoundCloudPlayerWidget: Widget {
    let kind = "SoundCloudPlayer"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            SoundCloudWidgetView(entry: entry)
        }
        .configurationDisplayName("widget_name")
        .description("widget_desc")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

@main
struct SoundCloudWidgetBundle: WidgetBundle {
    var body: some Widget {
        SoundCloudPlayerWidget()
    }
}
