package com.callgalli.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.callgalli.rules.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhoneContact(val name: String, val number: String, val rawNumber: String)

/** Reads names from the phone's own contacts. Nothing is copied out of the phone. */
class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contact name for a number, or null. Safe to call without permission. */
    fun lookupName(rawNumber: String?): String? {
        if (rawNumber.isNullOrBlank() || !hasPermission()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(rawNumber))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    /** All contacts with a phone number, one row per distinct normalized number. */
    suspend fun allContacts(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val result = LinkedHashMap<String, PhoneContact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val raw = c.getString(1) ?: continue
                    val normalized = PhoneNumbers.normalize(raw)
                    if (normalized.isNotEmpty() && normalized !in result) result[normalized] = PhoneContact(name, normalized, raw)
                }
            }
        }
        result.values.toList()
    }

    /** Reads the number the user picked in the system contact picker (no READ_CONTACTS needed). */
    fun readPickedPhone(uri: Uri): PhoneContact? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val raw = c.getString(1) ?: return@use null
            PhoneContact(c.getString(0) ?: raw, PhoneNumbers.normalize(raw), raw)
        }
    }.getOrNull()
}
