package com.callgalli.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.callgalli.R
import com.callgalli.rules.TimerBand
import com.callgalli.rules.TimerDisplay
import kotlin.math.abs

/**
 * Small floating pill ("07:42 left") shown on top of the phone's own call screen,
 * plus a full-width banner at the 60 s / 30 s warnings.
 * Needs the "Display over other apps" permission; without it only the notification shows.
 */
class TimerOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var pill: TextView? = null
    private var banner: LinearLayout? = null
    private var pillParams: WindowManager.LayoutParams? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    fun update(state: InCallState) {
        val call = state.call
        val remaining = call?.remainingMillis
        if (call == null || remaining == null || call.band == null || !canShow()) {
            hide(); return
        }
        showPill(context.getString(R.string.pill_left, TimerDisplay.mmss(remaining)), call.band)
        val b = state.banner
        if (b != null) showBanner(b) else removeBanner()
    }

    fun hide() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
        removeBanner()
    }

    // ---- Pill -----------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showPill(text: String, band: TimerBand) {
        val (bg, fg) = colors(band)
        val view = pill ?: TextView(context).also { tv ->
            tv.textSize = 18f
            tv.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            val h = dp(16); val v = dp(8)
            tv.setPadding(h, v, h, v)
            tv.elevation = dp(6).toFloat()
            val params = baseParams(WindowManager.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48)
            }
            // Drag it out of the way of the call buttons.
            var startY = 0; var touchY = 0f
            tv.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startY = params.y; touchY = e.rawY; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = (e.rawY - touchY).toInt()
                        if (abs(dy) > 4) { params.y = (startY + dy).coerceAtLeast(0); runCatching { wm.updateViewLayout(tv, params) } }
                        true
                    }
                    else -> false
                }
            }
            if (runCatching { wm.addView(tv, params) }.isFailure) return
            pill = tv
            pillParams = params
        }
        view.text = text
        view.setTextColor(fg)
        view.background = GradientDrawable().apply { cornerRadius = dp(50).toFloat(); setColor(bg) }
    }

    // ---- Banner ---------------------------------------------------------------------------

    private fun showBanner(b: WarningBanner) {
        val (bg, fg) = colors(if (b.secondsLeft <= 30) TimerBand.RED else TimerBand.AMBER)
        val view = banner ?: LinearLayout(context).also { layout ->
            layout.orientation = LinearLayout.VERTICAL
            val p = dp(16)
            layout.setPadding(p, dp(36), p, dp(12))
            layout.addView(TextView(context).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD })
            layout.addView(TextView(context).apply { textSize = 16f })
            val params = baseParams(WindowManager.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.TOP }
            if (runCatching { wm.addView(layout, params) }.isFailure) return
            banner = layout
        }
        view.setBackgroundColor(bg)
        (view.getChildAt(0) as TextView).apply { setTextColor(fg); text = context.getString(R.string.warning_title, b.secondsLeft) }
        (view.getChildAt(1) as TextView).apply { setTextColor(fg); text = context.getString(R.string.warning_body, b.label) }
    }

    private fun removeBanner() {
        banner?.let { runCatching { wm.removeView(it) } }
        banner = null
    }

    // ---- Helpers --------------------------------------------------------------------------

    private fun baseParams(width: Int) = WindowManager.LayoutParams(
        width,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun colors(band: TimerBand): Pair<Int, Int> {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when (band) {
            TimerBand.GREEN -> if (dark) 0xFF7BC67E.toInt() to 0xFF00390A.toInt() else 0xFF2E7D32.toInt() to 0xFFFFFFFF.toInt()
            TimerBand.AMBER -> if (dark) 0xFFFFC94D.toInt() to 0xFF3D2A00.toInt() else 0xFFF2A600.toInt() to 0xFF2B1D00.toInt()
            TimerBand.RED -> if (dark) 0xFFFF8A80.toInt() to 0xFF5C0A0A.toInt() else 0xFFC62828.toInt() to 0xFFFFFFFF.toInt()
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
}
