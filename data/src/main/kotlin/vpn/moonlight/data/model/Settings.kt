package vpn.moonlight.data.model

/** How the tunnel treats individual apps. Mirrors the three-way control in the design. */
enum class SplitMode {
    /** Everything on the device goes through the tunnel. */
    All,

    /** Only the selected apps are tunnelled; everything else goes direct. */
    OnlySelected,

    /** The selected apps go direct; everything else is tunnelled. */
    ExceptSelected,
}

enum class ThemeMode { Dark, Light, System }

enum class AppLanguage(val tag: String) {
    Russian("ru"),
    English("en"),
}

/** User preferences, persisted in DataStore. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val language: AppLanguage = AppLanguage.Russian,
    val notificationsEnabled: Boolean = true,
    val splitMode: SplitMode = SplitMode.All,
    val splitPackages: Set<String> = emptySet(),
    val selection: NodeSelection = NodeSelection.Auto,
    val onboardingComplete: Boolean = false,
    /** Raises xray-core's own log level to debug, for diagnosing a report. */
    val verboseLogging: Boolean = false,
)

/** An installed app that can be tunnelled. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)
