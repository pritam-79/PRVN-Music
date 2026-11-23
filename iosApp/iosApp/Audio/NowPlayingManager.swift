import Foundation
import MediaPlayer
import UIKit

final class NowPlayingManager: NSObject {
    static let shared = NowPlayingManager()

    private override init() {
        super.init()
    }

    func setupRemoteCommands() {
        let commandCenter = MPRemoteCommandCenter.shared()

        commandCenter.playCommand.isEnabled = true
        commandCenter.playCommand.addTarget { _ in
            print("[PRVN NowPlaying] Remote play command received")
            return .success
        }

        commandCenter.pauseCommand.isEnabled = true
        commandCenter.pauseCommand.addTarget { _ in
            print("[PRVN NowPlaying] Remote pause command received")
            return .success
        }

        commandCenter.togglePlayPauseCommand.isEnabled = true
        commandCenter.togglePlayPauseCommand.addTarget { _ in
            print("[PRVN NowPlaying] Remote toggle play/pause received")
            return .success
        }

        commandCenter.nextTrackCommand.isEnabled = true
        commandCenter.nextTrackCommand.addTarget { _ in
            print("[PRVN NowPlaying] Remote next track received")
            return .success
        }

        commandCenter.previousTrackCommand.isEnabled = true
        commandCenter.previousTrackCommand.addTarget { _ in
            print("[PRVN NowPlaying] Remote previous track received")
            return .success
        }

        commandCenter.changePlaybackPositionCommand.isEnabled = true
        commandCenter.changePlaybackPositionCommand.addTarget { event in
            if let positionEvent = event as? MPChangePlaybackPositionCommandEvent {
                print("[PRVN NowPlaying] Remote seek to \(positionEvent.positionTime)s")
                return .success
            }
            return .commandFailed
        }
    }

    func updateNowPlaying(
        title: String,
        artist: String,
        album: String,
        duration: Double,
        currentTime: Double,
        isPlaying: Bool,
        artworkImage: UIImage? = nil
    ) {
        var nowPlayingInfo = [String: Any]()
        nowPlayingInfo[MPMediaItemPropertyTitle] = title
        nowPlayingInfo[MPMediaItemPropertyArtist] = artist
        nowPlayingInfo[MPMediaItemPropertyAlbumTitle] = album
        nowPlayingInfo[MPMediaItemPropertyPlaybackDuration] = duration
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0

        if let image = artworkImage {
            nowPlayingInfo[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }

        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }
}
