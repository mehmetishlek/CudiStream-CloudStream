package com.cudistream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FilmMakinesiProvider : MainAPI() {
    override var mainUrl = "https://filmmakinesi.to"
    override var name = "FilmMakinesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)
    override val usesWebView get() = true

    override val mainPage = mainPageOf(
        "$mainUrl/filmler-1/" to "Tum Filmler",
        "$mainUrl/kesfet/" to "Kesfet",
        "$mainUrl/yil/2026/film/" to "2026",
        "$mainUrl/yil/2025-fm5/film/" to "2025",
        "$mainUrl/yil/2024-fm1/film/" to "2024",
        "$mainUrl/tur/aksiyon-fm1/film/" to "Aksiyon",
        "$mainUrl/tur/aile-fm2/film/" to "Aile",
        "$mainUrl/tur/animasyon-fm2/film/" to "Animasyon",
        "$mainUrl/tur/belgesel/film/" to "Belgesel",
        "$mainUrl/tur/biyografi/film/" to "Biyografi",
        "$mainUrl/tur/bilim-kurgu-fm3/film/" to "Bilim Kurgu",
        "$mainUrl/tur/dram-fm1/film/" to "Dram",
        "$mainUrl/tur/fantastik-fm1/film/" to "Fantastik",
        "$mainUrl/tur/gerilim-fm1/film/" to "Gerilim",
        "$mainUrl/tur/gizem/film/" to "Gizem",
        "$mainUrl/tur/komedi-fm1/film/" to "Komedi",
        "$mainUrl/tur/korku-fm1/film/" to "Korku",
        "$mainUrl/tur/macera-fm1/film/" to "Macera",
        "$mainUrl/tur/muzik/film/" to "Muzik",
        "$mainUrl/tur/polisiye/film/" to "Polisiye",
        "$mainUrl/tur/romantik-fm1/film/" to "Romantik",
        "$mainUrl/tur/savas-fm1/film/" to "Savas",
        "$mainUrl/tur/spor/film/" to "Spor",
        "$mainUrl/tur/tarih-fm1/film/" to "Tarih",
        "$mainUrl/tur/western-fm1/film/" to "Western"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else paginateUrl(request.data, page)
        val doc = app.get(url).document
        val items = doc.select("div.film-list .content.row .col-6 > .item-relative > a.item").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/arama/?s=$query" else "$mainUrl/arama/sayfa/$page/?s=$query"
        val doc = app.get(url).document
        val items = doc.select("div.film-list .content.row .col-6 > .item-relative > a.item").mapNotNull { it.toSearchResponse() }
        return newSearchResponseList(items, items.isNotEmpty())
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        val title = attr("data-title").ifBlank { selectFirst(".item-footer .title")?.text() } ?: return null
        val img = selectFirst("img.thumbnail")
        val score = attr("data-score").toFloatOrNull()
        val isTvSeries = attr("href").startsWith("/dizi/")
        return newMovieSearchResponse(title, href, if (isTvSeries) TvType.TvSeries else TvType.Movie) {
            this.posterUrl = fixUrlNull(img?.attr("src"))
            if (score != null) this.score = Score.from10(score)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = app.get(url).document
        val title = d.selectFirst("h1.title")?.text()?.replace(" izle", "")?.trim() ?: return null
        val poster = d.selectFirst("meta[property='og:image']")?.attr("content")
        val plot = d.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
        val year = d.selectFirst("h1.title span.date a")?.text()?.toIntOrNull()
        val tags = d.select(".info .type a").mapNotNull { it.text().trim().ifEmpty { null } }
        val ratingText = d.selectFirst(".info .imdb b")?.text()
        val score = ratingText?.toFloatOrNull()?.let { Score.from10(it) }
        val durationText = d.selectFirst(".info .time")?.text()
        val duration = durationText?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val recommendations = d.select("#info--box ~ .related .item").mapNotNull {
            val link = it.attr("href")
            val titleRec = it.attr("data-title").ifBlank { it.selectFirst(".item-footer .title")?.text() }
            val img = it.selectFirst("img.thumbnail")
            if (link.isNotBlank() && !titleRec.isNullOrBlank()) {
                newMovieSearchResponse(titleRec, fixUrlNull(link) ?: return@mapNotNull null, TvType.Movie) {
                    this.posterUrl = fixUrlNull(img?.attr("src"))
                }
            } else null
        }

        val actors = d.select(".cast").mapNotNull {
            val name = it.selectFirst(".cast-name")?.text()?.trim()
            val img = it.selectFirst("img.cast-img")?.attr("src")
            if (!name.isNullOrBlank()) Actor(name, fixUrlNull(img)) else null
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = plot
            this.year = year
            this.tags = tags
            this.score = score
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val d = app.get(data).document
        val iframeSrc = d.selectFirst("iframe[data-src*='closeload']")?.attr("data-src")
            ?: d.selectFirst("iframe[src*='closeload']")?.attr("src")
            ?: return false
        val embedUrl = if (iframeSrc.startsWith("/")) "$mainUrl$iframeSrc" else iframeSrc
        loadExtractor(embedUrl, data, subtitleCallback, callback)
        return true
    }

    private fun paginateUrl(baseUrl: String, page: Int): String {
        return if (baseUrl.contains("kesfet")) {
            "$mainUrl/kesfet/sayfa/$page/"
        } else if (baseUrl.contains("yil/")) {
            val slug = baseUrl.substringAfter("$mainUrl/").substringBeforeLast("/")
            "$mainUrl/$slug/sayfa/$page/"
        } else if (baseUrl.contains("tur/")) {
            val slug = baseUrl.substringAfter("$mainUrl/").substringBeforeLast("/")
            "$mainUrl/$slug/sayfa/$page/"
        } else {
            "${baseUrl.removeSuffix("/")}/sayfa/$page/"
        }
    }
}
