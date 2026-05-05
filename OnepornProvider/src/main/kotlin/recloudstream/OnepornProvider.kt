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

    // إضافة Headers لمحاكاة متصفح حقيقي وتجنب حظر البوتات
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "en-US,en;q=0.9"
    )

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
            try {
                val pageUrl = if (page <= 1) url else "${url.trimEnd('/')}/$page/"
                // استخدام الـ headers في الطلب
                val response = app.get(pageUrl, headers = headers).document
                
                // تحديث الـ selectors بناءً على هيكلية الموقع الحالية
                val videos = response.select("div.item, div.video-item, article.item").mapNotNull {
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
        val doc = app.get(searchUrl, headers = headers).document
        return doc.select("div.item, div.video-item, article.item").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // البحث عن العنوان في وسوم متنوعة لضمان الحصول عليه
        val title = selectFirst(".title, a[title], h3, .video-title, .video-name")?.text()?.trim() 
            ?: selectFirst("a")?.attr("title")?.trim()
            ?: return null
            
        val href = selectFirst("a")?.attr("href") ?: return null
        if (href.contains("/models/") || href.contains("/categories/")) return null
        
        val link = fixUrl(href)
        
        // المواقع غالباً تستخدم lazy loading، لذا نبحث في data-src أولاً
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
            ?: doc.selectFirst("link[rel='image_src']")?.attr("href")
            
        val description = doc.selectFirst("div.description, .video-description, .video-details-info, meta[name='description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim()
        
        val tags = doc.select("div.video-info-item a[href*='/categories/'], .tags a, .categories a, .video-tags a").map { it.text() }

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
        val response = app.get(data, headers = headers)
        val html = response.text

        // محاولة استخراج الروابط المباشرة من الـ HTML
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
        
        // إذا لم نجد روابط Regex، قد نحتاج للبحث في الـ iframe أو مشغلات أخرى مستقبلاً
        return true
    }
}
