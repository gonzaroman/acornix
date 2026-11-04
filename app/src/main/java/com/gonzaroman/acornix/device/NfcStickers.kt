package com.gonzaroman.acornix.device

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/** Writes a shortcut onto an NFC sticker: touching the sticker later opens it, even with Acornix closed. */
object NfcStickers {

    fun isSupported(context: Context) = NfcAdapter.getDefaultAdapter(context) != null

    fun isEnabled(context: Context) = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    /** The link plus an Android Application Record, so the sticker always opens Acornix and not a browser. */
    fun message(link: String, packageName: String) = NdefMessage(
        arrayOf(NdefRecord.createUri(link), NdefRecord.createApplicationRecord(packageName)),
    )

    enum class Result { WRITTEN, READ_ONLY, TOO_SMALL, FAILED }

    /** Waits for a sticker while [activity] is in front and writes [link] on it; [done] runs on a background thread. */
    fun startWriting(activity: Activity, link: String, done: (Result) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return done(Result.FAILED)
        val msg = message(link, activity.packageName)
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(activity, { tag -> done(write(tag, msg)) }, flags, null)
    }

    fun stopWriting(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    private fun write(tag: Tag, msg: NdefMessage): Result = runCatching {
        Ndef.get(tag)?.use { ndef ->
            ndef.connect()
            return when {
                !ndef.isWritable -> Result.READ_ONLY
                ndef.maxSize < msg.byteArrayLength -> Result.TOO_SMALL
                else -> { ndef.writeNdefMessage(msg); Result.WRITTEN }
            }
        }
        NdefFormatable.get(tag)?.use { blank ->
            blank.connect()
            blank.format(msg)
            return Result.WRITTEN
        }
        Result.FAILED
    }.getOrDefault(Result.FAILED)
}
