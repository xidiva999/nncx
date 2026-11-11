package dev.nncx.contacts

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A person is just another rankable target.
 *
 * `key` is the bucket the call log and the index agree on: digits only, last
 * nine. +971 50 123 4567, 050 123 4567 and 00971501234567 are one person and
 * three strings — in Dubai that is not an edge case, it is every contact.
 * Nine digits is short enough to survive any country prefix and long enough
 * not to collide.
 */
data class Contact(
    val lookupKey: String,
    val name: String,
    val number: String,
    val key: String,
) {
    val sortKey: String = name.lowercase()
    val initial: String = name.trim().firstOrNull()?.uppercase() ?: "?"
}

object ContactIndex {

    fun bucket(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() } ?: return null
        if (digits.length < 6) return null
        return digits.takeLast(9)
    }

    suspend fun load(context: Context): List<Contact> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Contact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val lookup = c.getString(0) ?: continue
                    val name = c.getString(1)?.trim().orEmpty()
                    val number = c.getString(2)?.trim().orEmpty()
                    val key = bucket(number) ?: continue
                    if (name.isEmpty()) continue
                    // One row per person: the first number wins.
                    out.putIfAbsent(key, Contact(lookup, name, number, key))
                }
            }
        }
        out.values.toList()
    }
}
