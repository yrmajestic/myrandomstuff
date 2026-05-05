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

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sections = listOf(
            Triple("Latest Updates", "$mainUrl/latest-updates/", "latest"),
            Triple("Top Rated", "$mainUrl/top-rated/", "top"),
            Triple("Most Popular", "$mainUrl/most-popular/", "popular"),
            Triple("Teamskeet", "$mainUrl/networks/teamskeet-com/", "network"),
            Triple("MYLF", "$mainUrl/networks/mylf-com/", "network"),
            Triple("Brazzers", "$mainUrl/networks/brazzers-com/", "network"),
            Triple("Tushy", "$mainUrl/networks/tushy-com/", "network"),
            Triple("Blacked", "$mainUrl/networks/blacked/", "network"),
            Triple("Bangbros", "$mainUrl/networks/bangbros-com/", "network"),
            Triple("Reality Kings", "$mainUrl/networks/realitykings-com/", "network"),
            Triple("Adult Time", "$mainUrl/networks/adult-time/", "network"),
            Triple("FakeHub", "$mainUrl/networks/fakehub-com/", "network"),
            Triple("Woodman Casting", "$mainUrl/networks/woodmancastingx-com/", "network"),
            Triple("Naughty America", "$mainUrl/networks/naughtyamerica-com/", "network"),
            Triple("Private", "$mainUrl/networks/private/", "network")
        )

        val homePageList = sections.mapNotNull { (name, url, _) ->
            val pageUrl = if (page <= 1) url else "${url.trimEnd('/')}/$page/"
            try {
                val response = app.get(pageUrl, headers = headers).document
                val videos = response.select("div.item, div.video-item, article.item, .thumb-block").mapNotNull {
                    it.toSearchResult()
                }
                if (videos.isNotEmpty()) HomePageList(name, videos) else null
            } catch (e: Exception) {
                null
            }
        }

        return newHomePageResponse(homePageList)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/${query.replace(" ", "+")}/relevance/"
        val doc = app.get(searchUrl, headers = headers).document
        return doc.select("div.item, div.video-item, article.item, .thumb-block").mapNotNull { it.toSearchResult() }
    }

    // تصحيح نوع البيانات ليدعم التمرير في البحث
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchUrl = if (page <= 1) {
            "$mainUrl/search/${query.replace(" ", "+")}/relevance/"
        } else {
            "$mainUrl/search/${query.replace(" ", "+")}/relevance/$page/"
        }
        val doc = app.get(searchUrl, headers = headers).document
        val results = doc.select("div.item, div.video-item, article.item, .thumb-block").mapNotNull { it.toSearchResult() }
        return if (results.isEmpty()) null else newSearchResponseList(results)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst(".title, a[title], h3, .video-title, .video-name")?.text()?.trim() 
            ?: selectFirst("a")?.attr("title")?.trim()
            ?: return null
            
        val href = selectFirst("a")?.attr("href") ?: return null
        if (href.contains("/models/") || href.contains("/categories/")) return null
        
        val link = fixUrl(href)
        val poster = selectFirst("img")?.let { img ->
            img.attr("data-src").takeIf { it.isNotBlank() } 
            ?: img.attr("data-original").takeIf { it.isNotBlank() }
            ?: img.attr("src")
        }

        return newMovieSearchResponse(title, link, TvType.NSFW) {
            this.posterUrl = fixUrlNull(poster)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        val title = doc.selectFirst("h1, .video-info-title, .title")?.text()?.trim() ?: "Unknown"
        val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
        val description = doc.selectFirst("div.description, .video-description, .video-details-info")?.text()?.trim()
        
        val tags = doc.select("div.video-info-item a[href*='/categories/'], .tags a, .categories a, .video-tags a").map { it.text() }
        
        val recommendations = doc.select("div.item, div.video-item, article.item").mapNotNull {
            it.toSearchResult()
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data, headers = headers)
        val html = response.text
        val foundLinks = mutableSetOf<String>()

        val cleanHtml = html.replace("\\/", "/").replace("\\\"", "\"")

        val linkRegex = """https?://[^\s"'<>]+ahcdn\.com[^\s"'<>]+(?:\.mp4|\.m3u8)[^\s"'<>]*""".toRegex()
        val genericRegex = """https?://[^\s"'<>]+(?:\.mp4|\.m3u8)[^\s"'<>]*""".toRegex()

        (linkRegex.findAll(cleanHtml) + genericRegex.findAll(cleanHtml)).forEach { match ->
            addLink(match.value, foundLinks, callback)
        }

        response.document.select("iframe").forEach { iframe ->
            val iframeUrl = iframe.attr("src")
            if (iframeUrl.isNotBlank()) {
                try {
                    val iframeHtml = app.get(fixUrl(iframeUrl), headers = headers).text
                    linkRegex.findAll(iframeHtml.replace("\\/", "/")).forEach { match ->
                        addLink(match.value, foundLinks, callback)
                    }
                } catch (e: Exception) { }
            }
        }

        return foundLinks.isNotEmpty()
    }

    private suspend fun addLink(link: String, foundLinks: MutableSet<String>, callback: (ExtractorLink) -> Unit) {
        val cleanLink = link.substringBefore("\"").substringBefore("'").substringBefore(">")
        if (cleanLink.isBlank() || !foundLinks.add(cleanLink)) return

        val quality = when {
            cleanLink.contains("2160") || cleanLink.contains("4k") -> 2160
            cleanLink.contains("1080") -> 1080
            cleanLink.contains("720") -> 720
            else -> 480
        }
        
        callback(
            newExtractorLink(
                source = "1porn",
                name = "1porn ${quality}p",
                url = cleanLink,
            ) {
                this.quality = quality
                this.referer = mainUrl
            }
        )
    }
}
