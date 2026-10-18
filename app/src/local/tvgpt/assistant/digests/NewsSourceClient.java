package local.tvgpt.assistant.digests;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import javax.xml.parsers.*;
import okhttp3.*;
import org.json.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** No subscription headers enter this client. DNS checks apply to every socket and redirect. */
public final class NewsSourceClient {
  private final OkHttpClient client =
      new OkHttpClient.Builder()
          .followRedirects(false)
          .followSslRedirects(false)
          .retryOnConnectionFailure(false)
          .callTimeout(30, TimeUnit.SECONDS)
          .dns(
              host -> {
                List<InetAddress> ips = Dns.SYSTEM.lookup(host);
                for (InetAddress ip : ips)
                  if (!NewsPolicy.publicAddress(ip))
                    throw new UnknownHostException("Непубличный источник");
                return ips;
              })
          .build();

  public byte[] download(String raw, int limit) throws Exception {
    String url = NewsPolicy.normalizeUrl(raw);
    for (int redirects = 0; redirects <= 3; redirects++) {
      try (Response response =
          client
              .newCall(new Request.Builder().url(url).header("User-Agent", "TVGPT-News/1").build())
              .execute()) {
        if (response.isRedirect()) {
          String location = response.header("Location");
          if (location == null) throw new IOException("Redirect без адреса");
          url = NewsPolicy.normalizeUrl(new URI(url).resolve(location).toString());
          continue;
        }
        if (!response.isSuccessful() || response.body() == null)
          throw new IOException("Источник HTTP " + response.code());
        if (response.body().contentLength() > limit)
          throw new IOException("Источник слишком большой");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (InputStream in = response.body().byteStream()) {
          int n;
          while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) throw new IOException("Источник слишком большой");
            out.write(buffer, 0, n);
          }
        }
        return out.toByteArray();
      }
    }
    throw new IOException("Слишком много перенаправлений");
  }

  public JSONArray read(String url) throws Exception {
    String raw = new String(download(url, 2 * 1024 * 1024), "UTF-8");
    String feed = feedUrl(raw, url);
    if (!feed.isEmpty() && !feed.equals(url))
      try {
        return parse(new String(download(feed, 2 * 1024 * 1024), "UTF-8"), feed);
      } catch (Exception ignored) {
      }
    JSONArray links = sectionLinks(raw, url);
    if (links.length() == 0) return parse(raw, url);
    JSONArray articles = new JSONArray();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
    for (int i = 0; i < links.length() && System.nanoTime() < deadline; i++) {
      try {
        String link = links.getString(i);
        articles.put(parseArticle(new String(download(link, 2 * 1024 * 1024), "UTF-8"), link));
      } catch (Exception ignored) {
      }
    }
    return articles;
  }

  /** Exact article metadata never follows a site-wide alternate feed. */
  public JSONArray article(String url) throws Exception {
    return new JSONArray()
        .put(parseArticle(new String(download(url, 2 * 1024 * 1024), "UTF-8"), url));
  }

  public static JSONObject parseArticle(String raw, String url) throws Exception {
    if ("sport.business-gazeta.ru".equals(new URI(url).getHost())) {
      Matcher preload = Pattern.compile("(?is)<news-list-component\\b[^>]*>").matcher(raw);
      if (preload.find()) {
        String encoded = attribute(preload.group(), "preloadnews");
        if (!encoded.isEmpty()) {
          JSONObject news = new JSONObject(entities(encoded));
          String body = news.optString("body"), photo = "";
          if (!news.optString("pic").isEmpty())
            photo =
                NewsPolicy.normalizeUrl(
                    new URI("https://img-sport.business-gazeta.ru")
                        .resolve(news.getString("pic"))
                        .toString());
          if (photo.isEmpty()) photo = htmlImage(body, url);
          return new JSONObject()
              .put("url", NewsPolicy.normalizeUrl(url))
              .put("title", plain(news.optString("title"), 120))
              .put("text", plain(body, 12000))
              .put("published_at", date(news.optString("public_time")))
              .put("image_url", photo);
        }
      }
    }
    JSONArray parsed = parse(raw, url);
    String expected = NewsPolicy.normalizeUrl(url);
    for (int i = 0; i < parsed.length(); i++) {
      JSONObject a = parsed.getJSONObject(i);
      if (NewsPolicy.normalizeUrl(a.optString("url")).equals(expected)) return a;
    }
    return new JSONObject().put("url", expected).put("image_url", "");
  }

  public static JSONArray parse(String raw, String url) throws Exception {
    JSONArray out = new JSONArray();
    String lower = raw.toLowerCase(Locale.ROOT);
    if (lower.contains("<rss") || lower.contains("<feed")) {
      if (lower.contains("<!doctype") || lower.contains("<!entity"))
        throw new IOException("DTD запрещён");
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setExpandEntityReferences(false);
      DocumentBuilder parser = f.newDocumentBuilder();
      parser.setEntityResolver(
          (publicId, systemId) -> {
            throw new org.xml.sax.SAXException("External entity");
          });
      Document doc = parser.parse(new InputSource(new StringReader(raw)));
      NodeList items = doc.getElementsByTagName("item");
      if (items.getLength() == 0) items = doc.getElementsByTagNameNS("*", "entry");
      for (int i = 0; i < Math.min(30, items.getLength()); i++) {
        Element e = (Element) items.item(i);
        String link = child(e, "link");
        NodeList links = e.getElementsByTagName("link");
        for (int j = 0; j < links.getLength(); j++) {
          Element l = (Element) links.item(j);
          if (l.hasAttribute("href")
              && (l.getAttribute("rel").isEmpty() || l.getAttribute("rel").equals("alternate")))
            link = l.getAttribute("href");
        }
        if (link.isEmpty()) continue;
        String image = "";
        NodeList enclosures = e.getElementsByTagName("enclosure");
        for (int j = 0; j < enclosures.getLength(); j++) {
          Element media = (Element) enclosures.item(j);
          if (media.getAttribute("type").startsWith("image/")) image = media.getAttribute("url");
        }
        if (image.isEmpty()) {
          NodeList media = e.getElementsByTagNameNS("*", "thumbnail");
          if (media.getLength() > 0) image = ((Element) media.item(0)).getAttribute("url");
        }
        String description = child(e, "description");
        if (description.isEmpty()) description = child(e, "summary");
        if (image.isEmpty()) image = htmlImage(description, url);
        String date = child(e, "pubDate");
        if (date.isEmpty()) date = child(e, "published");
        if (date.isEmpty()) date = child(e, "updated");
        out.put(
            new JSONObject()
                .put("url", new URI(url).resolve(link).toString())
                .put("title", plain(child(e, "title"), 120))
                .put("text", plain(description, 12000))
                .put("published_at", date(date))
                .put("image_url", image));
      }
    } else {
      String body = region(raw, "itemprop", "articleBody");
      if (body.isEmpty()) body = region(raw, "class", "article__body");
      if (body.isEmpty()) body = region(raw, "tag", "article");
      String published = meta(raw, "article:published_time");
      if (published.isEmpty()) {
        Matcher time = Pattern.compile("(?is)<time\\b[^>]*>").matcher(raw);
        if (time.find()) published = attribute(time.group(), "datetime");
      }
      String photo = body.isEmpty() ? "" : htmlImage(body, url);
      if (photo.isEmpty()) photo = htmlImage(raw, url);
      out.put(
          new JSONObject()
              .put("url", url)
              .put("title", plain(meta(raw, "og:title"), 120))
              .put("text", plain(body.isEmpty() ? raw : body, 12000))
              .put("published_at", date(published))
              .put("image_url", photo));
    }
    return out;
  }

  /** Discover publication URLs, never use a section landing page as a news item. */
  public static JSONArray sectionLinks(String html, String base) throws Exception {
    if (html.toLowerCase(Locale.ROOT).contains("<rss")
        || html.toLowerCase(Locale.ROOT).contains("<feed")
        || !region(html, "itemprop", "articleBody").isEmpty()
        || !meta(html, "article:published_time").isEmpty()) return new JSONArray();
    URI uri = new URI(base);
    String scope = html;
    if ("sport.business-gazeta.ru".equals(uri.getHost())) {
      if (uri.getPath().matches("/(article|news)/[0-9]+/?")) return new JSONArray();
      String section = uri.getPath().replaceAll("^/|/$", "");
      String tab = region(html, "id", section);
      // This publisher includes other sports and recommendations alongside the selected tab.
      if (!tab.isEmpty()) {
        scope = region(html, "class", "top-material") + tab;
        Matcher rows =
            Pattern.compile(
                    "(?is)<div\\b[^>]*class=['\"][^'\"]*\\bline-article\\b[^'\"]*['\"][^>]*>")
                .matcher(html);
        while (rows.find()) scope += element(html, rows.start(), rows.end(), "div");
      }
    } else {
      String main = region(html, "tag", "main");
      if (!main.isEmpty()) scope = main;
    }
    LinkedHashSet<String> found = new LinkedHashSet<>();
    Matcher anchors = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a\\s*>").matcher(scope);
    while (anchors.find() && found.size() < 18) {
      String href = attribute(anchors.group(1), "href");
      String title = plain(anchors.group(2), 300);
      if (title.length() < 20) title = attribute(anchors.group(1), "title");
      if (href.isEmpty() || href.contains("#") || title.length() < 20) continue;
      try {
        String link = NewsPolicy.normalizeUrl(uri.resolve(href).toString());
        URI target = new URI(link);
        String path = target.getPath();
        if (!Objects.equals(uri.getHost(), target.getHost()) || link.equals(base)) continue;
        if (path.matches(
            ".*(/(article|news|story|stories|post|posts)/.+|/\\d{4}/.+|[^/]*-[^/]*-[^/]*).*"))
          found.add(link);
      } catch (Exception ignored) {
      }
    }
    JSONArray result = new JSONArray();
    for (String link : found) result.put(link);
    return result;
  }

  private static String region(String html, String attr, String value) {
    Matcher tags = Pattern.compile("(?is)<([a-z][a-z0-9]*)\\b[^>]*>").matcher(html);
    while (tags.find()) {
      String actual = attr.equals("tag") ? tags.group(1) : attribute(tags.group(), attr);
      boolean matches =
          attr.equals("class")
              ? Arrays.asList(actual.split("\\s+")).contains(value)
              : actual.equalsIgnoreCase(value);
      if (matches) return element(html, tags.start(), tags.end(), tags.group(1));
    }
    return "";
  }

  private static String element(String html, int start, int end, String tag) {
    Matcher tags = Pattern.compile("(?is)</?" + Pattern.quote(tag) + "\\b[^>]*>").matcher(html);
    tags.region(end, html.length());
    int depth = 1;
    while (tags.find()) {
      if (tags.group().startsWith("</")) depth--;
      else if (!tags.group().endsWith("/>")) depth++;
      if (depth == 0) return html.substring(start, tags.end());
    }
    return "";
  }

  private static String child(Element e, String name) {
    NodeList nodes = e.getElementsByTagNameNS("*", name);
    if (nodes.getLength() == 0) nodes = e.getElementsByTagName(name);
    return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
  }

  private static Object date(String text) {
    try {
      if (text.matches("\\d{4}-\\d{2}-\\d{2}MSK\\d{2}:\\d{2}"))
        return LocalDateTime.parse(text.replace("MSK", "T"))
            .atZone(ZoneId.of("Europe/Moscow"))
            .toInstant()
            .toEpochMilli();
      return Instant.parse(text.trim()).toEpochMilli();
    } catch (Exception e) {
      try {
        return ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
            .toInstant()
            .toEpochMilli();
      } catch (Exception ignored) {
        return JSONObject.NULL;
      }
    }
  }

  public static String plain(String text, int max) {
    String result =
        entities(text)
            .replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>", " ")
            .replaceAll("(?s)<[^>]*>", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
    return result.substring(0, Math.min(max, result.length()));
  }

  private static String entities(String text) {
    return text.replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&");
  }

  private static String attribute(String tag, String name) {
    Matcher m = Pattern.compile("(?is)\\b" + name + "\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
    return m.find() ? m.group(2).replace("&amp;", "&") : "";
  }

  private static String meta(String html, String name) {
    Matcher m = Pattern.compile("(?is)<meta\\b[^>]*>").matcher(html);
    while (m.find()) {
      String tag = m.group();
      if (attribute(tag, "property").equalsIgnoreCase(name)
          || attribute(tag, "name").equalsIgnoreCase(name)) return attribute(tag, "content");
    }
    return "";
  }

  public static String feedUrl(String html, String base) {
    Matcher links = Pattern.compile("(?is)<link\\b[^>]*>").matcher(html);
    while (links.find()) {
      String tag = links.group(), type = attribute(tag, "type");
      if (attribute(tag, "rel").equalsIgnoreCase("alternate")
          && (type.equalsIgnoreCase("application/rss+xml")
              || type.equalsIgnoreCase("application/atom+xml")))
        try {
          return NewsPolicy.normalizeUrl(new URI(base).resolve(attribute(tag, "href")).toString());
        } catch (Exception ignored) {
        }
    }
    return "";
  }

  public static String htmlImage(String html, String base) {
    String value = meta(html, "og:image");
    if (value.isEmpty()) value = meta(html, "twitter:image");
    if (value.isEmpty()) {
      Matcher m = Pattern.compile("(?is)<img\\b[^>]*>").matcher(html);
      while (m.find() && value.isEmpty()) {
        String tag = m.group();
        value = attribute(tag, "data-src");
        if (value.isEmpty()) value = attribute(tag, "src");
        if (value.toLowerCase(Locale.ROOT).contains(".svg")) value = "";
      }
    }
    try {
      return value.isEmpty()
          ? ""
          : NewsPolicy.normalizeUrl(new URI(base).resolve(value).toString());
    } catch (Exception e) {
      return "";
    }
  }
}
