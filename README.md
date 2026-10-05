# Moonlight VPN — Android

A Kotlin/Jetpack Compose VPN client built on **Xray-core 26.7.28**, implementing
the `Moonlight Client` design. Subscriptions come from a Remnawave panel and are
imported by QR code, clipboard, manual URL, or a Telegram bot link.

## Downloads

Latest release, always at the same URL:

```
https://github.com/kiineld/moonlightvpn_android/releases/latest/download/moonlight-android-universal.apk
```

Per-ABI builds are about a third of the size — `moonlight-android-arm64-v8a.apk`
covers essentially every phone from 2016 on.

Releases are cut by tagging: `git tag v1.2.3 && git push origin v1.2.3`. The
workflow in `.github/workflows/release.yml` fetches the native artifacts, builds
tun2socks from source, runs the tests, and publishes signed APKs.

### Signing

Release builds are signed from repository secrets. There is **no key in this
repository** — a committed signing key is a public one, and anyone holding it can
build an APK that installs as an update over yours.

Set it up once:

```bash
scripts/setup-signing.sh
```

That creates a 4096-bit key at `~/moonlight-release.keystore` and uploads
`MOONLIGHT_KEYSTORE_BASE64`, `MOONLIGHT_KEYSTORE_PASSWORD`,
`MOONLIGHT_KEY_ALIAS` and `MOONLIGHT_KEY_PASSWORD` to GitHub. The key never
touches the repository or the terminal output.

**Back up that file.** Losing it means you can never publish an update that
installs over the current release — every user would have to uninstall first.

The release workflow refuses to run without those secrets, and verifies with
`apksigner` that every artifact is signed before publishing. Local `assembleRelease`
without a key produces an unsigned APK rather than falling back to anything.

To sign locally, put this in `~/.gradle/gradle.properties` (never the repo):

```properties
moonlight.keystore=/Users/you/moonlight-release.keystore
moonlight.keystore.password=…
moonlight.key.alias=moonlight
moonlight.key.password=…
```

## Getting a build

The native artifacts are large and are not committed. Fetch and build them once:

```bash
scripts/fetch-native.sh && scripts/build-tun2socks.sh && ./gradlew assembleDebug
```

