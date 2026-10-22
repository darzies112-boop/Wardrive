# AetherPulse — Tactical Wi-Fi Wardriving & RF Telemetry Mapper

AetherPulse is an Android wardriving and wireless spectrum reconnaissance tool that pairs real-time Android `WifiManager` scanning and `LocationManager` / Fused GPS telemetry with a high-contrast tactical GPS coordinate grid, directional signal radar, channel congestion analyzer, security protocol auditor, and cloud-synced session logging with WiGLE CSV and KML export.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following core product decisions have been locked in based on your selections:

- **Primary Visualization**: **Tactical GPS Grid Map & Signal Radar** — A dual-mode interactive canvas featuring a zoomable/pannable GPS coordinate grid with plotted AP beacons, breadcrumb track trails, and a polar RSSI radar sweep displaying nearby networks by relative bearing and signal strength (`-30 dBm` to `-100 dBm`).
- **Analysis & Export Suite**:
  - **WiGLE-Compatible CSV & KML Export**: One-tap export of recorded sessions and discovered BSSIDs via the Android Share Sheet in standard WiGLE v1.4 CSV format and Google Earth `.kml` placemark/track format.
  - **Real-Time Channel Congestion & Frequency Analyzer**: Interactive spectrum distribution charts across `2.4 GHz` (Channels 1–14), `5 GHz` (UNII bands), and `6 GHz` (Wi-Fi 6E) showing overlapping channel widths (`20/40/80/160 MHz`) and optimal channel recommendations.
  - **Security Protocol Breakdown & Open Network Alerts**: Automatic capability parsing (`WPA3-SAE`, `WPA2-PSK`, `WEP`, `OWE`, `Open`) with visual/haptic alerts when unencrypted or vulnerable WEP/WPS networks are detected.
  - **Track Recording with Distance & Session Stats**: Live GPS breadcrumb tracking calculating total distance traveled (Haversine formula), current speed, altitude, unique BSSIDs discovered, and new-vs-known network counts.
- **Storage & Multi-Device Sync**: **Cloud Sync with Google Sign-In** — Backed by **Firebase Authentication** (Google Sign-In via Jetpack Credential Manager) and **Cloud Firestore** so wardriving sessions and unique network discoveries sync seamlessly across devices in real time.

> [!NOTE]
> **Hardware Telemetry & Indoor/Emulator Fallback**: Because Android emulators or indoor desktop environments may have static GPS coordinates or limited physical Wi-Fi beacons, AetherPulse binds directly to real `WifiManager.getScanResults()` and `LocationManager` GPS updates first, and provides a clearly labeled **Field Simulation / Replay Mode** toggle in the top telemetry bar so you can test moving GPS paths and dense multi-band RF environments at any time.

---

### 1. Overview & Core Concept

