package com.vishnu.kohliprotocol.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** Declared as a game by the app itself (appCategory="game" or the legacy isGame flag). */
    val isGame: Boolean,
    val icon: Bitmap?,
)

/** Lists apps the user can launch, so a game can be picked instead of typing a package name. */
class InstalledAppsSource(context: Context) {

    private val appContext = context.applicationContext

    /** Games first, then alphabetical. Excludes Kohli Protocol itself. */
    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val iconPx = (ICON_DP * appContext.resources.displayMetrics.density).toInt()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION") // the flags-as-Int overload is fine on every supported API level
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != appContext.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isGame = isGame(info),
                    icon = runCatching { pm.getApplicationIcon(info).toBitmap(iconPx, iconPx) }.getOrNull(),
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.isGame }.thenBy { it.label.lowercase() })
    }

    @Suppress("DEPRECATION") // FLAG_IS_GAME: older games only set this
    private fun isGame(info: ApplicationInfo): Boolean =
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    private companion object {
        const val ICON_DP = 40
    }
}
