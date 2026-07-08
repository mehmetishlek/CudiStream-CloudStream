package com.cudistream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class CudiStreamPlugin: Plugin() {
    override fun load() {
        registerMainAPI(FilmMakinesiProvider())
        registerMainAPI(HDFilmCehennemiProvider())
        registerExtractorAPI(CloseLoadExtractor())
    }
}
