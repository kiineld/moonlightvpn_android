# Moonlight VPN for Android — design

Date: 2026-08-13
Status: approved

## Purpose

A Kotlin/Compose Android VPN client for the Moonlight service, implementing the
`Moonlight Client.dc.html` design. Traffic is carried by Xray-core 26.7.28 over
VLESS Reality. Subscriptions come from a Remnawave panel, imported by QR code,
clipboard, manual URL, or a Telegram bot link.

## Non-goals

- No account creation, payment, or plan management in-app. Purchase and renewal
  happen in the bot or web cabinet; the app only consumes a subscription URL.
- No custom protocol/node editing. The panel is the source of truth for nodes.
- No hardware-derived device identifiers.

## Native stack

| Layer | Artifact | Version |
|---|---|---|
| Proxy core | `XTLS/libXray` gomobile AAR (wraps Xray-core) | v26.7.28 |
| tun → socks | `heiher/hev-socks5-tunnel`, built from source as a JNI library | 2.17.1 |

Data path:

```
tun fd → libtun2socks (child process) → SOCKS5 127.0.0.1:<port> → xray-core → VLESS Reality node
```

**Correction to the original plan.** This was specified as a prebuilt binary
dropped into `jniLibs`. That does not work: hev-socks5-tunnel's published Android
release artifacts are the standalone executable, whose usage is
`hev-socks5-tunnel CONFIG_PATH`. It creates its own tun device from the config —
which needs root — and offers no way to accept the descriptor `VpnService` hands
us. Only the library build (`ENABLE_LIBRARY`) exposes
`hev_socks5_tunnel_main_from_file(path, fd)` and the JNI entry points.

So it is compiled from source by `scripts/build-tun2socks.sh` (Android NDK 27.3)
with `-DPKGNAME=vpn/moonlight/core/tunnel -DCLSNAME=Tun2Socks`. The native
library registers its four methods against that exact class name at
`JNI_OnLoad`, so renaming `Tun2Socks.kt` would break the binding at load time
rather than compile time; the build script greps the built `.so` for the class
string and fails if it is absent.

### Binding surface

`libXray.LibXray` exposes a single JSON bridge plus Android hooks. gomobile maps
Go `int` to Java `long`.

```java
static native String invoke(String requestJson);
static native void   registerDialerController(DialerController);
static native void   registerListenerController(DialerController);
static native void   registerProcessFinder(ProcessFinder, long sdkVersion);
static native void   setDNS(DialerController, String server);
static native void   resetDNS();
interface DialerController { boolean protectFd(long fd); }
```

Requests are `{"apiVersion":1,"method":...,"payload":{...}}`; responses are
`{"success":bool,"data":...,"error":string}`. Methods used: `runXrayFromJson`,
`stopXray`, `getXrayState`, `pingBatch`, `convertShareLinksToXrayJson`,
`getFreePorts`, `xrayVersion`.

Two consequences worth stating, because they remove code we would otherwise own:

1. `registerDialerController` is a direct `VpnService.protect(fd)` callback, so
   outbound sockets bypass the tunnel without a unix-socket protect helper. This
   is what prevents the routing loop.
2. `convertShareLinksToXrayJson` parses `vless://`, `vmess://`, `trojan://` and
   `ss://` inside the core. We build only the wrapper config (inbound, DNS,
   routing) and graft the parsed outbound in.

## Modules

```
:app      Compose screens, ViewModels, navigation, AppContainer (manual DI)
:design   Design system — colour/type/shape/motion tokens, components
:data     Remnawave client, share links, DataStore, repositories
:vpn      MoonlightVpnService, xray bridge, config builder, tun2socks, ping
```

`app → design, data, vpn` · `vpn → data` · `design` standalone. Manual DI via a
single `AppContainer` — no annotation processing.

## Screens

Six, matching the design, with a floating pill tab bar over the three roots.

