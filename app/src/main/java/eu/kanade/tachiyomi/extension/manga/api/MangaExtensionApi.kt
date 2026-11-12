package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            fetchExtensionStoreIndex(repoBaseUrl)
                ?: fetchLegacyExtensionIndex(repoBaseUrl)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    /**
     * Reads the modern extension index (`index.json`) used by current repositories.
     *
     * Repositories that adopted this format (Keiyoushi, among others) serve a stub
     * `index.min.json` that only tells outdated clients to update, while the real
     * extension list lives in `index.json` under `extensionList`.
     *
     * @return the extensions, or `null` when this repository doesn't use this format.
     */
    private suspend fun fetchExtensionStoreIndex(
        repoBaseUrl: String,
    ): List<MangaExtension.Available>? {
        val response = runCatching {
            networkService.client
                .newCall(GET("$repoBaseUrl/index.json"))
                .awaitSuccess()
        }.getOrNull() ?: return null

        val index = runCatching {
            with(json) { response.parseAs<ExtensionStoreIndexJsonObject>() }
        }.getOrNull() ?: return null

        val extensions = index.extensionList?.extensions ?: return null
        return extensions.mapNotNull { it.toExtension(repoBaseUrl) }
    }

    private suspend fun fetchLegacyExtensionIndex(
        repoBaseUrl: String,
    ): List<MangaExtension.Available> {
        val response = networkService.client
            .newCall(GET("$repoBaseUrl/index.min.json"))
            .awaitSuccess()

        return with(json) {
            response
                .parseAs<List<ExtensionJsonObject>>()
                .toExtensions(repoBaseUrl)
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                it.extractLibVersion() in MangaExtensionLoader.SUPPORTED_LIB_VERSIONS
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkUrl = "$repoUrl/apk/${it.apk}",
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    private fun ExtensionStoreExtensionJsonObject.toExtension(
        repoUrl: String,
    ): MangaExtension.Available? {
        if (packageName.isBlank()) return null
        val libVersion = extensionLib?.contentOrNull?.toDoubleOrNull()
            ?: versionName?.contentOrNull?.substringBeforeLast('.')?.toDoubleOrNull()
            ?: return null
        if (libVersion !in MangaExtensionLoader.SUPPORTED_LIB_VERSIONS) return null

        val resources = resources ?: return null
        val apkUrl = resources.apkUrl ?: return null
        val sourceList = sources.orEmpty().mapNotNull { source ->
            val id = source.id?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            MangaExtension.Available.MangaSource(
                id = id,
                lang = source.language.orEmpty(),
                name = source.name.orEmpty(),
                baseUrl = source.homeUrl.orEmpty(),
                mirrorUrls = source.mirrorUrls,
            )
        }

        return MangaExtension.Available(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = packageName,
            versionName = versionName?.contentOrNull.orEmpty(),
            versionCode = versionCode?.contentOrNull?.toLongOrNull() ?: 0L,
            libVersion = libVersion,
            lang = sourceList.firstOrNull { it.lang.isNotBlank() }?.lang ?: "all",
            isNsfw = contentWarning?.contentOrNull?.let {
                it.endsWith("_NSFW") || it.endsWith("_MIXED")
            } ?: false,
            sources = sourceList,
            apkUrl = apkUrl.toAbsoluteUrl(repoUrl),
            iconUrl = resources.iconUrl?.toAbsoluteUrl(repoUrl) ?: "$repoUrl/icon/$packageName.png",
            repoUrl = repoUrl,
        )
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return extension.apkUrl
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
    val mirrorUrls: List<String> = emptyList(),
)

/**
 * Server format of `index.json` in modern extension repositories (Mihon 0.20+).
 *
 * The legacy `index.min.json` array is still read for repositories that didn't migrate.
 */
@Serializable
private data class ExtensionStoreIndexJsonObject(
    val extensionList: ExtensionStoreExtensionListJsonObject? = null,
)

@Serializable
private data class ExtensionStoreExtensionListJsonObject(
    val extensions: List<ExtensionStoreExtensionJsonObject> = emptyList(),
)

@Serializable
private data class ExtensionStoreExtensionJsonObject(
    val name: String = "",
    val packageName: String = "",
    val resources: ExtensionStoreResourcesJsonObject? = null,
    val extensionLib: JsonPrimitive? = null,
    val versionCode: JsonPrimitive? = null,
    val versionName: JsonPrimitive? = null,
    val contentWarning: JsonPrimitive? = null,
    val sources: List<ExtensionStoreSourceJsonObject>? = null,
)

@Serializable
private data class ExtensionStoreResourcesJsonObject(
    val apkUrl: String? = null,
    val iconUrl: String? = null,
    val jarUrl: String? = null,
)

@Serializable
private data class ExtensionStoreSourceJsonObject(
    val id: JsonPrimitive? = null,
    val name: String? = null,
    val language: String? = null,
    val homeUrl: String? = null,
    val mirrorUrls: List<String> = emptyList(),
)

private fun String.toAbsoluteUrl(repoUrl: String): String {
    return if (startsWith("http://") || startsWith("https://")) this else "$repoUrl/${trimStart('/')}"
}

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
        mirrorUrls = it.mirrorUrls,
    )
}
