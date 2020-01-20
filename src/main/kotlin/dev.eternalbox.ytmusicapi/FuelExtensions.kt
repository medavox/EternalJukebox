package dev.eternalbox.ytmusicapi

import dev.eternalbox.eternaljukebox.JSON_MAPPER

fun YoutubeMusicSearchResponse.getSongs(): Array<YTMSong> {
    return contents.sectionListRenderer.contents.filter { content -> content.musicShelfRenderer.title.runs.any { run -> run.text == "Songs" } }
        .flatMap { content ->
            content.musicShelfRenderer.contents.map { json ->
                JSON_MAPPER.convertValue(
                    json,
                    YTMSRSongContents::class.java
                )
            }
        }
        .map { shelf ->
            val (videoID, playlistID, params) = shelf.musicResponsiveListItemRenderer.overlay.musicItemThumbnailOverlayRenderer.content.musicPlayButtonRenderer.playNavigationEndpoint.watchEndpoint
            val artist =
                shelf.musicResponsiveListItemRenderer.flexColumns.flatMap { column -> column.musicResponsiveListItemFlexColumnRenderer.text.runs.toList() }
                    .mapNotNull artist@{ run ->
                        if (run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType != "MUSIC_PAGE_TYPE_ARTIST")
                            return@artist null
                        return@artist YTMArtist(run.text, run.navigationEndpoint.browseEndpoint.browseId)
                    }
                    .firstOrNull()
            val album =
                shelf.musicResponsiveListItemRenderer.flexColumns.flatMap { column -> column.musicResponsiveListItemFlexColumnRenderer.text.runs.toList() }
                    .mapNotNull album@{ run ->
                        if (run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType != "MUSIC_PAGE_TYPE_ALBUM")
                            return@album null
                        return@album YTMAlbum(run.text, run.navigationEndpoint.browseEndpoint.browseId)
                    }
                    .firstOrNull()

            YTMSong(videoID, playlistID, params, artist, album)
        }
        .toTypedArray()
//    return emptyArray()
}