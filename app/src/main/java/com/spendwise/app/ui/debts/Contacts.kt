package com.spendwise.app.ui.debts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.Contacts
import com.spendwise.app.domain.KnownPerson

/** The picked contact's name and first email, or null if it has no name or can't be read. */
internal fun readContact(resolver: ContentResolver, contact: Uri): KnownPerson? = try {
    val name = resolver.query(contact, arrayOf(Contacts.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
    if (name.isNullOrBlank()) null else {
        // Through the picked contact's own data: the picker's grant covers it, not the whole Email table
        val data = Uri.withAppendedPath(contact, Contacts.Data.CONTENT_DIRECTORY)
        // A name alone is still worth filling if the email can't be read
        val email = try {
            resolver.query(data, arrayOf(Email.ADDRESS), "${Contacts.Data.MIMETYPE} = ?", arrayOf(Email.CONTENT_ITEM_TYPE), null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: SecurityException) {
            null
        }
        KnownPerson(name, email.orEmpty())
    }
} catch (e: SecurityException) {
    null
}
