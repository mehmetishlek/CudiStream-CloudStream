package com.cudistream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HDFilmCehennemiProvider : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)
    override val usesWebView get() = true

    override val mainPage = mainPageOf(
        "$mainUrl/category/film-izle-2/" to "Tum Filmler",
        "$mainUrl/en-cok-begenilen-filmleri-izle-4/" to "En Cok Begenilenler",
        "$mainUrl/en-cok-yorumlananlar-2/" to "En Cok Yorumlananlar",
        "$mainUrl/imdb-7-puan-uzeri-filmler-2/" to "IMDB 7+",
        "$mainUrl/category/tavsiye-filmler-izle2/" to "Tavsiye Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "$request.data?page=$page"
        val doc = app.get(url).document
        val items = doc.select("a.poster").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        val items = doc.select("a.poster").mapNotNull { it.toSearchResponse() }
        return newSearchResponseList(items, items.isNotEmpty())
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        val title = attr("title").ifBlank { selectFirst("img")?.attr("alt") }?.trim() ?: return null
        val img = selectFirst("img")
        val yearText = selectFirst(".poster-meta span")?.text()
        val year = yearText?.toIntOrNull()
        val ratingText = selectFirst(".imdb, .poster-rating, .poster-meta")?.text()
        val rating = ratingText?.let {
            Regex("""(\d+(?:\.\d+)?)""").find(it)?.groupValues?.get(1)?.toFloatOrNull()
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = fixUrlNull(img?.attr("src")?.ifBlank { img?.attr("data-src") })
            if (rating != null) this.score = Score.from10(rating)
            if (year != null) this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = app.get(url).document
        val jsonld = d.selectFirst("script[type='application/ld+json']")?.data()
        val title = jsonld?.let {
            Regex(""""name"\s*:\s*"([^"]+)""").find(it)?.groupValues?.get(1)
        } ?: d.selectFirst("title")?.text()?.replace(" izle | Hdfilmcehennemi | Film izle | HD Film izle", "")?.trim()
        ?: return null

        val poster = jsonld?.let {
            Regex(""""image"\s*:\s*"([^"]+)""").find(it)?.groupValues?.get(1)
        } ?: d.selectFirst("meta[property='og:image']")?.attr("content")

        val year = d.selectFirst(".poster-meta span")?.text()?.toIntOrNull()
        val plot = d.selectFirst("meta[property='og:description'], meta[name='description']")?.attr("content")?.trim()
        val tags = d.select("a[href*='/tur/']").mapNotNull { it.text().trim().ifEmpty { null } }
        val ratingText = d.selectFirst(".imdb, .poster-meta .imdb")?.text()
        val rating = ratingText?.let { Regex("""(\d+(?:\.\d+)?)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }

        val recommendations = d.select("a.poster").mapNotNull {
            val link = it.attr("href")
            val titleRec = it.attr("title").ifBlank { it.selectFirst("img")?.attr("alt") }
            val img = it.selectFirst("img")
            if (link.isNotBlank() && !titleRec.isNullOrBlank()) {
                newMovieSearchResponse(titleRec, fixUrlNull(link) ?: return@mapNotNull null, TvType.Movie) {
                    this.posterUrl = fixUrlNull(img?.attr("src")?.ifBlank { img?.attr("data-src") })
                }
            } else null
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = rating?.let { Score.from10(it) }
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val d = app.get(data).document
        val iframeSrc = d.selectFirst("iframe")?.attr("src") ?: return false
        loadExtractor(iframeSrc, data, subtitleCallback, callback)
        return true
    }
}
