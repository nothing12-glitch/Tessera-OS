package os.tessera.launcher.data

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * Один застосунок, який можна запустити з головного екрана чи шухляди.
 * [id] стабільний між запусками, тому ним і звʼязуємо іконку з коміркою сітки.
 */
data class LauncherApp(
    val id: String,
    val label: String,
    val component: ComponentName,
    val icon: Drawable?,
) {
    val launchIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = this@LauncherApp.component
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }

    companion object {
        fun idOf(component: ComponentName) = "${component.packageName}|${component.className}"
    }
}
