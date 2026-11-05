package com.example.autotap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.Toast
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.max

class AutoService : AccessibilityService() {

    class Step(val delay: Long, val dur: Long, val pts: List<Float>)

    companion object {
        var instance: AutoService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var overlayLp: WindowManager.LayoutParams? = null
    private var bubble: View? = null

    private val recorded = ArrayList<Step>()
    private var recording = false
    private var playing = false
    private var lastEnd = 0L
    private val cur = ArrayList<Float>()
    private var curDelay = 0L

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopAll()
        instance = null
        return super.onUnbind(intent)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun startRecording() {
        stopAll()
        recorded.clear()
        recording = true
        lastEnd = 0L

        val v = View(this)
        v.setBackgroundColor(0x1AFF0000)
        v.setOnTouchListener { _, e -> onRecordTouch(e) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        overlay = v
        overlayLp = lp
        showBubble("⏹ রেকর্ড শেষ করুন")
        toast("রেকর্ড চালু। এখন যা করার করুন।")
    }

    private fun onRecordTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cur.clear()
                cur.add(e.rawX)
                cur.add(e.rawY)
                curDelay = if (recorded.isEmpty()) 0L else max(0L, e.downTime - lastEnd)
            }
            MotionEvent.ACTION_MOVE -> {
                if (cur.size in 2..119) {
                    val lx = cur[cur.size - 2]
                    val ly = cur[cur.size - 1]
                    if (abs(e.rawX - lx) + abs(e.rawY - ly) > 25f) {
                        cur.add(e.rawX)
                        cur.add(e.rawY)
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (cur.size >= 2) {
                    cur.add(e.rawX)
                    cur.add(e.rawY)
                    val step = Step(curDelay, max(60L, e.eventTime - e.downTime), ArrayList(cur))
                    recorded.add(step)
                    lastEnd = e.eventTime
                    passThrough(step)
                }
            }
        }
        return true
    }

    private fun passThrough(s: Step) {
        val v = overlay ?: return
        val lp = overlayLp ?: return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        val restore: () -> Unit = {
            if (recording) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
            }
        }
        val ok = dispatchGesture(gesture(s), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { restore() }
            override fun onCancelled(g: GestureDescription?) { restore() }
        }, null)
        if (!ok) restore()
    }

    fun startPlay(waitMinutes: Int, repeat: Int) {
        val steps = load()
        if (steps.isEmpty()) {
            toast("আগে রেকর্ড করুন")
            return
        }
        stopAll()
        playing = true
        showBubble("⏹ বন্ধ করুন")
        if (waitMinutes > 0) toast("$waitMinutes মিনিট পরে শুরু হবে")
        handler.postDelayed({ runStep(steps, 0, repeat) }, waitMinutes * 60_000L)
    }

    private fun runStep(steps: List<Step>, i: Int, left: Int) {
        if (!playing) return
        if (i >= steps.size) {
            if (left == 0 || left > 1) {
                handler.postDelayed({ runStep(steps, 0, if (left == 0) 0 else left - 1) }, 1500)
            } else {
                stopAll()
                toast("কাজ শেষ")
            }
            return
        }
        val s = steps[i]
        handler.postDelayed({
            if (!playing) return@postDelayed
            val next: () -> Unit = { runStep(steps, i + 1, left) }
            val ok = dispatchGesture(gesture(s), object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { next() }
                override fun onCancelled(g: GestureDescription?) { next() }
            }, null)
            if (!ok) next()
        }, s.delay)
    }

    private fun gesture(s: Step): GestureDescription {
        val p = Path()
        p.moveTo(s.pts[0], s.pts[1])
        var moved = false
        var i = 2
        while (i + 1 < s.pts.size) {
            if (abs(s.pts[i] - s.pts[0]) + abs(s.pts[i + 1] - s.pts[1]) > 25f) moved = true
            i += 2
        }
        if (moved) {
            i = 2
            while (i + 1 < s.pts.size) {
                p.lineTo(s.pts[i], s.pts[i + 1])
                i += 2
            }
        }
        val d = s.dur.coerceIn(1L, 59000L)
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0L, d))
            .build()
    }

    private fun showBubble(text: String) {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { stopAll() }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.y = 200
        wm.addView(b, lp)
        bubble = b
    }

    fun stopAll() {
        if (recording && recorded.isNotEmpty()) {
            save()
            toast("${recorded.size} টি ধাপ সেভ হয়েছে")
        }
        recording = false
        playing = false
        handler.removeCallbacksAndMessages(null)
        overlay?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        overlay = null
        overlayLp = null
        bubble = null
    }

    private fun save() {
        val arr = JSONArray()
        for (s in recorded) {
            val o = JSONArray()
            o.put(s.delay)
            o.put(s.dur)
            val p = JSONArray()
            s.pts.forEach { p.put(it.toDouble()) }
            o.put(p)
            arr.put(o)
        }
        getSharedPreferences("autotap", Context.MODE_PRIVATE)
            .edit().putString("steps", arr.toString()).apply()
    }

    private fun load(): List<Step> {
        val str = getSharedPreferences("autotap", Context.MODE_PRIVATE)
            .getString("steps", null) ?: return emptyList()
        val out = ArrayList<Step>()
        val arr = JSONArray(str)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONArray(i)
            val p = o.getJSONArray(2)
            val pts = ArrayList<Float>()
            for (j in 0 until p.length()) pts.add(p.getDouble(j).toFloat())
            out.add(Step(o.getLong(0), o.getLong(1), pts))
        }
        return out
    }

    fun stepCount(): Int = load().size
}
