# 🎵 JooxSource - Lavalink v4 Plugin

**JooxSource** is a high-performance **JOOX Audio Source Plugin** for **Lavalink v4**.

This plugin enables Discord bots and audio clients to search, resolve, and stream music directly from **JOOX** (Tencent Music Entertainment), featuring intelligent **proxy rotation**, **regional catalog selection** (TH, ID, MY, HK), **audio quality negotiation** (128k, 320k, Lossless FLAC), and format probing (MP3, AAC, FLAC).

---

## 🚀 Architecture & Request Flow

```mermaid
flowchart TD
    Client(["🎧 Client / Discord Bot"]) -->|"GET /v4/loadtracks"| LavalinkServer["🖥️ Lavalink Server (v4)"]

    subgraph Plugin ["Lavalink Plugin (:plugin)"]
        PluginLoader["🔌 JooxPluginLoader<br/><i>(AudioPlayerManagerConfiguration)</i>"]
    end

    subgraph Core ["Core Library (:main) - Standalone Lavaplayer Ready"]
        SourceManager["🎼 JooxAudioSourceManager<br/>(Track & Search Resolver)"]
        ApiClient["🌐 JooxApiClient<br/>(Next.js SSR Scraper)"]
        ProxyRotator["🛡️ ProxyRotator<br/>(Quarantine & Failover)"]
        AudioTrack["🔊 JooxAudioTrack<br/>(Dual-Mode Player)"]
    end

    JooxWeb[("☁️ JOOX Web / CDN<br/><i>(__NEXT_DATA__ SSR)</i>")]
    FallbackProviders["🌐 Audio Fallback Providers<br/><i>(YouTube / SoundCloud / YTM)</i>"]
    VoiceChannel(["🔊 Discord Voice Channel"])

    %% Request & Metadata Resolution Flow
    LavalinkServer -->|"1. Dispatch Query"| PluginLoader
    PluginLoader -->|"2. Delegate Resolution"| SourceManager
    SourceManager -->|"3. Resolve Metadata"| ApiClient
    ApiClient <-->|"Proxy Rotation"| ProxyRotator
    ApiClient <-->|"4. Scrape Next.js SSR"| JooxWeb
    SourceManager -->|"5. LoadResult (Track / Playlist)"| LavalinkServer
    LavalinkServer -->|"Final JSON Response"| Client

    %% Playback Execution Flow (Dual-Mode)
    LavalinkServer -.->|"6. Start Playback"| AudioTrack

    %% Direct CDN Stream Branch
    AudioTrack -->|"✅ Direct CDN Stream (non-DRM)"| VoiceChannel

    %% Fallback Mirroring Branch
    AudioTrack -.->|"❌ Fallback Mirroring (%QUERY%)"| FallbackProviders
    FallbackProviders -.->|"Delegate Audio Stream"| VoiceChannel

    %% Custom Styling
    classDef clientNode fill:#1f2937,stroke:#374151,stroke-width:2px,color:#f9fafb;
    classDef serverNode fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#f8fafc;
    classDef hitNode fill:#064e3b,stroke:#059669,stroke-width:2px,color:#ecfdf5;
    classDef missNode fill:#7f1d1d,stroke:#dc2626,stroke-width:2px,color:#fef2f2;
    classDef serviceNode fill:#1e3a8a,stroke:#2563eb,stroke-width:2px,color:#eff6ff;
    classDef dbNode fill:#831843,stroke:#db2777,stroke-width:2px,color:#fdf2f8;
    classDef coreNode fill:#312e81,stroke:#6366f1,stroke-width:2px,color:#eef2ff;

    class Client,VoiceChannel clientNode;
    class LavalinkServer serverNode;
    class PluginLoader serviceNode;
    class FallbackProviders missNode;
    class SourceManager,ApiClient,ProxyRotator,AudioTrack coreNode;
    class JooxWeb dbNode;
```

### Why JooxSource is Resilient and High-Performance:
1. **Dual-Mode Playback Engine (Direct CDN + Fallback Mirroring):** `JooxAudioTrack` resolves direct CDN audio streams (supporting up to 320 kbps MP3 or lossless FLAC) whenever available. If a track is regional-restricted, VIP-locked, or DRM-protected, it seamlessly delegates playback to Lavalink's configured fallback providers (YouTube, SoundCloud, YouTube Music) using the LavaSrc search mirroring model.
2. **Intelligent Proxy Rotation & Quarantine:** Scraper and API traffic through `JooxApiClient` is protected by `ProxyRotator`. If an IP gets rate-limited or fails, it is automatically quarantined for `cooldownSeconds` with zero downtime, smoothly cycling through remaining healthy proxies.

---

## 🔑 Supported Identifiers & Query Syntax

