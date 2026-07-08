package com.cudistream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CloseLoadExtractor : ExtractorApi() {
    override val name = "CloseLoad"
    override val mainUrl = "https://closeload.filmmakinesi.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).text.replace("\\/", "/")

        val m3u8 = Regex("""["']([^"']+\.m3u8[^"']*)["']""").find(res)?.groupValues?.get(1)
        if (m3u8 != null) {
            callback.invoke(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            })
            return
        }

        val videoUrl = Regex("""file["']?\s*:\s*["']([^"']+)["']""").find(res)?.groupValues?.get(1)
            ?: Regex("""src["']?\s*:\s*["']([^"']+)["']""").find(res)?.groupValues?.get(1)
            ?: Regex("""url["']?\s*:\s*["']([^"']+)["']""").find(res)?.groupValues?.get(1)
            ?: Regex("""<source\s+src=["']([^"']+)["']""").find(res)?.groupValues?.get(1)
            ?: return

        callback.invoke(
            newExtractorLink(name, name, videoUrl, ExtractorLinkType.VIDEO) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
