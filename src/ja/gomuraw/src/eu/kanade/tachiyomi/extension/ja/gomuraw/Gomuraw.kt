package eu.kanade.tachiyomi.extension.ja.gomuraw

import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Gomuraw : ParsedHttpSource() {
override val name = "Gomuraw"
override val baseUrl = "https://gomuraw.com"
override val lang = "ja"
override val supportsLatest = true

    // [CRITICAL] 必须模拟真实浏览器 UA 才能通过 Cloudflare 基础校验
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", baseUrl)
                .build()
            chain.proceed(request)
        }
        .build()

    // 1. 热门漫画解析
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/trending/page/$page", headers)
    override fun popularMangaSelector() = "div.post-item"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select("a").attr("href").removePrefix(baseUrl)
        title = element.select("h3, .title").text().trim()
        thumbnail_url = element.select("img").attr("abs:src")
    }
    override fun popularMangaNextPageSelector() = "a.next"

    // 2. 最新更新解析
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/newest/page/$page", headers)
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // 3. 搜索功能
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/manga?keyword=$query&page=$page", headers)
    }
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // 4. 详情页解析
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.manga-info")
        title = document.select("h1").text()
        author = document.select("div.author a").text()
        description = document.select("div.summary").text()
        genre = document.select("div.genres a").joinToString { it.text() }
        status = when (document.select("div.status").text()) {
            "連載中" -> SManga.ONGOING
            "完結" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // 5. 章节列表解析
    override fun chapterListSelector() = "ul.chapter-list li a"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.attr("href").removePrefix(baseUrl)
        name = element.select("span.chapter-name").text().ifEmpty { element.text() }
        date_upload = 0L // 该站通常不直接显示时间戳，需额外解析
    }

    // 6. 图片页解析 (核心)
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.read-content img, div.page-break img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")
}