| Screen | Notes |
|---|---|
| Onboarding | Logo, three feature rows, "Add subscription" / "Later" |
| Import | QR camera, clipboard paste, manual URL, Telegram bot row; success state |
| Connect | 244dp dial, session/remaining stats, quick-pick chips, node list |
| Subscription | Plan hero, traffic bar, sub link with copy/QR, refresh, extend, add |
| Settings | Split tunnelling entry, theme, language, notifications, support links |
| Split tunnelling | Three modes segmented control + per-app switches |

## Design system

Tokens map from the CSS one-for-one. Dark is lime `#D2FF1F` on slate `#101828`;
light flips the accent to yellow `#FFE078`. The accent splits into three roles
that must stay distinct because light mode depends on it:

- `accent` — fills (buttons, dial sweep, active pills)
- `accentInk` — accent used as type or a glyph (`#EFAE2E` in light)
- `accentLine` — accent as a thin mark (bars, dots, rings)

Motion carries over as real easings: `ease` = `cubic-bezier(.2,.7,.3,1)`,
`easeSlide` = `cubic-bezier(.5,1.28,.32,1)` for anything that slides into place,
and the three press scales `.985` (cards) / `.97` (buttons) / `.92` (icons).

Fonts: Onest for UI/body, Unbounded for display, both as variable TTFs from
Google Fonts (the design ships `woff2`, which Android cannot load). Unbounded
never appears below 15sp and never in running text.

## Subscription protocol

`GET <subscriptionUrl>` with Remnawave device headers:

```
x-hwid: <random UUID, minted once, stored in DataStore>
x-device-os: Android
x-ver-os: <Build.VERSION.RELEASE>
x-device-model: <manufacturer> <model>
```

HWID is a random UUID rather than an `ANDROID_ID` or IMEI: it still drives the
panel's device limit, but carries no hardware identity, which keeps it inside
Play's data-safety rules.

Two response shapes are accepted — Remnawave JSON, or a base64 list of share
links (any panel). `subscription-userinfo: upload=…; download=…; total=…;
expire=…` plus `profile-title` supply the plan hero and traffic bar. A missing
header degrades to "unknown" rather than a zero.

## Connection state

Owned by the service, exposed as one `StateFlow`:

```
Disconnected → Connecting → Connected(since: Instant) → Reconnecting → Error(reason)
```

The session timer derives from `since`, so it survives process death instead of
being counted in the UI. `Error` carries a typed reason for a real message.

## Split tunnelling

Three modes from the design map onto `VpnService.Builder`:

| Mode | Builder call |
|---|---|
| All traffic | neither |
| Only these | `addAllowedApplication` for each selected |
| Except these | `addDisallowedApplication` for each selected |

The app's own package is always excluded so it can reach the panel while the
tunnel is up. The app list is filtered to packages holding `INTERNET`.

## Testing

JVM unit tests, no device needed, for the parts where correctness is not visual:

- `subscription-userinfo` header parsing, including absent and malformed fields
- Remnawave JSON and base64 subscription decoding
- Xray config assembly: inbound port, DNS, routing, outbound grafting
- Split mode → allowed/disallowed package sets, incl. self-exclusion
- Byte, day and duration formatters against the design's strings

Not verifiable in this environment, and stated as such rather than implied: an
actual tunnel carrying traffic, which needs a device and a live subscription.

## Outcome

Built and verified in this environment:

- `./gradlew assembleDebug` succeeds; per-ABI APKs are ~42 MB.
- The APK carries `libgojni.so` (Xray-core) and `libhev-socks5-tunnel.so`, both
  variable fonts, and both locales.
- 55 JVM unit tests pass.

Not verified, and stated rather than implied: a tunnel actually carrying traffic,
which needs a device and a live subscription.

## Risks

- **Play policy.** A `VpnService` app needs the VPN disclosure and a privacy
  policy. Split tunnelling requires the installed-app list, which means
  `QUERY_ALL_PACKAGES` and its declaration form. Noted in the README.
- **APK size.** The AAR carries a ~50 MB `libgojni.so` per ABI. Release builds
  use ABI splits / App Bundle so a device downloads one.
- **`sub.moonlight.vpn` is a placeholder** from the design. The panel host is a
  `BuildConfig` field, not a hardcoded literal.
