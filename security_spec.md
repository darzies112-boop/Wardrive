# AetherPulse Firestore Security Specification

## 1. Data Invariants
1. **Strict Multi-Tenant Ownership**: Every document in `/users/{userId}/networks/{networkId}` and `/users/{userId}/sessions/{sessionId}` belongs exclusively to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
2. **Immutable Identity & Creation Metadata**: `userId`, `bssid` (for networks), and `createdAt` cannot be mutated after creation.
3. **Strict Schema & Bounds Validation**: All strings are length-bounded, enums (`securityType`, `band`, `channelWidthMhz`) are strictly validated against allowlists, coordinates (`lat` in `[-90, 90]`, `lon` in `[-180, 180]`) and `rssi` (`[-120, 0]`) are range-checked, and timestamps enforce `<= request.time`.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read/Write**: `auth = null` attempting `get`/`list`/`create` on `/users/alice/networks`.
2. **Cross-User Read Scraping**: `bob` attempting `get` or `list` on `/users/alice/networks`.
3. **Identity Spoofing on Create**: `alice` writing to `/users/alice/networks/net1` with `userId: "bob"`.
4. **Shadow/Ghost Field Injection on Create**: Injecting `"isAdmin": true` or `"hacked": 1` into `DiscoveredNetwork`.
5. **Shadow/Ghost Field Injection on Update**: Updating `DiscoveredNetwork` with an undeclared field `"verified": true`.
6. **Immutable Field Tampering (`createdAt`)**: Modifying `createdAt` during an `update` on `DiscoveredNetwork` or `WardriveSession`.
7. **Immutable Field Tampering (`bssid` / `userId`)**: Modifying `bssid` or `userId` during an `update`.
8. **Enum Poisoning (`securityType` / `band`)**: Creating a network with `securityType: "WPA99"` or `band: "99 GHz"`.
9. **Coordinate Out-of-Bounds**: Creating a network with `bestLat: 250.0` or `bestLon: -999.0`.
10. **RSSI Out-of-Bounds**: Creating a network with `currentRssi: 50` or `bestRssi: -999`.
11. **Denial-of-Wallet String Overflow**: Creating a network with a 5,000-character `ssid` or a session with a 200-character `title`.
12. **Future Timestamp Spoofing**: Submitting a client timestamp in the future (`createdAt > request.time`).
