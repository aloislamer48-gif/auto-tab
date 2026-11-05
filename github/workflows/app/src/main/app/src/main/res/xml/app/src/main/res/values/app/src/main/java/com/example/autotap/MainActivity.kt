package com.example.autotap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        status = TextView(this)
        status.textSize = 16f
        root.addView(status)

        root.addView(btn("১. Accessibility চালু করুন") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(btn("২. রেকর্ড শুরু করুন") {
            val s = svc() ?: return@btn
            s.startRecording()
            home()
        })

        val repeat = EditText(this)
        repeat.hint = "কতবার চালাবে (০ = বন্ধ না করা পর্যন্ত)"
        repeat.inputType = InputType.TYPE_CLASS_NUMBER
        repeat.setText("1")
        root.addView(repeat)

        val wait = EditText(this)
        wait.hint = "কত মিনিট পরে শুরু হবে (০ = এখনই)"
        wait.inputType = InputType.TYPE_CLASS_NUMBER
        wait.setText("0")
        root.addView(wait)

        root.addView(btn("৩. প্লে করুন") {
            val s = svc() ?: return@btn
            if (s.stepCount() == 0) {
                toast("আগে রেকর্ড করুন")
                return@btn
            }
            s.startPlay(
                wait.text.toString().toIntOrNull() ?: 0,
                repeat.text.toString().toIntOrNull() ?: 1
            )
            home()
        })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val s = AutoService.instance
        status.text = if (s == null) {
            "Accessibility এখনো চালু নেই"
        } else {
            "চালু আছে ✓   সেভ করা ধাপ: ${s.stepCount()}"
        }
    }

    private fun btn(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.setOnClickListener { onClick() }
        return b
    }

    private fun svc(): AutoService? {
        val s = AutoService.instance
        if (s == null) toast("আগে ১ নম্বর বোতাম দিয়ে Accessibility চালু করুন")
        return s
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun home() {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
    }
}
