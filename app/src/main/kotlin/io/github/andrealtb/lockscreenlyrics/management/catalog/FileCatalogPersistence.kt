package io.github.andrealtb.lockscreenlyrics.management.catalog

import android.content.Context
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import org.json.JSONException
import org.json.JSONObject

/**
 * Stores the accepted catalog as one small JSON file, replaced via write-to-temp + rename in
 * the same directory. The file only holds already-verified public data; it is re-verified on
 * every load, so a corrupted or edited file is simply not trusted.
 */
internal class FileCatalogPersistence(private val directory: File) : CatalogPersistence {
    private val file get() = File(directory, FILE_NAME)

    override fun read(): PersistedCatalog? {
        val text = try {
            file.readText()
        } catch (_: FileNotFoundException) {
            return null
        }
        return try {
            val json = JSONObject(text)
            if (json.optInt("format") != FORMAT) return null
            val accepted = AcceptedRevision(json.getLong("revision"), json.getString("payloadSha256"))
            PersistedCatalog(accepted, Base64.getDecoder().decode(json.getString("envelope")))
        } catch (_: JSONException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun write(value: PersistedCatalog) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot create $directory")
        val json = JSONObject()
            .put("format", FORMAT)
            .put("revision", value.accepted.revision)
            .put("payloadSha256", value.accepted.payloadSha256)
            .put("envelope", Base64.getEncoder().encodeToString(value.envelope))
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(json.toString())
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    companion object {
        const val FILE_NAME = "provider-catalog-state.json"
        private const val FORMAT = 1

        /** No-backup storage: a restored backup must not reset or forge the revision floor. */
        fun forApp(context: Context) = FileCatalogPersistence(File(context.noBackupFilesDir, "store"))
    }
}

/** The signed snapshot embedded by the release workflow; absent in local/debug builds. */
internal object BundledCatalogSnapshot {
    const val ASSET_PATH = "catalog/stable.json"

    fun read(context: Context): ByteArray? = try {
        context.assets.open(ASSET_PATH).use { it.readBytes() }
    } catch (_: FileNotFoundException) {
        null
    }
}
