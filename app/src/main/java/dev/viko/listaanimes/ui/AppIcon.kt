package dev.viko.listaanimes.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.viko.listaanimes.data.settings.Accent

/** Troca o ícone do lançador ativando um dos activity-alias do manifesto. */
object AppIcon {
    private fun alias(accent: Accent, dark: Boolean) = ".Icon_${accent.name.lowercase()}_${if (dark) "d" else "l"}"

    fun apply(context: Context, accent: Accent, dark: Boolean) {
        val pm = context.packageManager
        val target = alias(accent, dark)
        val all = Accent.entries.flatMap { a -> listOf(alias(a, false), alias(a, true)) }
        val name = { a: String -> ComponentName(context.packageName, context.packageName + a) }
        // Nada a fazer se já é o ativo (evita o lançador recarregar à toa).
        val current = all.firstOrNull { a ->
            when (pm.getComponentEnabledSetting(name(a))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a == alias(Accent.TERRACOTA, false)
                else -> false
            }
        }
        if (current == target) return
        pm.setComponentEnabledSetting(name(target), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        all.filter { it != target }.forEach {
            pm.setComponentEnabledSetting(name(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