`fetch-native.sh` downloads the libXray AAR and the two Google Fonts.
`build-tun2socks.sh` needs the Android NDK (27.3+) and compiles
hev-socks5-tunnel; see [Why tun2socks is built from source](#why-tun2socks-is-built-from-source).

Requires JDK 17+, Android SDK platform 36, and NDK 27.3.13750724.

## Architecture

```
:app      Compose screens, ViewModels, navigation, AppContainer (manual DI)
:design   Design system — colour/type/shape/motion tokens, components, icons
:data     Remnawave client, share-link parsing, DataStore, repositories
:vpn      MoonlightVpnService, Xray bridge, config builder, tun2socks, latency probe
```

`app → design, data, vpn` · `vpn → data` · `design` standalone.

Dependency wiring is a single `AppContainer` rather than Hilt — the graph is a
dozen singletons, which is not worth annotation processing.

### The data path

```
tun fd → libhev-socks5-tunnel (JNI) → SOCKS5 127.0.0.1:<port> → xray-core → VLESS Reality node
```

| Layer | Artifact | Version |
|---|---|---|
| Proxy core | `XTLS/libXray` gomobile AAR | v26.7.28 |
| tun → socks | `heiher/hev-socks5-tunnel`, built as a JNI library | 2.17.1 |

`MoonlightVpnService` brings these up in an order that is **not**
interchangeable, and the class comment says so:

1. **Register the socket protector first.** `LibXray.registerDialerController`
   installs a `VpnService.protect(fd)` callback. Without it — or with it
   installed after the core starts — the core's own connection to the node gets
   captured by the tun interface and the tunnel deadlocks.
2. Start xray-core, which opens the local SOCKS inbound.
3. Establish the tun interface with the per-app rules.
4. Hand the tun descriptor to hev-socks5-tunnel.

Teardown runs in reverse.

### Two integration details worth knowing

**The core stores a node's display name in `sendThrough`.** Xray has no
outbound-name field, so `convertShareLinksToXrayJson` parks the remark there.
libXray only clears it on a throwaway validation copy, so the config it returns
still carries it. Left in place, Xray tries to parse `🇳🇱 Amsterdam` as a bind
address and refuses to start. `XrayConfigBuilder.extractProxyOutbound` strips it,
and a test pins that behaviour.

**Share links are parsed by the core, not by us.** `convertShareLinksToXrayJson`
handles `vless://`, `vmess://`, `trojan://` and `ss://`. This app builds only the
wrapper — SOCKS inbound, DNS, routing — and grafts the parsed outbound in.
`ShareLinkParser` in `:data` reads *display* metadata only (remark, flag, host),
never the cryptographic payload.

### Why tun2socks is built from source

hev-socks5-tunnel's published Android release artifacts are the **standalone
executable**, whose usage is `hev-socks5-tunnel CONFIG_PATH`. It creates its own
tun device from the config, which needs root, and it has no way to accept the
file descriptor `VpnService` hands us. Only the library build (`ENABLE_LIBRARY`)
exposes `hev_socks5_tunnel_main_from_file(path, fd)` and the JNI entry points.

`scripts/build-tun2socks.sh` compiles it with
`-DPKGNAME=vpn/moonlight/core/tunnel -DCLSNAME=Tun2Socks`, because the native
library registers its four methods against that exact class name at
`JNI_OnLoad`. **Renaming or moving `Tun2Socks.kt` breaks the binding at load
time, not compile time** — so the script greps the built `.so` for the class
string and fails if it does not match.

### Routing

Private address space bypasses the tunnel using literal CIDRs rather than
`geoip:private`, so no `geoip.dat` has to be shipped or loaded. IPv6 is routed
into the tunnel deliberately: leaving it out would let a device with working IPv6
send that traffic straight out the physical interface, around the tunnel.

## Subscriptions

The **JSON subscription** (`<url>/json`) is tried first, with the base64 body as
fallback. This order is load-bearing, not a preference: Remnawave lets a host
carry a raw XRAY JSON override — a balancer across a dozen outbounds, its own DNS
and routing rules — and the base64 format flattens every host to a single URI,
discarding all of it. A host whose override is a VLESS balancer appears in base64
as one `ss://` placeholder that cannot work. Both endpoints return the same
`subscription-userinfo` and `profile-title` headers, so nothing is lost.

A node from the JSON format keeps its **entire config verbatim**; the tunnel
replaces only the inbound (`XrayConfigBuilder.fromPanelConfig`). The panel's DNS,
routing and balancers are usually better tuned than anything generated here.

### Geodata is required

`geoip.dat` and `geosite.dat` ship in `vpn/src/main/assets/geo/` because every
panel config references `geosite:`/`geoip:` rules (`category-ru`, `geoip:ru`,
`youtube`, `vk`), and the core refuses to load a config whose routing rules it
cannot resolve. Two ordering constraints, in `XrayAssets`:

1. `XRAY_LOCATION_ASSET` must be set **before any libXray class is touched** — the
   Go runtime snapshots the environment when `libgojni.so` loads, so setting it
   later is invisible to the core.
2. The ~24 MB extraction only has to finish before a config loads, so it runs off
   the main thread.

`GET <subscriptionUrl>` with the Remnawave device headers:

```
x-hwid: <salted SHA-256 of ANDROID_ID, laid out as a UUID>
x-device-os: Android
x-ver-os: <Build.VERSION.RELEASE>
x-device-model: <manufacturer> <model>
```

The HWID is **derived from the device**: `ANDROID_ID`, hashed with this app's
salt and laid out as a UUID (`HardwareId`, the same scheme as the macOS client).
Android keeps `ANDROID_ID` per device, per user and per signing key, and it
survives everything short of a factory reset — so the panel counts one phone as
one device however often the app is reinstalled. The identifier itself never
leaves the device; only its hash does.

It used to be a random UUID minted on first launch and stored in DataStore,
which every reinstall and every cleared-data minted afresh, each taking another
slot of the device limit. That is now only the fallback for a device whose
`ANDROID_ID` cannot be read. Because the signing key is part of what
`ANDROID_ID` is scoped to, a locally signed build and a release build of the
same app are two devices to the panel; builds signed with the same key are one.

Four body shapes are accepted, detected by content rather than `Content-Type`
(panels mislabel it): Remnawave JSON, a bare JSON array, base64 (standard or
URL-safe, padded or not), and plain text. `subscription-userinfo` and
`profile-title` response headers take precedence over the body, field by field,
because they are what every panel implements consistently. A missing field reads
as "unknown" rather than zero.

## Latency probing

`LatencyProbe` measures every node with libXray's `pingBatch`, **whether or not
the tunnel is up**.

`pingBatch` builds an instance of its own from the outbounds alone — no
inbounds, no routing, so no geodata — and dials through each outbound directly.
It is not the global core `RunXray` manages, so it neither needs nor disturbs
the instance carrying the tunnel. The probe used to borrow that global core
(each node on its own SOCKS inbound of a merged config), which meant a full pass
could only run with the tunnel down; once connected, the Ping button measured
the node in use and left every other row as it was.

Each node is one `pingBatch` call, eight nodes at a time, and its result is
published the moment it lands. A plain node is timed through its primary
outbound. A balancer node is timed through the outbounds its balancer selects
between — up to five, the most one batch takes — and is as fast as its fastest,
since that is where the balancer sends traffic.

The figure is a fresh connection: the handshake with the node and one request
through it, to `http://cp.cloudflare.com/generate_204` with a five second
timeout — the same target and timeout as the macOS client, so one server reads
the same on both. Plain http on purpose: a TLS handshake to the *target* is a
round trip that says nothing about the node. The colour steps are the Mac's too:
under 150 ms, under 300 ms, slower.

**A measurement stays until a newer one replaces it.** Starting a pass blanks
nothing: a row being measured shows its last figure, dimmed, until the new one
arrives, and a node that did not answer reads `n/a` rather than looking never
checked. The figures are written to `files/latencies.json` at the end of a pass
and read back at launch, so they outlive the process; a subscription refresh
drops only those of nodes that are gone.

The outbounds are handed to the core as files, which carry the node's
credentials: they are written to the app's private cache and deleted as soon as
the core has read them.

The core keeps the socket-protect callback for the life of the process and
fails any dial it returns false for. `VpnService.protect` is honoured only for
the app currently holding the VPN, so with the tunnel down a refusal is not
treated as one — there is no tunnel of ours to stay out of.

## Battery

The tunnel is up for hours, mostly with the screen off, so nothing about it may
be unconditional.

- **Traffic counters are polled only while something is showing them.** The
  service watches the counter flow's subscription count; with the connect screen
  off, nothing asks and nothing polls. It used to be a wake-up every second for
  the whole session.
- **The connection time ticks in the label that shows it**, from the timestamp
  the session started, and only while the screen is at least started. It used
  to be a ticker that rebuilt the whole screen's state once a second, connected
  or not.
- **No animation runs unless something is changing.** The old dial's halo
  redrew every frame for as long as the screen was up, and every spinner kept a
  repeating animation alive while idle. The moon is drawn once per state; the
  orbit and the spinners exist only while something is in flight.
- **The core's log is trimmed only at debug level**, the one level at which it
  grows. At the default it was being measured twice a minute for nothing.
- **A latency pass starts no core and loads no geodata**, and runs only when
  asked for — on a tap, or once after importing a subscription.
- **The canvas is true black**, which on an OLED panel is pixels switched off.

## Split tunnelling

| Mode | `VpnService.Builder` |
|---|---|
| All traffic | `addDisallowedApplication(self)` |
| Only these | `addAllowedApplication` per selection |
| Except these | `addDisallowedApplication` per selection, plus self |

This app is always excluded: it must reach the panel to refresh a subscription
while connected. An empty "only these" list falls back to tunnelling everything,
because an empty allow list routes nothing at all and reads as a broken VPN
rather than as a configuration choice.

## Design system

Black and white, as on the Mac. A black canvas, near-black surfaces told apart
by a step of grey and a hairline, white type, and white as the one interactive
colour — black, in the light theme. Colour is spent in two places only: the
logo's lime `#D2FF1F`, which is the brand, and the small signals that carry
meaning — latency, errors, warnings.

The token names are the ones every screen was written against; what they resolve
to is what changed. The accent keeps its three roles:

- `accent` — fills (buttons, the plan card, active pills)
- `accentInk` — accent as type or a glyph
- `accentLine` — accent as a thin mark (bars, dots, rings)

The tile behind a row's glyph is one quiet grey, and the glyph on it is drawn in
`text` — `textOnAccent` is for accent fills only. A switch's knob is the opposite
of its track: the accent is white in the dark theme, and a white knob on a white
track is a switch with no knob.

Everything that moves is on one spring (`MlMotion.standard`, the Mac's
`spring(response: 0.5, dampingFraction: 0.9)`), with no overshoot; colour and
press changes use one short fade.

Icons are **lucide 0.468.0**, the set the design is drawn with, carried across as
raw SVG path data (`design/.../Icons.kt`) rather than redrawn or swapped for
Material equivalents, so stroke geometry is identical. Non-path elements were
converted to path commands at generation time.

Fonts are Onest (UI/body) and Unbounded (display) as variable TTFs from Google
Fonts — the design ships `woff2`, which Android cannot load. Weights are named
instances on the `wght` axis.

The connect control is **the moon from the logo**: a dim crescent with its two
stars when off, a full moon lit in the logo's lime when on, and a thin orbit
turning round it while the tunnel changes state. Above it is the connection
time, below it the state in words. Opening the server list shrinks the moon and
gives the list the room that frees.

## Localisation

Russian and English, `values-ru` over a default `values`. The in-app switch uses
`AppCompatDelegate.setApplicationLocales`, the backport that also works on API
26–32 where `LocaleManager` does not exist.

`bundle { language { enableSplit = false } }` is **required**: with per-language
delivery, an App Bundle would install only the device's language and the in-app
switch would have no strings for the other one.

## Play Store notes

- **VPN disclosure.** A `VpnService` app needs the VPN disclosure in Play Console
  and a published privacy policy.
- **`QUERY_ALL_PACKAGES`** is used to list apps for split tunnelling and requires
  a declaration form. The list is filtered to packages that hold `INTERNET`.
- **Foreground service type.** Android has no VPN-specific type, so the service
  declares `specialUse` with a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explaining why.
- **APK size.** `libgojni.so` is ~50 MB per ABI, so release builds are split per
  ABI (`splits.abi`); a device downloads ~42 MB rather than 123 MB.
- **`REQUEST_INSTALL_PACKAGES`** is used by the in-app updater in Settings, which
  installs a build downloaded from this repository's releases. Play restricts the
  permission to app stores and file managers, so a Play build has to drop the
  updater along with it.

## Deep links

The app registers the `moonlight://` scheme, so a bot or web page can hand it a
subscription or toggle the tunnel:

| Link | Effect |
|---|---|
| `moonlight://import?url=<subscription url>` | Adds the subscription and shows the result |
| `moonlight://import/<url-encoded subscription url>` | Same, url in the path |
| `moonlight://connect` | Connects, asking for VPN consent on first run |
| `moonlight://disconnect` | Disconnects |
| `moonlight://open` | Opens the app |

`sub`, `subscription` and `link` are accepted as aliases for `url`, and the
value may be percent-encoded. Only `http`/`https` targets are accepted — a deep
link must not be able to point the import flow at something that is not a
subscription endpoint. A bare host gains `https://`.

Import **submits automatically** rather than only filling the field, because the
design promises a link from the bot "adds itself". The subscription screen shows
what was added and can delete it.

`https://` App Links for a panel domain are not registered; that needs a
`assetlinks.json` hosted on the domain and is a deployment decision.

## Configuration

`app/build.gradle.kts` `buildConfigField`s, not hardcoded literals:

| Field | Purpose |
|---|---|
| `DEFAULT_PANEL_HOST` | Placeholder host shown in the import field |
| `TELEGRAM_CHANNEL_URL` | Settings → Our channel |
| `CABINET_URL` | Subscription → Extend subscription |
| `SUPPORT_URL` | Settings → Support |

The design's `sub.moonlight.vpn` is a placeholder; point these at real endpoints
before shipping.

## Tests

```bash
./gradlew testDebugUnitTest
```

55 JVM tests, no device needed, covering the parts where correctness is not
visual: `subscription-userinfo` parsing (partial, malformed, absent), all four
subscription body shapes, share-link metadata and stable ids, Xray config
assembly (including the `sendThrough` strip), split-mode → package sets, and the
byte/day/duration formatters against the design's own strings.

## Known limitations

- **Not verified end-to-end.** The project compiles, the APK packages both native
  libraries, and the unit tests pass. An actual tunnel carrying traffic has not
  been exercised — that needs a physical device and a live subscription.
- Pasting a bare `vless://` link imports nothing; the import path expects a
  subscription URL. Single-node import is not implemented.
- `ThemeMode.System` exists in the model but the settings UI offers only
  Dark/Light, matching the design.
- Reconnect-on-network-change is not implemented; `ConnectionState.Reconnecting`
  is modelled but never entered.
