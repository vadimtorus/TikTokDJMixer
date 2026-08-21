# TikTok DJ Mixer

Professional DJ mixing application for Android with live streaming to TikTok.

## Features

### DJ Controls
- **Dual Deck System** - Two independent decks with full playback control
- **Crossfader** - Smooth crossfade between tracks
- **3-Band EQ** - Low, Mid, High frequency controls for each deck
- **BPM Detection** - Automatic BPM analysis and display
- **BPM Sync** - Synchronize tempo between decks
- **Hot Cues** - Set and jump to cue points
- **Pitch Control** - Adjust playback speed (0.8x - 1.2x)

### Audio Effects
- Echo, Reverb, Delay
- Flanger, Phaser
- Low-pass / High-pass Filters
- Distortion, Bitcrusher
- X/Y modulation pad

### Spectral Analysis
- Real-time FFT spectrum analyzer
- 32-band frequency visualization
- Left/Right channel display

### Live Streaming
- **TikTok Live API** - Direct integration with TikTok Live
- **RTMP Streaming** - Manual RTMP server configuration
- Configurable resolution (480p/720p/1080p)
- Adjustable bitrate (500-6000 kbps)
- Microphone input support

### Auto-Update System
- GitHub Releases integration
- Periodic update checks
- In-app update notifications
- One-tap APK installation

## Installation

### From GitHub Releases
1. Go to [Releases](../../releases)
2. Download the latest APK
3. Enable "Install from unknown sources"
4. Install and open the app

### Build from Source
```bash
# Clone repository
git clone https://github.com/your-username/TikTokDJMixer.git

# Navigate to project
cd TikTokDJMixer

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

## Configuration

### TikTok Live API
1. Create a TikTok Developer account
2. Create an app at [developers.tiktok.com](https://developers.tiktok.com)
3. Get Client Key and Client Secret
4. Enter credentials in the Stream settings

### RTMP Streaming
1. Set up an RTMP server (e.g., nginx-rtmp)
2. Configure your stream key
3. Enter RTMP URL in format: `rtmp://server/live/stream_key`

## Project Structure

```
TikTokDJMixer/
├── app/src/main/java/com/tiktokdj/mixer/
│   ├── engine/           # Core DJ engine
│   │   ├── DeckPlayer.kt        # Individual deck playback
│   │   ├── MixerEngine.kt       # Main mixer logic
│   │   ├── BPMDetector.kt       # BPM detection algorithm
│   │   ├── SpectralAnalyzer.kt  # FFT spectrum analysis
│   │   └── EffectsProcessor.kt  # Audio effects
│   ├── audio/            # Audio utilities
│   │   └── TrackLoader.kt       # Device music library
│   ├── streaming/        # Streaming engines
│   │   ├── TikTokLiveStreamer.kt # TikTok Live API
│   │   ├── RTMPStreamer.kt      # RTMP protocol
│   │   └── StreamManager.kt     # Stream orchestration
│   ├── ui/               # Jetpack Compose UI
│   │   ├── MainActivity.kt
│   │   ├── DJMixerApp.kt
│   │   ├── deck/         # Deck components
│   │   ├── mixer/        # Mixer controls
│   │   ├── effects/      # Effects panel
│   │   ├── stream/       # Stream settings
│   │   └── theme/        # UI theme
│   ├── model/            # Data models
│   │   └── Models.kt
│   ├── service/          # Android services
│   │   └── StreamingService.kt
│   ├── updater/          # Auto-update system
│   │   └── AppUpdater.kt
│   └── utils/            # Utilities
│       └── Utils.kt
├── .github/workflows/    # CI/CD
│   ├── android.yml       # Build & Release
│   └── update-check.yml  # Auto update check
└── build.gradle.kts
```

## Versioning

This project follows Semantic Versioning (MAJOR.MINOR.PATCH):

- **Major** - Breaking changes
- **Minor** - New features
- **Patch** - Bug fixes

Current version: `1.0.0`

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

- Create an [Issue](../../issues) for bug reports
- Check [Discussions](../../discussions) for questions
- Email: vadimtorus@gmail.com
