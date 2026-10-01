package dev.appolon.homelab.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A saved PocketID identity plus the cookies that authenticate it. */
@Serializable
@androidx.compose.runtime.Immutable
data class PocketIdAccount(
    val id: String,
    val username: String,
    val email: String? = null,
    val displayName: String? = null,
    val isAdmin: Boolean = false,
    /** "name=value" cookie pairs that authenticate this account. */
    val sessionCookies: List<String> = emptyList(),
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

/** Persisted account list and which one is active. */
@Serializable
@androidx.compose.runtime.Immutable
data class AccountState(
    val accounts: List<PocketIdAccount> = emptyList(),
    val activeAccountId: String? = null,
) {
    fun account(id: String?): PocketIdAccount? = accounts.firstOrNull { it.id == id }
    val active: PocketIdAccount? get() = account(activeAccountId)
}

/** Symmetric sealing of stored sessions; swappable for tests. */
interface CookieCipher {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

/**
 * Encrypts account data with an AES/GCM key held in AndroidKeyStore, so
 * live session cookies are not readable off a backed-up or rooted disk.
 */
object KeystoreCipher : CookieCipher {
    private const val ALIAS = "homelab_account_store"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun open(sealed: String): String? = runCatching {
        val all = Base64.getDecoder().decode(sealed)
        val iv = all.copyOfRange(0, 12)
        val encrypted = all.copyOfRange(12, all.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()
}

/** Pure encode/decode of [AccountState]; cipher handling stays injectable. */
object AccountStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(state: AccountState, cipher: CookieCipher): String = cipher.seal(json.encodeToString(state))

    fun decode(sealed: String, cipher: CookieCipher): AccountState? =
        cipher.open(sealed)?.let { text -> runCatching { json.decodeFromString<AccountState>(text) }.getOrNull() }
}

private val Context.accountDataStore by preferencesDataStore("pocketid_accounts")

/**
 * Multi-account PocketID session storage. Accounts keep their own session
 * cookies so the switcher can swap the active identity in and out of the
 * WebView cookie jar at will.
 */
class AccountStore(
    private val context: Context,
    private val cipher: CookieCipher = KeystoreCipher,
) {
    private val key = stringPreferencesKey("accounts_state")

    /** Current state; an unwritable/undecodable store yields a fresh one. */
    suspend fun load(): AccountState = withContext(Dispatchers.IO) {
        runCatching {
            context.accountDataStore.data.first()[key]?.let { AccountStateCodec.decode(it, cipher) }
        }.getOrNull() ?: AccountState()
    }

    suspend fun save(state: AccountState) = withContext(Dispatchers.IO) {
        context.accountDataStore.edit { prefs -> prefs[key] = AccountStateCodec.encode(state, cipher) }
    }
}