| Query / Identifier Type | Example Syntax | Resolved Resource |
| :--- | :--- | :--- |
| **Search Query (Short)** | `jxsearch:bodyslam ความรัก` | Search results playlist |
| **Search Query (Full)** | `jooxsearch:tulus hati hati di jalan` | Search results playlist |
| **Single Track URL** | `https://www.joox.com/th/single/qrcNQXzSxjtHj_aLy1O14Q==` | Single Track |
| **Regional Track URL** | `https://www.joox.com/id/single/abc12345` | Single Track |
| **Direct Track ID** | `joox:track:qrcNQXzSxjtHj_aLy1O14Q==` | Single Track |
| **Album URL** | `https://www.joox.com/th/album/vA22fbWTCpQ_W7Rr+2Owyw==` | Complete Album Playlist |
| **Direct Album ID** | `joox:album:vA22fbWTCpQ_W7Rr+2Owyw==` | Complete Album Playlist |
| **Playlist URL** | `https://www.joox.com/th/playlist/vt_Y0uqb3cjTkTDCVSaWwQ==` | User / Curated Playlist |
| **Direct Playlist ID** | `joox:playlist:vt_Y0uqb3cjTkTDCVSaWwQ==` | User / Curated Playlist |

---

## ⚙️ Configuration (`application.yml`)

Add the following block to your Lavalink `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: com.github.petergamez.jooxsource:jooxsource-plugin:x.y.z
      repository: https://jitpack.io # Or place the .jar file directly into the plugins/ directory

plugins:
  joox:
    enabled: true               # Enable or disable the JOOX source plugin (default: true)

    rest-api:
      enabled: true             # Enable or disable /v4/joox REST endpoints (default: true)
      password: ""              # Optional: Password for REST API via X-Joox-Password header (disabled if empty)

    countryCode: "TH"           # Regional catalog: TH (default), ID, MY, HK
    qualities: [ MP3_320, MP3_128, M4A_96, FLAC ] # Quality preference order
    allowSearch: true           # Enable jooxsearch: and jxsearch: prefixes (default: true)
    searchLimit: 20             # Max items returned per search query (default: 20)
    wmid: ""                    # Optional: Joox Web Master ID
    sessionToken: ""            # Optional: Joox VIP session token for premium/FLAC tracks

    # Playback & Audio Mirroring (LavaSrc model)
    directStream: true          # Try direct JOOX CDN stream first (if available/VIP)
    fallback:
      enabled: true             # Enable automated audio fallback mirroring (default: true)
      providers:                # Priority order for audio resolution
        - ytmsearch:%QUERY%
        - ytsearch:%QUERY%
        - scsearch:%QUERY%

    # Proxy Rotation Configuration (recommended for multi-region or geo-unblocking)
    proxy:
      enabled: false            # Enable proxy rotation (default: false)
      strategy: "LoadBalance"   # Rotation strategy: LoadBalance, NanoSwitch, Failover
      retryLimit: 3             # Max proxy retry attempts before failing request
      cooldownSeconds: 60       # Quarantine cooldown for failing proxies in seconds
      connectTimeoutSeconds: 5  # Connection timeout per proxy in seconds
      proxies:
        - http://proxy1.example.com:8080
        - http://username:password@proxy2.example.com:8080
        - socks5://proxy3.example.com:1080
```

---

### 🔧 Configuration Options Breakdown

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `enabled` | Boolean | `true` | Enables or disables the entire JOOX source plugin. |
| `rest-api.enabled` | Boolean | `true` | Enables diagnostic & proxy management REST endpoints under `/v4/joox/...`. |
| `rest-api.password` | String | `""` | Optional password for REST API (`X-Joox-Password: <secret>`). Disabled if empty. |
| `countryCode` | String | `"TH"` | Regional catalog: `TH` (Thailand), `ID` (Indonesia), `MY` (Malaysia), `HK` (Hong Kong). |
| `qualities` | Array | `[ MP3_320, MP3_128, M4A_96, FLAC ]` | Quality negotiation priority list (highest preference first). |
| `allowSearch` | Boolean | `true` | Allows search queries via `jooxsearch:` and `jxsearch:`. |
| `searchLimit` | Integer | `20` | Maximum number of results returned for search queries. |
| `wmid` | String | `""` | Optional JOOX user Web Master ID (wmid) for authenticated requests. |
| `sessionToken` | String | `""` | Optional JOOX VIP session token for high-bitrate / VIP tracks. |
| `directStream` | Boolean | `true` | Attempts direct playback from JOOX CDN before falling back. |
| `fallback.enabled` | Boolean | `true` | Enables automated audio fallback mirroring (LavaSrc model) when direct stream is unavailable. |
| `fallback.providers` | List | `[ ytmsearch:%QUERY%, ytsearch:%QUERY%, scsearch:%QUERY% ]` | Ordered list of search providers for resolving playable delegate tracks (supports `%QUERY%` or plain prefixes). |
| `proxy.enabled` | Boolean | `false` | Enables intelligent proxy routing and automatic failover. |
| `proxy.strategy` | String | `"LoadBalance"` | Rotation strategy: `LoadBalance`, `NanoSwitch`, or `Failover`. |
| `proxy.retryLimit` | Integer | `3` | Maximum retry attempts across available proxies before aborting request. |
| `proxy.cooldownSeconds` | Integer | `60` | Duration (in seconds) to quarantine a failed proxy before testing it again. |
| `proxy.connectTimeoutSeconds` | Integer | `5` | Connection timeout per proxy attempt in seconds. |
| `proxy.proxies` | List | `[]` | List of proxy URLs (`http://`, `https://`, or `socks5://`). |

