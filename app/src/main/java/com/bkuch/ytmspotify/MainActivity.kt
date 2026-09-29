package com.bkuch.ytmspotify

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings + setup instructions. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var clientId: EditText
    private lateinit var clientSecret: EditText
    private lateinit var linkStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<Switch>(R.id.enabled).apply {
            isChecked = prefs.enabled
            setOnCheckedChangeListener { _, checked -> prefs.enabled = checked }
        }
        findViewById<Switch>(R.id.handle_regular).apply {
            isChecked = prefs.handleRegularYouTube
            setOnCheckedChangeListener { _, checked -> prefs.handleRegularYouTube = checked }
        }

        val delayLabel = findViewById<TextView>(R.id.delay_label)
        findViewById<SeekBar>(R.id.delay).apply {
            max = 10
            progress = prefs.delayMs / 500
            delayLabel.text = delayText(progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    prefs.delayMs = value * 500
                    delayLabel.text = delayText(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        clientId = findViewById(R.id.client_id)
        clientSecret = findViewById(R.id.client_secret)
        clientId.setText(prefs.spotifyClientId)
        clientSecret.setText(prefs.spotifyClientSecret)

        linkStatus = findViewById(R.id.link_status)
        findViewById<Button>(R.id.open_link_settings).setOnClickListener { openLinkSettings() }

        val testUrl = findViewById<EditText>(R.id.test_url)
        findViewById<Button>(R.id.paste).setOnClickListener {
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            YouTubeLink.extractUrl(text)?.let { testUrl.setText(it) }
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            val url = YouTubeLink.extractUrl(testUrl.text.toString())
            if (url == null) {
                Toast.makeText(this, R.string.no_link, Toast.LENGTH_SHORT).show()
            } else {
                saveCredentials()
                startActivity(Intent(this, HijackActivity::class.java).setAction(Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(Intent.EXTRA_TEXT, url))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        linkStatus.text = describeLinkHandling()
    }

    override fun onPause() {
        super.onPause()
        saveCredentials()
    }

    private fun saveCredentials() {
        prefs.spotifyClientId = clientId.text.toString()
        prefs.spotifyClientSecret = clientSecret.text.toString()
    }

    private fun delayText(steps: Int): String =
        getString(R.string.delay_label, steps * 0.5)

    private fun openLinkSettings() {
        val pkg = Uri.parse("package:$packageName")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
    }

    private fun describeLinkHandling(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return getString(R.string.link_status_legacy)
        }
        val state = try {
            getSystemService(DomainVerificationManager::class.java)
                ?.getDomainVerificationUserState(packageName)
        } catch (e: Exception) {
            null
        } ?: return getString(R.string.link_status_unknown)

        val lines = state.hostToStateMap.toSortedMap().map { (host, s) ->
            val on = s == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                s == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            (if (on) "✅ " else "❌ ") + host
        }
        val header = if (state.isLinkHandlingAllowed) "" else getString(R.string.link_handling_off) + "\n"
        return header + lines.joinToString("\n")
    }
}
