package app.koma.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** A person found by search. [uri] opens their contact card. */
class ContactHit(val name: String, val uri: Uri)

object ContactSearch {
    /** Contacts whose name (or number) matches [q]. Needs the contacts permission; returns nothing without it. */
    fun find(ctx: Context, q: String, limit: Int): List<ContactHit> {
        val out = ArrayList<ContactHit>()
        try {
            val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
            val cols = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            ctx.contentResolver.query(uri, cols, null, null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(2) ?: continue
                    out += ContactHit(name, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }
}
