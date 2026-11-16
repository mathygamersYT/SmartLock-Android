package com.example.iot

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Reads only a tag's UID during an explicit foreground scan. */
class NfcUidReader(private val activity: AppCompatActivity) : DefaultLifecycleObserver {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private var dialog: AlertDialog? = null
    @Volatile private var generation = 0
    @Volatile private var listening = false
    init { activity.lifecycle.addObserver(this) }

    fun read(onUid: (String) -> Unit) {
        scan(null, onUid)
    }

    fun write(uid: String, allowed: () -> Boolean, onWritten: (String) -> Unit) {
        AlertDialog.Builder(activity).setTitle(R.string.nfc_write)
            .setMessage(R.string.nfc_write_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> if (allowed()) scan(uid, onWritten, allowed) }.show()
    }

    private fun scan(expectedUid: String?, onUid: (String) -> Unit, allowed: () -> Boolean = { true }) {
        stop()
        val nfc = adapter
        if (nfc == null) { message(R.string.nfc_missing); return }
        if (!nfc.isEnabled) {
            AlertDialog.Builder(activity).setTitle(R.string.nfc_title)
                .setMessage(R.string.nfc_disabled).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.nfc_settings) { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }.show()
            return
        }
        val scan = ++generation
        val processing = java.util.concurrent.atomic.AtomicBoolean(false)
        listening = true
        dialog = AlertDialog.Builder(activity).setTitle(R.string.nfc_title)
            .setMessage(if (expectedUid == null) R.string.nfc_approach else R.string.nfc_write_approach).setNegativeButton(R.string.cancel, null)
            .create().also { it.setOnDismissListener { stop() }; it.show() }
        try {
            nfc.enableReaderMode(activity, { tag ->
                if (!processing.compareAndSet(false, true)) return@enableReaderMode
                if (!listening || generation != scan || !allowed()) return@enableReaderMode
                val uid = AccessPolicy.uidFromBytes(tag.id)
                val error = when {
                    uid == null -> R.string.nfc_incompatible
                    expectedUid != null && uid != expectedUid -> R.string.nfc_wrong_card
                    expectedUid != null -> writeTag(tag, uid)
                    else -> readStoredIdentity(tag, uid)
                }
                activity.runOnUiThread {
                    if (!listening || generation != scan || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    stop()
                    if (error != null) message(error) else if (uid != null) onUid(uid)
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B, Bundle().apply {
                    putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
                })
        } catch (_: RuntimeException) { stop(); message(R.string.nfc_error) }
    }

    private fun writeTag(tag: Tag, uid: String): Int? {
        val record = NfcCredentialRecord.create(uid)
        val message = NdefMessage(arrayOf(record))
        val ndef = Ndef.get(tag)
        try {
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) return R.string.nfc_read_only
                if (ndef.maxSize < message.toByteArray().size) return R.string.nfc_no_space
                ndef.writeNdefMessage(message)
                if (!ndef.ndefMessage.toByteArray().contentEquals(message.toByteArray()))
                    return R.string.nfc_write_failed
            } else {
                val blank = NdefFormatable.get(tag) ?: return R.string.nfc_not_ndef
                try {
                    blank.connect()
                    blank.format(message)
                } finally { try { blank.close() } catch (_: java.io.IOException) { } }
            }
            return null
        } catch (_: Exception) {
            return R.string.nfc_write_failed
        } finally { try { ndef?.close() } catch (_: java.io.IOException) { } }
    }

    private fun readStoredIdentity(tag: Tag, uid: String): Int? {
        val records = Ndef.get(tag)?.cachedNdefMessage?.records ?: return null
        val ownRecords = records.filter { it.tnf == NdefRecord.TNF_MIME_MEDIA &&
            it.type.contentEquals(NfcCredentialRecord.MIME.toByteArray(Charsets.US_ASCII)) }
        if (ownRecords.isEmpty()) return null // A blank card can still be registered by its physical UID.
        return if (ownRecords.size == 1 && NfcCredentialRecord.matches(ownRecords.single(), uid)) null
            else R.string.nfc_identity_mismatch
    }

    fun stop() {
        val wasListening = listening
        listening = false
        generation++
        if (wasListening) try { adapter?.disableReaderMode(activity) } catch (_: RuntimeException) { }
        val current = dialog
        dialog = null
        current?.setOnDismissListener(null)
        current?.dismiss()
    }
    private fun message(id: Int) {
        if (!activity.isFinishing && !activity.isDestroyed)
            AlertDialog.Builder(activity).setTitle(R.string.nfc_title).setMessage(id)
                .setPositiveButton(R.string.close, null).show()
    }
    override fun onPause(owner: LifecycleOwner) = stop()
    override fun onDestroy(owner: LifecycleOwner) { stop(); activity.lifecycle.removeObserver(this) }
}
