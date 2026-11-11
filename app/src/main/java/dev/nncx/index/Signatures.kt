package dev.nncx.index

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * "My Builds" by signing certificate rather than by FLAG_DEBUGGABLE.
 *
 * NNCx is itself signed with the machine's debug keystore — the same key that
 * signs every other debug build you make. So "signed like me" picks up your
 * work without any configuration at all, and ignores other people's debug
 * APKs, which FLAG_DEBUGGABLE would happily include.
 */
object Signatures {

    private val cache = HashMap<String, String?>()
    private var mine: String? = null
    private var mineResolved = false

    fun myCert(context: Context): String? {
        if (!mineResolved) {
            mine = certOf(context, context.packageName)
            mineResolved = true
        }
        return mine
    }

    fun signedLikeMe(context: Context, packageName: String): Boolean {
        val self = myCert(context) ?: return false
        return certOf(context, packageName) == self
    }

    /** COLD data: a package's signature does not change without a reinstall. */
    private fun certOf(context: Context, packageName: String): String? =
        cache.getOrPut(packageName) {
            runCatching {
                val info = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val signers = info.signingInfo?.apkContentsSigners ?: return@runCatching null
                val first = signers.firstOrNull() ?: return@runCatching null
                MessageDigest.getInstance("SHA-256")
                    .digest(first.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }

    fun clear() {
        cache.clear()
        mineResolved = false
    }
}
