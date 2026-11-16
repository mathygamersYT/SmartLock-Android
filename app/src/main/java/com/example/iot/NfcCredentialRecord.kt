package com.example.iot

import android.nfc.NdefRecord

/** Public identification metadata, not a password or proof of authorization. */
object NfcCredentialRecord {
    const val MIME = "application/vnd.smartlock.credential"
    fun create(uid: String): NdefRecord {
        require(AccessPolicy.normalizeUid(uid) == uid)
        return NdefRecord.createMime(MIME, ("SMARTLOCK:1:" + uid).toByteArray(Charsets.US_ASCII))
    }
    fun matches(record: NdefRecord, physicalUid: String): Boolean =
        record.tnf == NdefRecord.TNF_MIME_MEDIA &&
            record.type.contentEquals(MIME.toByteArray(Charsets.US_ASCII)) &&
            record.payload.contentEquals(("SMARTLOCK:1:" + physicalUid).toByteArray(Charsets.US_ASCII))
}
