# 🎵 JOOX Audio Source Plugin for Lavalink v4

A high-performance **JOOX Audio Source Plugin** for **Lavalink v4** and **Lavaplayer**, engineered with a decoupled multi-module architecture based on the [TrackCache](https://github.com/PeterGamez/TrackCache) standard.

This plugin enables Discord bots and audio clients to search, resolve, and stream music directly from **JOOX** (Tencent Music Entertainment), featuring intelligent **proxy rotation**, **regional catalog selection** (ID, TH, MY, HK), **audio quality negotiation** (128k, 320k, Lossless FLAC), and format probing (MP3, AAC, FLAC).

---

## 🏛️ Modular Architecture Overview

Following the decoupled pattern established by TrackCache, the repository is split into two distinct subprojects to prevent classpath leakage and maximize portability:

```mermaid
flowchart TD
    Client(["🎧 Lavalink Client / Discord Bot"]) -->|"POST /v4/loadtracks"| Lavalink["🖥️ Lavalink v4 Server"]

    subgraph PluginModule [":plugin (Lavalink Extension Module)"]
        PluginLoader["🔌 JooxPluginLoader<br/><i>(AudioPlayerManagerConfiguration SPI)</i>"]
        PluginConfig["⚙️ JooxPluginConfig<br/><i>(@ConfigurationProperties: plugins.joox)</i>"]
        RestApi["📊 JooxRestHandler<br/><i>(Diagnostics: /v4/joox/*)</i>"]
    end

    subgraph CoreModule [":main (Core Library - Standalone Lavaplayer Ready)"]
        SourceManager["🎼 JooxAudioSourceManager<br/><i>(AudioSourceManager)</i>"]
        AudioTrack["🔊 JooxAudioTrack<br/><i>(DelegatedAudioTrack)</i>"]
        ApiClient["🌐 JooxApiClient<br/><i>(Java 17 HttpClient & Failover)</i>"]
        ProtocolParser["🧩 JooxProtocolParser<br/><i>(JSON Deserializer)</i>"]
        ProxyRotator["🛡️ ProxyRotator<br/><i>(Round-Robin / Failover)</i>"]
        CoreConfig["📝 JooxConfig<br/><i>(Pure Java POJO)</i>"]
    end

    Lavalink -->|"1. Initialize Plugin"| PluginLoader
    PluginLoader -->|"2. Read application.yml"| PluginConfig
    PluginLoader -->|"3. Register Source Manager"| SourceManager

    Lavalink -->|"4. Dispatch Query"| SourceManager
    SourceManager -->|"5. Search / Fetch Metadata"| ApiClient
    ApiClient <-->|"6. Rotate / Quota Check"| ProxyRotator
    ApiClient -->|"7. Ingest Raw JSON"| ProtocolParser
    ProtocolParser -->|"8. Build Track / Playlist"| AudioTrack
    AudioTrack -->|"9. Probe & Stream Audio CDN"| Lavalink
    Lavalink -->|"10. Deliver Audio"| Client

    classDef clientNode fill:#1f2937,stroke:#374151,stroke-width:2px,color:#f9fafb;
    classDef serverNode fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#f8fafc;
    classDef pluginNode fill:#1e3a8a,stroke:#2563eb,stroke-width:2px,color:#eff6ff;
    classDef coreNode fill:#312e81,stroke:#6366f1,stroke-width:2px,color:#eef2ff;

    class Client clientNode;
    class Lavalink serverNode;
    class PluginLoader,PluginConfig,RestApi pluginNode;
    class SourceManager,AudioTrack,ApiClient,ProtocolParser,ProxyRotator,CoreConfig coreNode;
```

### Module Responsibilities:
1. **`:main` (Core Library):**
   - Pure audio player logic, Java 17 HTTP networking, proxy rotation mechanisms, and protocol parsing.
   - Depends solely on `lavaplayer` APIs without Spring Boot dependencies.
   - Can be used independently as a standard Lavaplayer source manager in standalone applications.
   - Scopes host-provided libraries (`lavaplayer`, `slf4j`, `jackson-databind`, `kotlin-stdlib`) as `compileOnly`, achieving zero external runtime dependencies.
2. **`:plugin` (Lavalink Extension):**
   - Spring Boot integration wrapper for Lavalink v4.
   - Implements `AudioPlayerManagerConfiguration` SPI to register `JooxAudioSourceManager`.
   - Binds `application.yml` properties under `plugins.joox`.
   - Exposes diagnostic and management REST endpoints under `/v4/joox/*`.
   - Scopes `lavalink-api` and `spring-boot-starter` strictly as `compileOnly`.
   - Produces an ultra-lightweight (~36 KB), clean artifact targeting the Lavalink v4 plugin spec.

---

## 🚀 Key Features

- **Decoupled Architecture:** Clean separation of concerns between core audio logic and Lavalink Spring runtime.
- **Intelligent Proxy Rotation:**
  - Multiple proxy rotation strategies: `roundRobin`, `random`, `failover`.
  - Supports HTTP, HTTPS, and SOCKS5 proxies with optional basic authentication.
  - Automatic fault detection and quarantine cooldown for dead or rate-limited proxies.
- **Regional Catalog Support:** Configurable country code (`ID` - Indonesia, `TH` - Thailand, `MY` - Malaysia, `HK` - Hong Kong, etc.) to unlock regional catalogs and bypass geo-restrictions.
- **Audio Quality Selection:**
  - `MP3_320` (High quality 320 kbps MP3 - default)
  - `MP3_128` (Standard quality 128 kbps MP3)
  - `M4A_96` (Bandwidth-efficient 96 kbps AAC)
  - `FLAC` (Lossless streaming, VIP enabled)
- **Format-Agnostic Streaming:** Uses native Lavaplayer `MediaContainerDetection` to automatically probe and decode MP3, M4A/AAC, or FLAC audio streams dynamically.
- **Search & Prefixes:** Native search capability via `jooxsearch:<query>` and `jxsearch:<query>`.
- **Diagnostic REST API:** Optional endpoints to monitor proxy health, latency, and reset quarantined proxies on the fly.

---

## 🔑 Supported Identifiers & URL Formats

| Type | Syntax / URL Example | Resolved Resource |
| :--- | :--- | :--- |
| **Search Query (Full)** | `jooxsearch:tulus hati hati di jalan` | Top search results |
| **Search Query (Short)** | `jxsearch:alan walker faded` | Top search results |
| **Track URL (Web)** | `https://www.joox.com/id/single/abc12345` | Single Track |
| **Track URL (Regional)** | `https://www.joox.com/th/single/xyz98765` | Single Track |
| **Direct Track ID** | `joox:track:abc12345` | Single Track |
| **Album URL** | `https://www.joox.com/id/album/alb12345` | Full Album Playlist |
| **Direct Album ID** | `joox:album:alb12345` | Full Album Playlist |
| **Playlist URL** | `https://www.joox.com/id/playlist/pl12345` | User / Curated Playlist |
| **Direct Playlist ID** | `joox:playlist:pl12345` | User / Curated Playlist |

---

## 🔊 Audio Streaming & Playback Mechanics

The plugin streams audio directly into Lavalink and Discord voice channels using Lavaplayer's native stream processing pipeline:

```mermaid
flowchart LR
    subgraph StreamFlow ["Audio Streaming Pipeline"]
        CDN["🌐 JOOX Audio CDN<br/><i>(MP3 / M4A / FLAC)</i>"] -->|"HTTP Range Stream"| HttpStream["📦 PersistentHttpStream<br/><i>(Lavaplayer IO)</i>"]
        HttpStream -->|"Probing"| Probe["🔍 MediaContainerDetection<br/><i>(Container Auto-detect)</i>"]
        Probe -->|"Opus Encoding"| FrameBuffer["⚡ Lavalink Audio Buffer"]
        FrameBuffer -->|"Voice UDP Packets"| DiscordVoice["🎧 Discord Voice Channel"]
    end

    classDef cdnNode fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#f8fafc;
    classDef streamNode fill:#312e81,stroke:#6366f1,stroke-width:2px,color:#eef2ff;
    classDef voiceNode fill:#064e3b,stroke:#059669,stroke-width:2px,color:#ecfdf5;

    class CDN cdnNode;
    class HttpStream,Probe,FrameBuffer streamNode;
    class DiscordVoice voiceNode;
```

### Essential Playback & Streaming Conditions:

1. **Regional Network / Geo-Blocking (Crucial):**
   - JOOX restricts audio streaming to supported territories: **Thailand (`TH`)**, **Indonesia (`ID`)**, **Malaysia (`MY`)**, and **Hong Kong (`HK`)**.
   - **Domestic Hosting:** If your Lavalink server is hosted within a supported territory (e.g., Thai ISP or local VPS), tracks will stream directly without extra configuration.
   - **Overseas Datacenters:** If your Lavalink node is hosted abroad (e.g., US, Europe, or Singapore), JOOX API and CDN endpoints will block playback with `HTTP 403 Forbidden`. **Solution:** Enable the built-in `proxy` configuration in `application.yml` and provide regional proxies (e.g., Thai HTTP or SOCKS5 proxies) to route requests.

2. **Free vs. VIP Restricted Tracks:**
   - **Public / Free Tracks:** Stream immediately in **Guest / Anonymous Mode** without requiring any login credentials.
   - **VIP Exclusive Tracks:** Require configuring your account's `wmid` and `sessionToken` in `application.yml` to authorize playback for VIP-only tracks and high-definition formats (`MP3_320` or `FLAC`).

---

## ⚙️ Configuration (`application.yml`)

Add the following configuration block to your Lavalink `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.petergamez.jooxsource:jooxsource-plugin:0.1.0"
      repository: "https://jitpack.io" # Or drop jooxsource-plugin-0.1.0.jar into plugins/

plugins:
  joox:
    enabled: true              # Enable or disable the JOOX source plugin (default: true)

    rest-api:
      enabled: true             # Enable or disable /v4/joox REST endpoints (default: true)
      password: ""              # Optional: Password for REST API via X-Joox-Password header (disabled if empty)

    countryCode: "TH"          # Regional catalog: TH (Thailand - default), ID (Indonesia), MY (Malaysia), HK (Hong Kong)
    # Audio quality priority list (ordered by preference)
    qualities: [ MP3_320, MP3_128, M4A_96, FLAC ]
    wmid: ""                   # Optional: Joox Web Master ID / anonymous user token
    sessionToken: ""           # Optional: Joox VIP session token for premium/FLAC tracks
    allowSearch: true          # Allow search syntax (jooxsearch: and jxsearch:)
    searchLimit: 20            # Number of tracks returned per search query

    # Proxy Rotation Configuration (recommended for multi-region or geo-unblocking)
    proxy:
      enabled: false           # Enable proxy rotation (default: false)
      strategy: "roundRobin"   # Rotation strategy: roundRobin, random, failover
      retryLimit: 3            # Max proxy retry attempts before failing request
      cooldownSeconds: 60      # Quarantine duration for a failed proxy in seconds
      connectTimeoutSeconds: 5 # Connection timeout per proxy in seconds
      proxies:
        - "http://proxy1.example.com:8080"
        - "http://username:password@proxy2.example.com:8080"
        - "socks5://proxy3.example.com:1080"
```

### 🔑 How to Obtain `wmid` and `sessionToken`

Both `wmid` and `sessionToken` are **optional** for general playback. However, they are required if you want to stream **VIP-exclusive tracks** or unlock **Lossless FLAC** audio quality:

1. **Open JOOX Web:** Navigate to [joox.com](https://www.joox.com/) in your web browser.
2. **Log In:** Sign in with your JOOX account.
3. **Open Developer Tools:** Press `F12` (or right-click anywhere on the page and select **Inspect**).
4. **Locate Session Cookies via Application or Network Tab:**
   - **Method A (Via Application / Storage Tab):**
     - Navigate to the **Application** tab.
     - In the left sidebar, expand **Cookies** and select `https://www.joox.com`.
     - Find the following cookie entries:
       - `wmid`: Numeric User ID (e.g., `123456789`) -> copy this into the `wmid` configuration field.
       - `session_key` (or `session_token`): Alphanumeric session token string -> copy this into the `sessionToken` configuration field.
   - **Method B (Via Network Tab):**
     - Switch to the **Network** tab and filter requests by `Fetch/XHR`.
     - Search for or play any song on the website.
     - Click on any request sent to `api.joox.com` (such as `web_get_songinfo` or `web_search`).
     - In the **Headers** panel, scroll down to **Request Headers** and locate the **Cookie** header.
     - Extract the values for `wmid=...` and `session_key=...`.

> [!TIP]
> If left empty (`wmid: ""` and `sessionToken: ""`), the plugin automatically operates in **Guest / Anonymous Mode**, allowing you to resolve and play all publicly available JOOX tracks without needing an account!

---

## 📊 Diagnostic & Management REST API

When `plugins.joox.rest-api.enabled` is `true`, the following endpoints are available under `/v4/joox/...`:

> [!NOTE]
> **Authentication:** If `rest-api.password` is configured in `application.yml`, include the `X-Joox-Password` header:
> `X-Joox-Password: <your-rest-api-password>`. If `rest-api.password` is empty (default), authentication is disabled.

### 1. Plugin Status
```http
GET /v4/joox/status
```
**Example Response:**
```json
{
  "enabled": true,
  "countryCode": "TH",
  "qualities": [
    "MP3_320",
    "MP3_128",
    "M4A_96",
    "FLAC"
  ],
  "allowSearch": true,
  "proxyEnabled": true,
  "totalProxies": 3,
  "availableProxies": 3
}
```

### 2. Proxy Pool Health & Metrics
```http
GET /v4/joox/proxies
```
**Example Response:**
```json
{
  "strategy": "roundRobin",
  "proxies": [
    {
      "host": "proxy1.example.com",
      "port": 8080,
      "type": "HTTP",
      "available": true,
      "consecutiveFailures": 0,
      "totalSuccesses": 142,
      "quarantinedUntil": 0
    },
    {
      "host": "proxy2.example.com",
      "port": 8080,
      "type": "HTTP",
      "available": false,
      "consecutiveFailures": 3,
      "totalSuccesses": 20,
      "quarantinedUntil": 1729864200000
    }
  ]
}
```

### 3. Reset Quarantined Proxies
```http
POST /v4/joox/proxies/reset
```
**Example Response:**
```json
{
  "success": true,
  "message": "All proxy quarantines have been reset",
  "availableProxies": 3
}
```

---

## 🛠️ Building From Source

### Prerequisites:
- **JDK 17** or higher
- Gradle wrapper (included)

### Build Commands:
```bash
# Build both core library (:main) and plugin (:plugin)
./gradlew build

# Run test suites across all modules
./gradlew test

# Install artifacts to local Maven repository (~/.m2)
./gradlew publishToMavenLocal
```

The compiled artifacts will be output to `build/libs/`:
- `build/libs/jooxsource-main-0.1.0.jar` (Standalone Lavaplayer Core Library)
- `build/libs/jooxsource-plugin-0.1.0.jar` (Lavalink v4 Plugin Jar)

