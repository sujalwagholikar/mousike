package com.mousike.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Preferences(
    val onboarded: Boolean = false,
    val name: String = "Listener",
    val languages: Set<String> = setOf("Hindi", "English"),
    val genres: Set<String> = setOf("Indie", "Pop"),
    val artists: Set<String> = emptySet(),
    val theme: String = "Midnight",
    val accent: String = "Coral",
    val aiEnabled: Boolean = false,
    val model: String = "gemini-3.5-flash-lite",
    val autoplay: Boolean = true,
    val wifiDownloads: Boolean = true,
    val discovery: Float = 0.25f,
    val detectAlbums: Boolean = true,
    val audioQuality: String = "High",
    val reduceMotion: Boolean = false,
    val textSize: String = "Default",
    val cardDensity: String = "Comfortable",
    val coverShape: String = "Square",
    val playerAmbience: Boolean = true,
    val showArtists: Boolean = true,
    val showAlbums: Boolean = true,
    val showMixes: Boolean = true,
    val refreshRate: String = "Auto",
    val preloadNext: Boolean = true,
    val fontCombo: String = "Signature",
    val surfaceStyle: String = "Soft",
    val playerLayout: String = "Classic",
    val playerArtworkSize: String = "Default",
    val playerBackground: String = "Artwork",
    val playerControls: String = "Accent",
    val showPlayerShortcuts: Boolean = true,
    val showPlayerContext: Boolean = true,
    val albumLayout: String = "Editorial",
    val trackSpacing: String = "Comfortable",
    val showTrackArtwork: Boolean = true,
    val showTrackDuration: Boolean = true,
    val showNavLabels: Boolean = true,
    val miniPlayerStyle: String = "Floating",
    val showMiniProgress: Boolean = true,
    val musicFirst: Boolean = true
)

