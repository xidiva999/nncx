package dev.nncx

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Deliberately stupid. No Compose, no theme tokens, no index, no coroutines,
 * no DataStore. Its only job is to still work when everything else does not,
 * so it must not share code with anything that can break.
 */
class FallbackActivity : Activity() {

    companion object {
        const val EXTRA_REASON = "reason"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A0C"))
            setPadding(dp(20), dp(56), dp(20), dp(24))
        }

        root.addView(label("NNCx — safe mode", 18f, "#FFFFFF", bold = true))
        root.addView(
            label(
                intent?.getStringExtra(EXTRA_REASON) ?: "home screen stopped",
                12f, "#FF6B6B"
            )
        )
        root.addView(label("the home screen crashed. everything below still launches.", 12f, "#8A8A94"))
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16))
        })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps().forEach { (name, intent) ->
            list.addView(
                label(name, 16f, "#E8E8EE").apply {
                    setPadding(0, dp(14), 0, dp(14))
                    isClickable = true
                    setOnClickListener { runCatching { startActivity(intent) } }
                }
            )
        }

        root.addView(ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
        })

        root.addView(label("settings → apps → default apps → home app, to switch back", 11f, "#5A5A64").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(root)
    }

    private fun apps(): List<Pair<String, Intent>> {
        val pm = packageManager
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(probe, 0)   // NOT MATCH_DEFAULT_ONLY — it hides most launchers
                .mapNotNull { ri ->
                    val ai = ri.activityInfo ?: return@mapNotNull null
                    if (ai.packageName == packageName) return@mapNotNull null
                    val name = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(ai.packageName)
                    name to Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ai.packageName, ai.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                .distinctBy { it.first }
                .sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun label(text: String, sp: Float, hex: String, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(hex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onBackPressed() { /* a home screen has nowhere to go back to */ }
}
