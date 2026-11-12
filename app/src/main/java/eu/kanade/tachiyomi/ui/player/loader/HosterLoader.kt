package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.getChangedAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class HosterLoader {
    companion object {
        /**
         * Returns true when the user wants dubbed audio. Extensions that ship a Sub/Dub preference
         * mark the matching video as preferred, but not every extension does it for downloads, so the
         * host falls back to looking at the video title/url.
         */
        private fun preferDub(): Boolean = try {
            Injekt.get<AudioPreferences>().preferDubAnimeSources().get()
        } catch (_: Exception) {
            false
        }

        /**
         * Heuristic that detects a dubbed video from its title/url.
         */
        private fun isDubVideo(video: Video): Boolean {
            val text = "${video.videoTitle} ${video.videoUrl}".lowercase()
            if (text.contains("dub")) return true
            if (text.contains("sub")) return false
            return text.contains("english")
        }

        /**
         * Finds the first dubbed video of the given hosters.
         */
        private fun findDubVideo(availableHosters: List<IndexedValue<HosterState>>): Pair<Int, Int>? {
            val dubHosterIdx = availableHosters.indexOfFirst {
                (it.value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }.any { (video, state) ->
                    isDubVideo(video) && (state == Video.State.READY || state == Video.State.QUEUE)
                }
            }
            if (dubHosterIdx == -1) return null

            val videoList = (availableHosters[dubHosterIdx].value as HosterState.Ready).let { hoster ->
                hoster.videoList zip hoster.videoState
            }
            val dubVideoIdx = videoList.indexOfFirst { (video, state) ->
                isDubVideo(video) && (state == Video.State.READY || state == Video.State.QUEUE)
            }
            return availableHosters[dubHosterIdx].index to dubVideoIdx
        }

        /**
         * Check for the best video from the current hosterState.
         *
         * The first video with the `preferred` attribute is selected, however
         * if no such video is selected the first video with a non-empty url is selected.
         * If there are no viable videos at all, an error is thrown.
         *
         * When the user asked for dubbed audio, a dubbed video wins over a subtitled one,
         * even if the extension marked the subtitled video as preferred.
         *
         * @return the indices of the hoster & video
         */
        fun selectBestVideo(hosterState: List<HosterState>): Pair<Int, Int> {
            val availableHosters = hosterState.withIndex()
                .filter { (_, state) -> state is HosterState.Ready }

            // Check for first preferred
            val isPreferred: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
                v.preferred && (s == Video.State.READY || s == Video.State.QUEUE)
            }
            val prefHosterIdx = availableHosters.indexOfFirst {
                (it.value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }.any(isPreferred)
            }
            if (prefHosterIdx != -1) {
                val videoList = (availableHosters[prefHosterIdx].value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }
                val prefVideoIdx = videoList.indexOfFirst(isPreferred)
                val preferredVideo = videoList[prefVideoIdx].first

                if (!preferDub() || isDubVideo(preferredVideo)) {
                    return availableHosters[prefHosterIdx].index to prefVideoIdx
                }

                // The extension prefers a subtitled video, but the user wants the dub
                findDubVideo(availableHosters)?.let { return it }
                return availableHosters[prefHosterIdx].index to prefVideoIdx
            }

            // No video was marked as preferred: prefer the dubbed one when the user asked for it
            if (preferDub()) {
                findDubVideo(availableHosters)?.let { return it }
            }

            // Check for first video with non-empty url
            val firstValid: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
                v.videoUrl.isNotEmpty() && (s == Video.State.READY || s == Video.State.QUEUE)
            }
            val firstAvailableHosterIdx = availableHosters.indexOfFirst {
                (it.value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }.any(firstValid)
            }
            if (firstAvailableHosterIdx != -1) {
                val videoList = (availableHosters[firstAvailableHosterIdx].value as HosterState.Ready).let { hoster ->
                    hoster.videoList zip hoster.videoState
                }
                val firstVideoIdx = videoList.indexOfFirst(firstValid)
                return availableHosters[firstAvailableHosterIdx].index to firstVideoIdx
            }

            // No success
            return Pair(-1, -1)
        }

        class EarlyReturnException(val video: Video) : Exception()

        /**
         * Return the first loaded and valid "best" video, based on the criteria in the function `selectBestVideo` above.
         *
         * @param source The source for the episode
         * @param hosterList the list of hosters
         * @return the video, or null if no valid video was found
         */
        suspend fun getBestVideo(source: AnimeSource, hosterList: List<Hoster>): Video? {
            val hosterStates = MutableList<HosterState>(hosterList.size) { HosterState.Idle("") }

            return try {
                withContext(Dispatchers.IO) {
                    hosterList.mapIndexed { hosterIdx, hoster ->
                        async {
                            val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)
                            hosterStates[hosterIdx] = hosterState

                            if (hosterState is HosterState.Ready) {
                                val prefIndex = hosterState.videoList.indexOfFirst { it.preferred && !it.initialized }
                                // The user wants dubbed audio: a dubbed video wins over a subtitled one
                                val dubIndex = if (preferDub()) {
                                    hosterState.videoList.indexOfFirst { isDubVideo(it) && !it.initialized }
                                } else {
                                    -1
                                }
                                val selectedIndex = when {
                                    prefIndex == -1 -> dubIndex
                                    dubIndex != -1 && !isDubVideo(hosterState.videoList[prefIndex]) -> dubIndex
                                    else -> prefIndex
                                }
                                if (selectedIndex != -1) {
                                    val video = hosterState.videoList[selectedIndex]
                                    hosterStates[hosterIdx] =
                                        (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                            selectedIndex,
                                            video,
                                            Video.State.LOAD_VIDEO,
                                        )

                                    val resolvedVideo = getResolvedVideo(source, video)
                                    if (resolvedVideo?.videoUrl?.isNotEmpty() == true) {
                                        coroutineContext.cancelChildren()
                                        throw EarlyReturnException(resolvedVideo)
                                    }

                                    hosterStates[hosterIdx] =
                                        (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                            selectedIndex,
                                            video,
                                            Video.State.ERROR,
                                        )
                                }
                            }
                        }
                    }.awaitAll()

                    var (hosterIdx, videoIdx) = selectBestVideo(hosterStates)
                    while (hosterIdx != -1) {
                        val hosterState = hosterStates[hosterIdx] as HosterState.Ready
                        val video = hosterState.videoList[videoIdx]
                        hosterStates[hosterIdx] =
                            (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                videoIdx,
                                video,
                                Video.State.LOAD_VIDEO,
                            )

                        val resolvedVideo = getResolvedVideo(source, video)
                        if (resolvedVideo?.videoUrl?.isNotEmpty() == true) {
                            coroutineContext.cancelChildren()
                            return@withContext resolvedVideo
                        }

                        hosterStates[hosterIdx] =
                            (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                videoIdx,
                                video,
                                Video.State.ERROR,
                            )
                        val newResult = selectBestVideo(hosterStates)
                        hosterIdx = newResult.first
                        videoIdx = newResult.second
                    }

                    coroutineContext.cancelChildren()
                    return@withContext null
                }
            } catch (e: EarlyReturnException) {
                e.video
            }
        }

        suspend fun getResolvedVideo(source: AnimeSource?, video: Video): Video? {
            val resolvedVideo = if (source is AnimeHttpSource && !video.initialized) {
                try {
                    source.resolveVideo(video)
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        throw e
                    }

                    null
                }
            } else {
                video
            }

            return resolvedVideo?.copy(initialized = true)
        }
    }
}
