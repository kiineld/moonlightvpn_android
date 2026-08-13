# Xray-core's gomobile binding. Both halves are reached by name from Go.
-keep class go.** { *; }
-keep class libXray.** { *; }

# hev-socks5-tunnel registers its four native methods against this exact class
# name inside JNI_OnLoad. If R8 renames the class or its methods the binding
# fails at load time — and only in release builds, where it is hardest to spot.
# AGP's default rules happen to keep classes with native methods today; this
# states the requirement rather than depending on that.
-keep class vpn.moonlight.core.tunnel.Tun2Socks {
    native <methods>;
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