- **What It Does**: Continuously scans nearby 802.11 wireless access points, correlates each BSSID and RSSI reading with high-precision GPS coordinates (`latitude`, `longitude`, `altitude`, `accuracy`), visualizes signal propagation on a tactical map and radar, audits encryption posture, and syncs wardriving runs to the user's cloud account.
- **Target Audience / Persona**: Wireless network engineers, security researchers, penetration testers, and wardriving enthusiasts who need rapid field visibility into RF coverage, rogue/open APs, and standardized export pipelines.
- **Key Value**: Combines live geospatial plotting, RF spectrum congestion diagnostics, and cloud-backed WiGLE/KML logging in a zero-clutter, sunlight- and night-ready tactical interface.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Authentication & Cloud Handshake**: Users sign in via Google Sign-In (`CredentialManager`). Once authenticated, their historical cloud wardriving sessions and master BSSID catalog stream live from Cloud Firestore.
  2. **Permission Onboarding & Hardware Readiness**: Clean tactical readiness banner checks and requests `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `NEARBY_WIFI_DEVICES` (Android 13+), displaying live GPS satellite lock status and Wi-Fi scan throttling awareness.
  3. **Tactical Radar & GPS Grid Canvas (`Map / Radar` Tab)**:
     - Toggle or split between the **Geospatial Coordinate Grid** (showing the user's live GPS heading vector, recorded breadcrumb polyline, and color-coded AP nodes by security/signal) and the **Polar RF Radar** (concentric dBm rings from `-40 dBm` inner ring to `-95 dBm` outer perimeter with sweeping phosphor indicator).
     - Tap any plotted BSSID node on the grid or radar to open an **AP Telemetry Inspector Bottom Sheet** with vendor OUI lookup, frequency/channel width, peak vs. current dBm, capabilities string, GPS lock coordinates, and a live single-AP dBm history sparkline.
  4. **Live AP Scanner & Security Auditor (`Networks` Tab)**:
     - Searchable, filterable real-time list of detected BSSIDs with quick filter chips (`All`, `Open / Unsecured`, `WPA3`, `2.4 GHz`, `5 GHz`, `6 GHz`, `Strong > -65 dBm`).
     - **Open Network Alert Banner**: Highlights newly discovered unencrypted or WEP networks with optional haptic pulse.
  5. **Spectrum & Channel Congestion (`Spectrum` Tab)**:
     - Band selector (`2.4 GHz`, `5 GHz`, `6 GHz`) rendering overlapping Gaussian bell curves / channel occupancy bars for every active AP, plus a **Security Breakdown Donut & Stat Grid** (`WPA3`, `WPA2`, `WEP`, `Open`).
  6. **Session Recorder & Cloud/Export Hub (`Sessions` Tab)**:
     - Start, pause, and finalize wardriving sessions with live elapsed timer, distance traveled (`km`/`mi`), unique AP counter, and one-tap **Save to Cloud** or **Export WiGLE CSV / KML** via Android Share Sheet.

- **Visual Identity & Theme**:
  - **Aesthetic Direction**: **Tactical RF Oscilloscope & Field Telemetry** — Precision dark UI inspired by spectrum analyzers and avionics HUDs, maximizing night-vision preservation and high-contrast legibility.
  - **Color Palette & Mood**:
    - Background Surface (`#080C10` Deep Obsidian) & Elevated Card Surface (`#101720` Tactical Slate)
    - Primary Accent (`#00F5A0` Phosphor Radar Emerald — strong signal & active sweep)
    - Secondary Accent (`#00D2FF` Telemetry Cyan — 5/6 GHz spectrum & GPS lock)
    - Warning / Open Network Alert (`#FFB800` Amber Alert — crowded channel / WEP / OWE)
    - Critical / Unsecured Alert (`#FF3B5C` Crimson Beacon — Open/Unencrypted APs & weak signal)
    - Grid Lines & Muted Chrome (`#1E293B` Hairline Coordinate Grid, `#94A3B8` Secondary Telemetry Labels)
  - **Typography & Hierarchy**:
    - **Display & Section Headers**: `Space Grotesk` (bundled locally via `font-util`) for crisp geometric technical titles.
    - **Telemetry, BSSIDs, dBm & Coordinates**: `JetBrains Mono` (bundled locally via `font-util`) for tabular alignment of MAC addresses (`00:1A:2B:3C:4D:5E`), GPS coordinates, frequency MHz, and dBm readouts.
  - **Component Styling & Layout**:
    - Edge-to-edge layout (`enableEdgeToEdge()`) with persistent top **Telemetry HUD Bar** (GPS Fix status, Active Scan indicator, Session Recording pill, and Field Simulation toggle) and bottom Material 3 `NavigationBar` respecting system insets.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Custom Vector GPS Coordinate Grid & Polar Radar vs. External Map Tile SDK**
  - *Chosen Approach*: High-precision custom Jetpack Compose `Canvas` geospatial projection (equirectangular local tangent plane projection with pinch-to-zoom, pan, scale bar in meters, coordinate graticules, breadcrumb trail, and AP clustering) paired with a Polar Radar view.
  - *Why*: Eliminates external map API key friction, works 100% offline in remote wardriving environments, renders at 60 FPS with custom RF heat halos, and matches the requested tactical GPS grid + radar aesthetic.
  - *Alternatives Considered*: Google Maps SDK (requires separate billing/API key configuration and obscures dense overlapping AP pins at street level).

- **Decision 2: Cloud Sync Architecture & Throttled Batching**
  - *Chosen Approach*: Real-time local state updates at scan frequency (`1–3s`), paired with user-scoped Cloud Firestore collections (`/users/{userId}/wardrive_sessions` and `/users/{userId}/discovered_networks`) using deterministic BSSID document IDs (`BSSID` sanitized) so repeated sightings update peak signal strength (`bestRssi`), last-seen timestamp, and strongest GPS fix without creating duplicate documents.
  - *Why*: Prevents Firestore write quota exhaustion during high-density drives while guaranteeing every unique AP and completed session is backed up and synced across the user's devices.

- **Decision 3: Hardware Wi-Fi + GPS Telemetry with Seamless Field Replay Mode**
  - *Chosen Approach*: Direct integration with Android's `WifiManager` (`SCAN_RESULTS_AVAILABLE_ACTION` broadcast receiver + active `startScan()`) and `LocationManager` (`GPS_PROVIDER` + `NETWORK_PROVIDER`), augmented by an explicit **Field Sim** switch that synthesizes a realistic moving GPS path with 2.4/5/6 GHz AP beacons when testing inside an emulator or stationary desk.
  - *Why*: Honours real hardware sensors first while ensuring every chart, radar ring, distance tracker, and KML/CSV export can be thoroughly exercised in any environment.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (Edge-to-Edge)                         │
