package client.wexmanager.core.apk

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.RootShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Установленный пакет: и с лаунчером, и без. */
@Immutable
data class ComponentPackage(
    val packageName: String,
    val label: String,
    val launchable: Boolean,
)

enum class ComponentKind(val title: String) {
    Activity("Активность"),
    Service("Служба"),
    Receiver("Приёмник"),
    Provider("Провайдер"),
}

/** Компонент из PackageInfo. [enabled] — фактическое состояние, не только флаг манифеста. */
@Immutable
data class ComponentRecord(
    val className: String,
    val kind: ComponentKind,
    val enabled: Boolean,
) {
    val listKey: String get() = "${kind.ordinal}:$className"
}

object PackageComponents {

    private const val COMPONENT_FLAGS = PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS or
        PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS

    /** Все установленные пакеты, включая те, у которых нет CATEGORY_LAUNCHER. */
    suspend fun loadPackages(pm: PackageManager): List<ComponentPackage> = withContext(Dispatchers.IO) {
        val apps = installedApplications(pm)
        val launchers = runCatching { launcherPackages(pm) }.getOrDefault(emptySet())
        val chunk = (apps.size / 6).coerceAtLeast(16)
        val loaded = coroutineScope {
            apps.chunked(chunk).map { part ->
                async {
                    part.mapNotNull { ai ->
                        coroutineContext.ensureActive()
                        val packageName = ai.packageName
                        if (packageName.isNullOrBlank()) return@mapNotNull null
                        val label = runCatching { ai.loadLabel(pm).toString() }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: packageName
                        ComponentPackage(packageName, label, packageName in launchers)
                    }
                }
            }.awaitAll().flatten()
        }
        loaded
            .distinctBy { it.packageName }
            .sortedWith(compareBy<ComponentPackage> { it.label.lowercase() }.thenBy { it.packageName })
    }

    suspend fun loadComponents(pm: PackageManager, packageName: String): List<ComponentRecord> = withContext(Dispatchers.IO) {
        val info = try {
            packageInfo(pm, packageName, COMPONENT_FLAGS)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException("Пакет не найден", e)
        }
        val out = ArrayList<ComponentRecord>()
        collect(out, pm, packageName, info.activities, ComponentKind.Activity)
        collect(out, pm, packageName, info.services, ComponentKind.Service)
        collect(out, pm, packageName, info.receivers, ComponentKind.Receiver)
        collect(out, pm, packageName, info.providers, ComponentKind.Provider)
        out.distinctBy { it.listKey }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.className.lowercase() }))
    }

    /**
     * null — состояние изменено. Иначе русский текст ошибки.
     * Чужой пакет обычно отвечает SecurityException; тогда, если root включён, вызывается `pm`.
     */
    suspend fun setEnabled(
        pm: PackageManager,
        packageName: String,
        className: String,
        enabled: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        val cn = ComponentName(packageName, className)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        try {
            pm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            viaRoot(cn, enabled, e)
        } catch (e: Exception) {
            message(e.message, "Не удалось изменить компонент")
        }
    }

    private suspend fun viaRoot(cn: ComponentName, enabled: Boolean, denied: SecurityException): String? {
        if (!RootShell.enabled) return message(denied.message, "Нет прав изменить компонент")
        val verb = if (enabled) "enable" else "disable"
        val cmd = "pm $verb ${RootShell.quote(cn.flattenToString())}"
        val result = try {
            RootShell.run(cmd)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return message(e.message, "Root недоступен")
        }
        if (result.code == 0) return null
        val detail = sequenceOf(result.err, result.out).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return message(detail, "pm $verb завершился с кодом ${result.code}")
    }

    private fun collect(
        into: MutableList<ComponentRecord>,
        pm: PackageManager,
        packageName: String,
        items: Array<out ComponentInfo>?,
        kind: ComponentKind,
    ) {
        items?.forEach { info ->
            val name = info.name?.takeIf { it.isNotBlank() } ?: return@forEach
            into += ComponentRecord(name, kind, isEnabled(pm, packageName, name, info.enabled))
        }
    }

    /** DEFAULT — в манифесте ещё нет переопределения, берём текущий android:enabled. */
    private fun isEnabled(pm: PackageManager, packageName: String, className: String, manifestEnabled: Boolean): Boolean {
        val state = runCatching { pm.getComponentEnabledSetting(ComponentName(packageName, className)) }
            .getOrElse { return manifestEnabled }
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else -> manifestEnabled
        }
    }

    @Suppress("DEPRECATION")
    private fun launcherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.queryIntentActivities(intent, flags)
        }
        return resolved.mapNotNullTo(HashSet()) { info ->
            info.activityInfo?.packageName?.takeIf { it.isNotBlank() }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(pm: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }

    private fun message(detail: String?, fallback: String): String {
        val clean = detail?.replace(Regex("\\s+"), " ")?.trim()?.take(220).orEmpty()
        return if (clean.isEmpty() || clean == fallback) fallback else "$fallback: $clean"
    }
}
