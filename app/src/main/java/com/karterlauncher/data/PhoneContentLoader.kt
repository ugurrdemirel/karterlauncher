package com.karterlauncher.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecentCallRow(
    val number: String,
    val cachedName: String?,
    val dateMillis: Long,
    val durationSeconds: Long,
    val type: Int,
)

data class ContactPhoneRow(
    val displayName: String,
    val number: String,
)

class PhoneContentLoader(private val context: Context) {

    suspend fun loadRecentCalls(limit: Int = 200): List<RecentCallRow> = withContext(Dispatchers.IO) {
        queryRecentCalls(limit)
    }

    suspend fun loadContactsWithPhones(): List<ContactPhoneRow> = withContext(Dispatchers.IO) {
        queryContactsPhones()
    }

    @SuppressLint("MissingPermission")
    private fun queryRecentCalls(limit: Int): List<RecentCallRow> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val sort = "${CallLog.Calls.DATE} DESC"
        return try {
            val cursor =
                resolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, sort) ?: return emptyList()
            cursor.use { c ->
                val idxNum = c.getColumnIndex(CallLog.Calls.NUMBER).takeIf { it >= 0 } ?: return emptyList()
                val idxName = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val idxType = c.getColumnIndex(CallLog.Calls.TYPE)
                val idxDate = c.getColumnIndex(CallLog.Calls.DATE).takeIf { it >= 0 } ?: return emptyList()
                val idxDur = c.getColumnIndex(CallLog.Calls.DURATION).takeIf { it >= 0 } ?: return emptyList()
                val list = ArrayList<RecentCallRow>()
                while (c.moveToNext() && list.size < limit) {
                    val num = c.getString(idxNum)?.trim().orEmpty()
                    if (num.isEmpty()) continue
                    val name = if (idxName >= 0) c.getString(idxName)?.trim()?.takeIf { it.isNotEmpty() } else null
                    val type = if (idxType >= 0) c.getInt(idxType) else CallLog.Calls.INCOMING_TYPE
                    val date = c.getLong(idxDate)
                    val dur = c.getLong(idxDur)
                    list.add(RecentCallRow(num, name, date, dur, type))
                }
                list
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryContactsPhones(): List<ContactPhoneRow> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val sort = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        return try {
            val cursor =
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sort,
                ) ?: return emptyList()
            cursor.use { c ->
                val idxName =
                    c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME).takeIf { it >= 0 }
                        ?: return emptyList()
                val idxNum =
                    c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER).takeIf { it >= 0 }
                        ?: return emptyList()
                val list = ArrayList<ContactPhoneRow>()
                while (c.moveToNext()) {
                    val name = c.getString(idxName)?.trim().orEmpty()
                    val rawNum = c.getString(idxNum)?.trim().orEmpty()
                    if (rawNum.isEmpty()) continue
                    list.add(
                        ContactPhoneRow(
                            displayName = name.ifEmpty { rawNum },
                            number = rawNum,
                        ),
                    )
                }
                list
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
