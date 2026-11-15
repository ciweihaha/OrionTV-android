package com.ciweihaha.oriontv.data

import android.content.Context
import com.ciweihaha.oriontv.data.epg.EpgRepository
import com.ciweihaha.oriontv.data.epg.XmlTvEpgRepository
import com.ciweihaha.oriontv.data.settings.AppSettings
import com.ciweihaha.oriontv.data.source.SourceRepository
import com.ciweihaha.oriontv.data.source.TvBoxSourceRepository

/**
 * Manual service locator: one shared source / EPG / library instance per
 * process, built from [SourceConfig]. Call [invalidate] after Settings
 * are saved so the next screen picks up the new addresses.
 *
 * The EPG repository resolves channel ids through the source repository
 * (its channel list is cached for 30 minutes, so the lookup is cheap).
 */
object AppSources {

    @Volatile
    private var source: SourceRepository? = null

    @Volatile
    private var epg: EpgRepository? = null

    @Volatile
    private var library: LibraryStore? = null

    @Volatile
    private var config: SourceConfig? = null

    /**
     * R13-D: bumps on every [invalidate] so screens that cache the
     * rendered rows (HomeFragment) can tell a Settings change happened
     * and reload, instead of showing the old playlist until restart.
     */
    @Volatile
    var sourceGeneration: Int = 0
        private set

    fun config(context: Context): SourceConfig =
        config ?: SourceConfig(context).also { config = it }

    fun source(context: Context): SourceRepository {
        val existing = source
        if (existing != null) return existing
        val cfg = config(context)
        // Live URL: the address saved in Settings wins; with nothing
        // saved yet, the built-in default (the user's own live.m3u).
        // LiveActivity resolves the same rule, so both screens always
        // load the same playlist and channel ids match.
        val liveUrl = AppSettings(context).getM3uUrl().takeIf { !it.isNullOrBlank() }
            ?: cfg.liveM3uUrl
        return TvBoxSourceRepository(
            liveM3uUrl = liveUrl,
            vodApiUrl = cfg.vodApiUrl,
        ).also { source = it }
    }

    fun epg(context: Context): EpgRepository {
        val existing = epg
        if (existing != null) return existing
        val appContext = context.applicationContext
        val cfg = config(context)
        return XmlTvEpgRepository(
            epgUrl = cfg.epgUrl,
            channelLookup = { channelId ->
                source(appContext).channels()
                    .find { it.id == channelId }
                    ?.let { it.name to it.tvgId }
            },
        ).also { epg = it }
    }

    fun library(context: Context): LibraryStore =
        library ?: LibraryStore(context).also { library = it }

    fun invalidate() {
        source = null
        epg = null
        sourceGeneration++
    }
}
