package com.doomslug.carlyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SpotifyVault(context: Context) {
    private val prefs = context.getSharedPreferences("spotify_account", 0)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("spotify_tokens", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("spotify_tokens", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun read(): JSONObject = runCatching {
        val bytes = Base64.decode(prefs.getString("tokens", ""), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }.getOrDefault(JSONObject())
    fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        check(prefs.edit().putString("tokens", Base64.encodeToString(cipher.iv + cipher.doFinal(value.toString().toByteArray()), Base64.NO_WRAP)).commit())
    }
    fun clear() { prefs.edit().clear().commit() }
}

internal class SpotifyHttpError(val status: Int, val retrySeconds: Int = 0) : Exception(when (status) {
    401 -> "Spotify connection expired. Connect again."
    403 -> "Spotify does not allow access to this playlist. Import its CSV export instead."
    429 -> "Spotify is busy. Retry after ${retrySeconds.coerceAtLeast(1)} seconds."
    else -> "Spotify request failed ($status). Retry later."
})

/** No client secret. Authorization stays in the browser and credentials in Android Keystore. */
internal object SpotifyAccount {
    const val REDIRECT = "http://127.0.0.1:43821/callback"
    private val worker = Executors.newSingleThreadExecutor()
    private var server: ServerSocket? = null
    @Volatile var status: String? = null
    fun connected(context: Context) = SpotifyVault(context).read().optString("refresh_token").isNotBlank()
    fun disconnect(context: Context) { synchronized(this) { server?.close(); server = null }; SpotifyVault(context).clear() }
    private fun random() = ByteArray(32).also(SecureRandom()::nextBytes).let {
        Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    fun connect(context: Context) {
        val app = context.applicationContext
        check(BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()) { "Spotify connection is not configured in this build." }
        val state = random(); val verifier = random()
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val listener = synchronized(this) {
            server?.close()
            ServerSocket(43821, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 180_000 }.also { server = it }
        }
        status = "Finish connecting in your browser"
        worker.execute {
            try {
                var code: String? = null
                val until = System.currentTimeMillis() + 180_000
                while (code == null && System.currentTimeMillis() < until) {
                    listener.accept().use { socket ->
                        socket.soTimeout = 3_000
                        val request = socket.getInputStream().bufferedReader().readLine().orEmpty().take(16_384)
                        val uri = Uri.parse("http://127.0.0.1" + request.split(' ').getOrElse(1) { "/" })
                        val valid = uri.path == "/callback" && uri.getQueryParameter("state") == state
                        val denied = valid && uri.getQueryParameter("error") != null
                        if (valid) code = uri.getQueryParameter("code")
                        val body = if (denied) "Spotify connection cancelled. Return to Car Lyrics." else if (code != null)
                            "Connected. You can return to Car Lyrics." else "This connection has expired. Try again in Car Lyrics."
                        socket.getOutputStream().write(("HTTP/1.1 ${if (valid) "200 OK" else "400 Bad Request"}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n$body").toByteArray())
                        if (denied) error("Spotify connection cancelled")
                    }
                }
                check(!code.isNullOrBlank()) { "Spotify connection timed out. Try again." }
                val token = tokenRequest(mapOf("grant_type" to "authorization_code", "code" to code!!,
                    "redirect_uri" to REDIRECT, "code_verifier" to verifier))
                saveToken(app, token)
                status = "Connected"
            } catch (e: Exception) { status = if (e is SpotifyHttpError || e is IllegalStateException) e.message else "Connection did not finish. Connect again." }
            finally {
                runCatching { listener.close() }
                synchronized(this) { if (server === listener) server = null }
                app.startActivity(Intent(app, SpotifyImportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code").appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .appendQueryParameter("state", state).appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge).build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
    private fun saveToken(context: Context, token: JSONObject) {
        val vault = SpotifyVault(context)
        if (token.optString("refresh_token").isBlank()) token.put("refresh_token", vault.read().optString("refresh_token"))
        token.put("expires_at", System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000)
        vault.write(token)
    }
    private fun tokenRequest(values: Map<String, String>): JSONObject {
        val body = (values + ("client_id" to BuildConfig.SPOTIFY_CLIENT_ID)).entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 15_000; conn.requestMethod = "POST"; conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) {
                val reason = runCatching { JSONObject(conn.errorStream.bufferedReader().use { it.readText() }).optString("error") }.getOrDefault("")
                throw IllegalStateException("Spotify sign-in failed (${conn.responseCode}, ${reason.take(40)}). Connect again.")
            }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }
    @Synchronized private fun accessToken(context: Context, force: Boolean = false): String {
        var token = SpotifyVault(context).read()
        if (force || token.optLong("expires_at") < System.currentTimeMillis() + 60_000) {
            check(token.optString("refresh_token").isNotBlank()) { "Connect Spotify first" }
            token = tokenRequest(mapOf("grant_type" to "refresh_token", "refresh_token" to token.getString("refresh_token")))
            saveToken(context, token)
        }
        return token.getString("access_token")
    }
    fun get(context: Context, url: String, retry: Boolean = true, attempt: Int = 0): JSONObject {
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.host == "api.spotify.com" && parsed.port == -1 && parsed.userInfo == null) { "Invalid Spotify page" }
        val conn = parsed.openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false; conn.connectTimeout = 15_000; conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer ${accessToken(context)}")
            if (conn.responseCode == 401 && retry) { accessToken(context, true); return get(context, url, false) }
            if (conn.responseCode in 500..599 && attempt < 2) {
                conn.disconnect(); Thread.sleep(1_000L * (attempt + 1))
                return get(context, url, retry, attempt + 1)
            }
            if (conn.responseCode != 200) throw SpotifyHttpError(conn.responseCode, conn.getHeaderField("Retry-After")?.toIntOrNull() ?: 30)
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: java.io.IOException) {
            if (attempt >= 2) throw e
            Thread.sleep(1_000L * (attempt + 1))
            return get(context, url, retry, attempt + 1)
        } finally { conn.disconnect() }
    }
}
