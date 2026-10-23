package os.tessera.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Collator
import java.util.Locale

/**
 * Список застосунків, які система вважає запусканими з головного екрана.
 *
 * Фільтр — `MAIN` + `LAUNCHER` через `<queries>` у маніфесті, тому
 * `QUERY_ALL_PACKAGES` не потрібен.
 */
class AppRepository(private val packageManager: PackageManager) {

    fun load(context: Context): List<LauncherApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(intent, 0)
        val collator = Collator.getInstance(Locale.forLanguageTag("uk"))

        return resolved.asSequence()
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activity.packageName, activity.name)
                LauncherApp(
                    id = LauncherApp.idOf(component),
                    label = info.loadLabel(packageManager).toString(),
                    component = component,
                    icon = runCatching { info.loadIcon(packageManager) }.getOrNull(),
                ).takeIf { activity.exported || activity.packageName == context.packageName }
            }
            .distinctBy { it.id }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }
}
