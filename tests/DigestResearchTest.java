package local.tvgpt.assistant.digests;

import org.json.*;

public final class DigestResearchTest {
  public static void main(String[] args) throws Exception {
    JSONObject malicious =
        new JSONObject()
            .put(
                "output",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("type", "function_call")
                            .put("name", "digest_schedule_mutate")));
    try {
      DigestResearchProtocol.parse(malicious);
      throw new AssertionError("source injection executed");
    } catch (IllegalArgumentException expected) {
    }
    JSONObject result =
        new JSONObject()
            .put(
                "output",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("type", "message")
                            .put(
                                "content",
                                new JSONArray()
                                    .put(
                                        new JSONObject()
                                            .put("type", "output_text")
                                            .put(
                                                "text",
                                                "```json\n"
                                                    + "[{\"title\":\"News\",\"url\":\"https://example.org/news\"}]\n"
                                                    + "```")))));
    if (DigestResearchProtocol.parse(result).length() != 1)
      throw new AssertionError("lost JSON result");
    String rss =
        "<rss><channel><item><title>A</title><link>https://example.org/a</link><description><![CDATA[<img"
            + " src='https://example.org/photo.jpg'>]]></description></item></channel></rss>";
    JSONArray feed = NewsSourceClient.parse(rss, "https://example.org/rss");
    if (!feed.getJSONObject(0).getString("image_url").equals("https://example.org/photo.jpg")
        || !feed.getJSONObject(0).isNull("published_at"))
      throw new AssertionError("lost source image or invented date");
    try {
      NewsSourceClient.parse(
          "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + rss, "https://example.org");
      throw new AssertionError("DTD accepted");
    } catch (java.io.IOException expected) {
    }
    if (!NewsSourceClient.htmlImage(
            "<meta content='https://example.org/a.jpg' property='og:image'>",
            "https://example.org/")
        .equals("https://example.org/a.jpg")) throw new AssertionError("OG cover fallback");
    if (!NewsSourceClient.feedUrl(
            "<link type='application/rss+xml' href='/rss.xml' rel='alternate'>",
            "https://example.org/")
        .equals("https://example.org/rss.xml")) throw new AssertionError("feed discovery");
    JSONObject exact =
        NewsSourceClient.parseArticle(
            "<link rel='alternate' type='application/rss+xml' href='/rss'><meta property='og:image'"
                + " content='https://example.org/exact.jpg'>",
            "https://example.org/older-article");
    if (!exact.getString("image_url").equals("https://example.org/exact.jpg"))
      throw new AssertionError("unrelated feed image used");
    String section =
        "<div class='top-material'><a href='/article/1'>Главная волейбольная новость</a></div><div"
            + " id='football' role='tabpanel'><a href='/news/2'>Посторонний футбольный"
            + " матч</a></div><div id='volleyball' role='tabpanel'><div><a href='/news/3'>Первая"
            + " волейбольная новость</a><a href='/news/3#comments'>28</a></div><a"
            + " href='/news/4'>Вторая волейбольная новость</a></div><aside><a"
            + " href='/article/5'>Посторонний рекомендуемый сюжет</a></aside>";
    JSONArray links =
        NewsSourceClient.sectionLinks(section, "https://sport.business-gazeta.ru/volleyball");
    if (links.length() != 3 || !links.getString(2).endsWith("/news/4"))
      throw new AssertionError("section must yield separate scoped article URLs: " + links);
    JSONObject body =
        NewsSourceClient.parseArticle(
            "<meta property='og:image' content=''><meta property='og:image'"
                + " content='/logo.png'><meta property='og:title' content='Настоящий"
                + " заголовок'><nav>Посторонняя навигация</nav><time"
                + " datetime='2026-10-18MSK08:50'></time><div itemprop='articleBody'><p>Текст самой"
                + " публикации.</p><figure><img data-src='/photo.jpg'></figure></div>",
            "https://sport.business-gazeta.ru/article/1");
    if (!body.getString("image_url").endsWith("/photo.jpg")
        || body.isNull("published_at")
        || body.getString("text").contains("навигация"))
      throw new AssertionError("article metadata/body/photo must belong to article: " + body);
    String preloaded =
        new JSONObject()
            .put("title", "Новость")
            .put("public_time", "2026-10-18T05:33:00.000000Z")
            .put("pic", "/images/match.jpg")
            .put(
                "body", "<p>Счёт матча 3:1</p><img src=\"https://example.org/icons/facebook.jpg\">")
            .toString();
    JSONObject embedded =
        NewsSourceClient.parseArticle(
            "<news-list-component :preloadnews='"
                + preloaded.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
                + "'></news-list-component>",
            "https://sport.business-gazeta.ru/news/123");
    if (!embedded
            .getString("image_url")
            .equals("https://img-sport.business-gazeta.ru/images/match.jpg")
        || !embedded.getString("text").equals("Счёт матча 3:1")
        || embedded.isNull("published_at"))
      throw new AssertionError("preloaded news data lost: " + embedded);
    System.out.println("DigestResearchTest passed");
  }
}
