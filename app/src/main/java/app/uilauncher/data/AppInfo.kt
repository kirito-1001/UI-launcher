package app.uilauncher.data

/** One launchable app. [key] is "package/activity" and identifies the app everywhere (layout, settings). */
data class AppInfo(
    val key: String,
    val label: String,
    val packageName: String,
    val className: String,
)