/** The key is encrypted by a non-exportable Android Keystore AES key. Never backed up. */
class AppSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mousike_preferences", Context.MODE_PRIVATE)
    private val secrets = context.applicationContext.getSharedPreferences("mousike_secrets", Context.MODE_PRIVATE)
    init {
        // Apply the requested new release defaults once; subsequent choices remain the listener's.
        if (!prefs.getBoolean("appearance360",false)) prefs.edit()
            .putString("accent","Coral").putString("coverShape","Square")
            .putBoolean("appearance360",true).apply()
    }
    private val mutable = MutableStateFlow(load())
    val state: StateFlow<Preferences> = mutable
    private fun load() = Preferences(
        onboarded=prefs.getBoolean("onboarded",false), name=prefs.getString("name","Listener")!!,
        languages=prefs.getStringSet("languages",setOf("Hindi","English"))!!.toSet(),
        genres=prefs.getStringSet("genres",setOf("Indie","Pop"))!!.toSet(),
        artists=prefs.getStringSet("artists",emptySet())!!.toSet(),
        theme=prefs.getString("theme","Midnight")!!, accent=prefs.getString("accent","Coral")!!,
        aiEnabled=prefs.getBoolean("aiEnabled",false), model=prefs.getString("model","gemini-3.5-flash-lite")!!,
        autoplay=prefs.getBoolean("autoplay",true),wifiDownloads=prefs.getBoolean("wifiDownloads",true),
        discovery=prefs.getFloat("discovery",.25f),detectAlbums=prefs.getBoolean("detectAlbums",true),
        audioQuality=prefs.getString("audioQuality","High")!!.let {if(it=="Data saver") "Low" else it},reduceMotion=prefs.getBoolean("reduceMotion",false),
        textSize=prefs.getString("textSize","Default")!!,cardDensity=prefs.getString("cardDensity","Comfortable")!!,
        coverShape=prefs.getString("coverShape","Square")!!,playerAmbience=prefs.getBoolean("playerAmbience",true),
        showArtists=prefs.getBoolean("showArtists",true),showAlbums=prefs.getBoolean("showAlbums",true),showMixes=prefs.getBoolean("showMixes",true),refreshRate=prefs.getString("refreshRate","Auto")!!,preloadNext=prefs.getBoolean("preloadNext",true),
        fontCombo=prefs.getString("fontCombo","Signature")!!,surfaceStyle=prefs.getString("surfaceStyle","Soft")!!,
        playerLayout=prefs.getString("playerLayout","Classic")!!,playerArtworkSize=prefs.getString("playerArtworkSize","Default")!!,
        playerBackground=prefs.getString("playerBackground","Artwork")!!,playerControls=prefs.getString("playerControls","Accent")!!,
        showPlayerShortcuts=prefs.getBoolean("showPlayerShortcuts",true),showPlayerContext=prefs.getBoolean("showPlayerContext",true),
        albumLayout=prefs.getString("albumLayout","Editorial")!!,trackSpacing=prefs.getString("trackSpacing","Comfortable")!!,
        showTrackArtwork=prefs.getBoolean("showTrackArtwork",true),showTrackDuration=prefs.getBoolean("showTrackDuration",true),
        showNavLabels=prefs.getBoolean("showNavLabels",true),miniPlayerStyle=prefs.getString("miniPlayerStyle","Floating")!!,
        showMiniProgress=prefs.getBoolean("showMiniProgress",true),musicFirst=prefs.getBoolean("musicFirst",true)
    )
    @Synchronized fun update(transform: (Preferences)->Preferences) {
        val p=transform(mutable.value)
        prefs.edit().putBoolean("onboarded",p.onboarded).putString("name",p.name)
            .putStringSet("languages",p.languages).putStringSet("genres",p.genres).putStringSet("artists",p.artists)
            .putString("theme",p.theme).putString("accent",p.accent).putBoolean("aiEnabled",p.aiEnabled)
            .putString("model",p.model.trim().removePrefix("models/")).putBoolean("autoplay",p.autoplay)
            .putBoolean("wifiDownloads",p.wifiDownloads).putFloat("discovery",p.discovery)
            .putBoolean("detectAlbums",p.detectAlbums).putString("audioQuality",p.audioQuality)
            .putBoolean("reduceMotion",p.reduceMotion).putString("textSize",p.textSize).putString("cardDensity",p.cardDensity)
            .putString("coverShape",p.coverShape).putBoolean("playerAmbience",p.playerAmbience)
            .putBoolean("showArtists",p.showArtists).putBoolean("showAlbums",p.showAlbums).putBoolean("showMixes",p.showMixes).putString("refreshRate",p.refreshRate).putBoolean("preloadNext",p.preloadNext).apply()
        prefs.edit().putString("fontCombo",p.fontCombo).putString("surfaceStyle",p.surfaceStyle)
            .putString("playerLayout",p.playerLayout).putString("playerArtworkSize",p.playerArtworkSize)
            .putString("playerBackground",p.playerBackground).putString("playerControls",p.playerControls)
            .putBoolean("showPlayerShortcuts",p.showPlayerShortcuts).putBoolean("showPlayerContext",p.showPlayerContext)
            .putString("albumLayout",p.albumLayout).putString("trackSpacing",p.trackSpacing)
            .putBoolean("showTrackArtwork",p.showTrackArtwork).putBoolean("showTrackDuration",p.showTrackDuration)
            .putBoolean("showNavLabels",p.showNavLabels).putString("miniPlayerStyle",p.miniPlayerStyle)
            .putBoolean("showMiniProgress",p.showMiniProgress).putBoolean("musicFirst",p.musicFirst).apply()
        mutable.value=p
    }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("mousike_gemini",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mousike_gemini",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveApiKey(value: String) {
        if(value.isBlank()) { secrets.edit().clear().apply(); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        secrets.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("key",Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
    }
    fun apiKey(): String = runCatching {
        val stored=secrets.getString("key",null) ?: return ""
        val iv=Base64.decode(secrets.getString("iv",null),Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
        String(cipher.doFinal(Base64.decode(stored,Base64.NO_WRAP)),Charsets.UTF_8)
    }.getOrDefault("")
    fun clear() { secrets.edit().clear().apply(); prefs.edit().clear().putBoolean("appearance360",true).apply(); mutable.value=load() }
    companion object {
        @Volatile private var instance: AppSettings?=null
        fun get(context: Context): AppSettings = instance ?: synchronized(this) {
            instance ?: AppSettings(context.applicationContext).also { instance=it }
        }
    }
}
