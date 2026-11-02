# 🎵 JooxSource - Lavalink v4 Plugin

**JooxSource** is a high-performance **JOOX Audio Source Plugin** for **Lavalink v4** engineered with a decoupled multi-module architecture.

This plugin enables Discord bots and audio clients to search, resolve, and stream music directly from **JOOX** (Tencent Music Entertainment), featuring intelligent **proxy rotation**, **regional catalog selection** (TH, ID, MY, HK), **audio quality negotiation** (128k, 320k, Lossless FLAC), and format probing (MP3, AAC, FLAC).

---

## 🚀 Architecture & Request Flow

```mermaid
flowchart TD
    Client(["🎧 Client / Discord Bot"]) -->|"1. GET /v4/loadtracks"| LavalinkServer["🖥️ Lavalink Server (v4)"]

    subgraph Plugin ["Lavalink Plugin (:plugin)"]
        PluginLoader["🔌 JooxPluginLoader"]
        RestHandler["📊 JooxRestHandler<br/><i>(/v4/joox/*)</i>"]
    end

    subgraph Core ["Core Library (:main) - Standalone Lavaplayer Ready"]
        SourceManager["🎼 JooxAudioSourceManager"]
        ApiClient["🌐 JooxApiClient"]
        ProxyRotator["🛡️ ProxyRotator"]
        AudioTrack["🔊 JooxAudioTrack"]
    end

    JooxAPI[("☁️ JOOX API / CDN")]

    %% Execution Flow
    LavalinkServer -->|"2. Dispatch Query"| SourceManager
    SourceManager -->|"3. Resolve Track"| ApiClient
    ApiClient <-->|"4. Rotate / Failover"| ProxyRotator
    ApiClient <-->|"5. Fetch Metadata"| JooxAPI
    SourceManager -->|"6. Create Track"| AudioTrack
    AudioTrack -->|"7. Stream Audio"| LavalinkServer
    LavalinkServer -->|"8. Deliver Audio"| Client

    %% Diagnostics REST
    Client <-->|"Diagnostics & Reset"| RestHandler
    RestHandler -.->|"Inspect / Manage"| ProxyRotator

    %% Custom Styling
    classDef clientNode fill:#1f2937,stroke:#374151,stroke-width:2px,color:#f9fafb;
    classDef serverNode fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#f8fafc;
    classDef pluginNode fill:#1e3a8a,stroke:#2563eb,stroke-width:2px,color:#eff6ff;
    classDef coreNode fill:#312e81,stroke:#6366f1,stroke-width:2px,color:#eef2ff;
    classDef cloudNode fill:#065f46,stroke:#10b981,stroke-width:2px,color:#ecfdf5;

    class Client clientNode;
    class LavalinkServer serverNode;
    class PluginLoader,RestHandler pluginNode;
    class SourceManager,ApiClient,ProxyRotator,AudioTrack coreNode;
    class JooxAPI cloudNode;
```

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
    - dependency: "com.github.petergamez.jooxsource:jooxsource-plugin:0.1.0"
      repository: "https://jitpack.io" # Or drop jooxsource-plugin-0.1.0.jar into plugins/

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

    # Proxy Rotation Configuration (recommended for multi-region or geo-unblocking)
    proxy:
      enabled: false            # Enable proxy rotation (default: false)
      strategy: "roundRobin"    # Rotation strategy: roundRobin, random, failover
      retryLimit: 3             # Max proxy retry attempts before failing request
      cooldownSeconds: 60       # Quarantine cooldown for failing proxies in seconds
      connectTimeoutSeconds: 5  # Connection timeout per proxy in seconds
      proxies:
        - "http://proxy1.example.com:8080"
        - "http://username:password@proxy2.example.com:8080"
        - "socks5://proxy3.example.com:1080"
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
| `proxy.enabled` | Boolean | `false` | Enables intelligent proxy routing and automatic failover. |
| `proxy.strategy` | String | `"roundRobin"` | Rotation strategy: `roundRobin`, `random`, or `failover`. |
| `proxy.retryLimit` | Integer | `3` | Maximum retry attempts across available proxies before aborting request. |
| `proxy.cooldownSeconds` | Integer | `60` | Duration (in seconds) to quarantine a failed proxy before testing it again. |
| `proxy.connectTimeoutSeconds` | Integer | `5` | Connection timeout per proxy attempt in seconds. |
| `proxy.proxies` | List | `[]` | List of proxy URLs (`http://`, `https://`, or `socks5://`). |

---

## 🛡️ Proxy Strategies & Regional Routing

JOOX enforces regional availability restrictions for music playback. If your Lavalink node is hosted outside of Southeast Asia (e.g. US, Europe), configuring regional proxies ensures reliable playback:

| Strategy | Description | Recommended For |
| :--- | :--- | :--- |
| **`roundRobin`** | Sequentially rotates through all healthy proxies in the pool. | High-volume clusters distributing traffic across multiple proxies. |
| **`random`** | Selects a random available proxy for each outgoing request. | Load balancing across a large pool of homogenous residential proxies. |
| **`failover`** | Always utilizes the primary proxy (`proxies[0]`), only falling back if it fails. | Dedicated high-speed proxies with backup fallbacks. |

> [!TIP]
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