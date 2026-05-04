package recloudstream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class OnePorn : MainAPI() {
    override var mainUrl = "https://www.1porn.tv"
    override var name = "1porn"
    override val hasMainPage = true
    override var lang = "en"
    override val supportedTypes = setOf(TvType.NSFW)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sections = listOf(
            Triple("Latest Updates", "$mainUrl/latest-updates/", "latest"),
            Triple("Top Rated", "$mainUrl/top-rated/", "top"),
            Triple("Most Popular", "$mainUrl/most-popular/", "popular"),
            Triple("Teamskeet", "$mainUrl/networks/teamskeet-com/", "network"),
            Triple("MYLF", "$mainUrl/networks/mylf-com/", "network"),
            Triple("Tushy", "$mainUrl/networks/tushy-com/", "network"),
            Triple("Blacked", "$mainUrl/networks/blacked/", "network"),
            Triple("Naughty America", "$mainUrl/networks/naughtyamerica-com/", "network")
        )

        val homePageList = sections.map { (name, url, _) ->
            val pageUrl = if (page <= 1) url else "${url.removeSuffix("/")}/$page/"
            try {
                val doc = app.get(pageUrl).document
                val videos = doc.select("div.video-item, article, .thumb-block, .video-card, .video-list-item").mapNotNull {
                    it.toSearchResult()
                }
                if (videos.isNotEmpty()) HomePageList(name, videos.take(20)) else null
            } catch (e: Exception) {
                null
            }
        }.filterNotNull()

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/${query.replace(" ", "+")}/relevance/"
        val doc = app.get(searchUrl).document
        return doc.select("div.video-item, article, .thumb-block, .video-card, .video-list-item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3, .title, .video-title, .video-name")?.text()?.trim() ?: return null
        val href = selectFirst("a")?.attr("href") ?: return null
        if (href.contains("/models/") || href.contains("/categories/")) return null
        
        val link = fixUrl(href)
        val poster = selectFirst("img")?.attr("src") ?: selectFirst("img")?.attr("data-src")

        return newMovieSearchResponse(title, link, TvType.NSFW) {
            this.posterUrl = fixUrlNull(poster)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .video-info-title")?.text()?.trim() ?: "Unknown"
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("div.description, .video-description, .video-details-info")?.text()?.trim()
        val tags = doc.select("div.video-info-item a[href*='/categories/'], .tags a, .categories a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val html = doc.html()

        val mp4Regex = """https?://[^"\s]+ahcdn\.com[^"\s]*\.mp4[^"\s]*""".toRegex()

        mp4Regex.findAll(html).forEach { match ->
            val link = match.value
            val quality = when {
                link.contains("_2160m") || link.contains("2160") || link.contains("4k") -> 2160
                link.contains("_1080m") || link.contains("1080") -> 1080
                link.contains("_720m") || link.contains("720") -> 720
                link.contains("_480m") || link.contains("480") -> 480
                else -> 480
            }
            callback(
                newExtractorLink(
                    source = "1porn",
                    name = "1porn ${quality}p",
                    url = link,
                ) {
                    this.quality = quality
                    this.referer = mainUrl
                }
            )
        }
        return true
    }
}