│             AuthGateComposable (CredentialManager Google Sign-In)          │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ Authenticated (uid)
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     WardriveViewModel (StateFlow Store)                    │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌────────────────────┐  │
│  │ Live Scan & GPS Flow │ │ Active Session Track │ │ Cloud Sync & Export│  │
│  └──────────┬───────────┘ └──────────┬───────────┘ └─────────┬──────────┘  │
└─────────────┼────────────────────────┼───────────────────────┼─────────────┘
              │                        │                       │
     ┌────────┴────────┐      ┌────────┴────────┐     ┌────────┴────────┐
     ▼                 ▼      ▼                 ▼     ▼                 ▼
┌──────────┐     ┌──────────────────┐     ┌────────────────┐   ┌─────────────┐
│Tactical  │     │Network List &    │     │Spectrum &      │   │Sessions &   │
│Grid Map &│     │Security Auditor  │     │Channel         │   │WiGLE / KML  │
│RF Radar  │     │(Filter + Alerts) │     │Congestion View │   │Cloud Hub    │
└──────────┘     └──────────────────┘     └────────────────┘   └─────────────┘
     ▲                                                               ▲
     │ Hardware Telemetry / Sim Engine                               │
┌────┴──────────────────────────────────────────┐   ┌────────────────┴───────┐
│ WifiLocationTelemetryEngine                   │   │ WardriveRepository     │
│ • WifiManager BroadcastReceiver + ScanResult  │   │ • Firestore (Named DB) │
│ • LocationManager GPS Fix + Haversine Tracker │   │ • /users/{uid}/...     │
│ • OUI Vendor Resolver & Security Parser       │   │ • WiGLE CSV / KML Gen  │
└───────────────────────────────────────────────┘   └────────────────────────┘
```

#### Data Model & State

1. **`DiscoveredNetwork` (Firestore `/users/{userId}/networks/{networkId}` & Live UI Model)**:
   - `id`: Deterministic sanitized BSSID (e.g., `00_1A_2B_3C_4D_5E`)
   - `bssid`: MAC address string (`00:1A:2B:3C:4D:5E`)
   - `ssid`: Network name (or `<Hidden SSID>`)
   - `capabilities`: Raw 802.11 capability string (e.g., `[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]`)
   - `securityType`: Parsed enum (`OPEN`, `WEP`, `WPA`, `WPA2`, `WPA3`, `OWE`)
   - `frequencyMhz`: Center frequency (`2412`–`6995` MHz)
   - `band`: `2.4 GHz`, `5 GHz`, or `6 GHz`
   - `channel`: Calculated 802.11 channel number (`1`–`196`)
   - `channelWidthMhz`: `20`, `40`, `80`, or `160` MHz
   - `currentRssi`: Latest signal strength in dBm (`-30` to `-100`)
   - `bestRssi`: Strongest recorded signal strength in dBm
   - `bestLat`, `bestLon`, `bestAlt`, `accuracyMeters`: GPS coordinates captured at peak signal strength
   - `vendor`: Resolved hardware manufacturer from IEEE OUI prefix
   - `seenCount`: Total sightings during active/cloud sessions
   - `userId`: Owner Firebase Auth UID
   - `createdAt`, `updatedAt`: Server timestamps (`@ServerTimestamp`)

2. **`WardriveSession` (Firestore `/users/{userId}/sessions/{sessionId}`)**:
   - `id`: Document ID
   - `title`: Session name (e.g., `Sector Sweep — Oct 22 06:04`)
   - `durationSeconds`: Elapsed recording duration
   - `distanceMeters`: Total GPS path distance via Haversine calculation
   - `totalNetworks`: Count of unique BSSIDs logged
   - `openNetworks`: Count of unencrypted/WEP networks identified
   - `band24Count`, `band5Count`, `band6Count`: Band distribution counts
   - `startLat`, `startLon`, `endLat`, `endLon`: Bounding coordinates
   - `trackPointsJson`: Compact serialized array of `[lat, lon, rssi, timestamp]` breadcrumbs for path replay and KML `<LineString>` export
   - `userId`: Owner Firebase Auth UID
   - `createdAt`, `updatedAt`: Server timestamps (`@ServerTimestamp`)

#### Interactive Component & State Mapping

- **Tactical Map & Radar Controls**:
  - Mode Switcher (`GPS Grid` | `RF Radar` | `Split HUD`), Zoom (`+` / `-` / pinch), `Center on GPS`, Band/Security color-coding toggle, and interactive node tap opening the **AP Telemetry Inspector Bottom Sheet**.
- **Session Recording Bar**:
  - `Start Drive` / `Pause` / `Finish & Sync to Cloud` controls with live distance counter, speed (`km/h`), GPS accuracy badge (`±4m`), and `Field Sim` toggle for instant trajectory simulation.
- **Spectrum & Security Views**:
  - Band tabs (`2.4 GHz` / `5 GHz` / `6 GHz`) dynamically recompute channel congestion scores and highlight the least-congested channel; tapping a security category in the breakdown filters the network list immediately.
- **Export & Cloud Actions**:
  - `Export WiGLE CSV` and `Export KML` generate standards-compliant files in cache storage and launch `Intent.ACTION_SEND` via `FileProvider` so users can save or share logs directly.