---

## 🛡️ Proxy Strategies & Regional Routing

JOOX enforces regional availability restrictions for music playback. If your Lavalink node is hosted outside of Southeast Asia (e.g. US, Europe), configuring regional proxies ensures reliable playback using Lavalink-aligned routing strategies:

| Strategy | Recommended Pool Size | Working Mechanism | Best Used For |
| :--- | :---: | :--- | :--- |
| **`LoadBalance`** *(Default)* | **2 – 10+ proxies** | Sequentially cycles through healthy proxies in a round-robin loop ($1 \rightarrow 2 \rightarrow 3 \rightarrow 1$). Evenly distributes request load across all IP addresses. | General setups with homogeneous proxies of similar quality and latency; prevents rate limits by spreading traffic equally. |
| **`NanoSwitch`** | **5 – 20+ proxies** | Rapidly alternates proxies on every system clock update without locks or state tracking. | Medium-to-large proxy pools where incoming requests naturally scatter across IPs; zero-lock and highly efficient under concurrent load. |
| **`Failover`** | **2 – 5 proxies** | Exclusively routes traffic through the primary proxy. Only switches to secondary backup proxies if the primary is quarantined or unreachable. | Setups with one dedicated, high-speed primary proxy and 1–2 backup fallback proxies for high availability (HA). |

### 💡 Strategy Selection Guide

- **`LoadBalance` (Recommended for most users):**
  Ideal for small-to-medium clusters with 2 to 10 proxies of comparable speed (e.g., 3–5 datacenter proxy IPs). It guarantees a balanced 100% fair distribution, mitigating HTTP 429 Rate Limits from JOOX.
- **`NanoSwitch` (Clock-based switching):**
  Ideal for pools with 5 to 20+ proxies. Operates statelessly by switching IP offsets on every incoming request. Concurrent requests from different voice channels naturally disperse across available proxies without counter contention.
- **`Failover` (Primary + Backup architecture):**
  Ideal when you possess 1 premium, ultra-fast regional proxy (such as a dedicated Thai/regional IP) and 1–2 cheaper backup proxies. All traffic stays on your fastest IP unless it fails.

> [!NOTE]
> **Automatic Quarantine:** When a proxy fails consecutively up to `retryLimit`, `ProxyRotator` quarantines it for `cooldownSeconds`. Once the cooldown expires, the proxy is seamlessly returned to rotation.

---

## 🎧 Audio Quality Negotiation

The plugin requests audio CDN streams based on your configured `qualities` list in descending order of preference:

| Quality | Bitrate / Container | Description |
| :--- | :--- | :--- |
| **`MP3_320`** | 320 kbps MP3 | High-fidelity audio, default recommended setting. |
| **`MP3_128`** | 128 kbps MP3 | Standard quality, lower bandwidth consumption. |
| **`M4A_96`** | 96 kbps AAC (M4A) | Highly efficient mobile-optimized AAC format. |
| **`FLAC`** | Lossless (16-bit / 24-bit) | Studio quality lossless audio (requires VIP session token). |

---

## 🔑 Optional Credentials (`wmid` & `sessionToken`)

By default, JooxSource runs in **Guest / Anonymous Mode**, resolving and playing all publicly available tracks without login. If you wish to unlock **VIP-only tracks** or **Lossless FLAC**, configure your credentials:

1. **Open JOOX Web:** Visit [joox.com](https://www.joox.com/) in your browser and log into your account.
2. **Open Developer Tools:** Press `F12` and navigate to the **Application** tab (or **Storage** tab in Firefox).
3. **Inspect Cookies:** Expand **Cookies** on the left menu and select `https://www.joox.com`.
4. **Copy Tokens:**
   - Copy the value of `wmid` into `plugins.joox.wmid` in `application.yml`.
   - Copy the value of `session_key` into `plugins.joox.sessionToken` in `application.yml`.

---

## 📊 Monitoring & Diagnostics REST API

When `plugins.joox.rest-api.enabled` is `true`, diagnostic endpoints are accessible under `/v4/joox/...`:

> [!NOTE]
> If `rest-api.password` is configured, pass the `X-Joox-Password` HTTP header:
> `X-Joox-Password: <your-password>`. If password is empty (default), authentication is disabled.

### 1. Check Plugin Status
```http
GET /v4/joox/status
```
**Example Response:**
```json
{
  "enabled": true,
  "countryCode": "TH",
  "qualities": [ "MP3_320", "MP3_128", "M4A_96", "FLAC" ],
  "allowSearch": true,
  "directStream": true,
  "fallbackEnabled": true,
  "fallbackProviders": [ "ytmsearch:%QUERY%", "ytsearch:%QUERY%", "scsearch:%QUERY%" ],
  "proxyEnabled": true,
  "totalProxies": 3,
  "availableProxies": 3
}
```

### 2. Inspect Proxy Pool Health
```http
GET /v4/joox/proxies
```
**Example Response:**
```json
{
  "strategy": "LoadBalance",
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
