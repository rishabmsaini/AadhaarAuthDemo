package com.finvu.aadhaardemo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/**
 * Stand-in for UIDAI's Aadhaar App.
 *
 * In production THIS screen is the real Aadhaar App: it shows the consent prompt, captures a live
 * selfie, runs the 1:1 face match ON THE DEVICE against the user's UIDAI-signed profile photo, and
 * returns a signed Verifiable Presentation. Finvu never sees the photo or the Aadhaar number.
 *
 * Here we only simulate that: a consent screen + a button that returns a MOCK (unsigned) VC so the
 * round-trip back into Finvu's WebView can be demonstrated without the real app installed.
 */
class MockAadhaarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1020"))
            setPadding(px(24), px(40), px(24), px(40))
        }

        fun label(text: String, size: Float, color: String, top: Int): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(Color.parseColor(color))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { topMargin = px(top) }
            }

        column.addView(label("\uD83D\uDEE1\uFE0F  Aadhaar App", 26f, "#FFFFFF", 0))
        column.addView(label("Simulated · stands in for UIDAI's app", 13f, "#7C879C", 4))

        column.addView(label("Finvu Account Aggregator requests", 16f, "#E7ECF6", 36))
        column.addView(
            label(
                "•  Name\n•  Date of birth\n•  Masked Aadhaar reference (last 4)\n•  Offline face-match result",
                15f, "#C4CDDE", 12
            )
        )

        column.addView(
            label(
                "Your live photo is matched on THIS device against your signed Aadhaar profile. " +
                    "Your photo and full Aadhaar number are not shared with Finvu.",
                13f, "#7C879C", 20
            )
        )

        val approve = Button(this).apply {
            text = "Simulate face scan & consent"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { topMargin = px(32) }
            setOnClickListener { returnToFinvu("SUCCESS") }
        }
        val deny = Button(this).apply {
            text = "Deny"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                .apply { topMargin = px(12) }
            setOnClickListener { returnToFinvu("FAILURE") }
        }
        column.addView(approve)
        column.addView(deny)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1020"))
            addView(column)
        })
    }

    /** Hand control back to Finvu via the return deep link with a MOCK verifiable presentation. */
    private fun returnToFinvu(status: String) {
        val mockVc = if (status == "SUCCESS") {
            JSONObject().apply {
                put("name", "Asha Kumar")
                put("dob", "1990-05-21")
                put("maskedRef", "XXXX-XXXX-4271")
                put("faceMatch", "PASS")
                put("issuer", "uidai-demo")
                // A real flow carries a UIDAI-signed JWS (SD-JWT) or an ISO mDOC, not this:
                put("sig", "MOCK_SIGNATURE_DO_NOT_TRUST")
            }.toString()
        } else ""

        val callback = android.net.Uri.parse("finvu://callback").buildUpon()
            .appendQueryParameter("status", status)
            .appendQueryParameter("vc", mockVc)
            .build()

        startActivity(
            Intent(Intent.ACTION_VIEW, callback).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
