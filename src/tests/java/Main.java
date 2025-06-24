import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static Queue<String> linkQueue = new ConcurrentLinkedQueue<>();
    private static Set<String> linkSet = new HashSet<>();
    private static Set<String> emailSet = new HashSet<>();
    private static Collection<String> agentNames = new ArrayList<>();
    private static SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private static BaseRobotRules rules = null;
    private static URL u = null;
    private static URLConnection connection = null;
    private static byte[] content = null;
    private static Document doc = null;
    private static WebScraper webScraper;
    private static Logger logger = LoggerFactory.getLogger(WebScraper.class);
    public static void main(String[] args) throws IOException {
        WebScraper webScraper = new WebScraper();

        Document doc = Jsoup.connect("https://touro.edu")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                .get();


        Elements html = doc.getElementsByTag("a");
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)(?!.*(png|jpg|gif).*$)https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)");
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String link = matcher.group().toUpperCase();
            System.out.println(link);
        }
//        for (Element element : html) {
//            String link = element.attr("href");
//            linkSet.add(link);
//        }
//        //scrapeEmails(emails);
//        System.out.println(linkSet);
//        scrapeLinks(emails);
//        System.out.println(linkSet);
        //System.out.println(emailSet);

        //System.out.println(isScrapeAllowed("www.linkedin.com"));

    }

    public static boolean isScrapeAllowed(String link) throws IOException {
        //boolean result = false;
        String url = "https://" + link + "/robots.txt";
        u = new URL(url);
        connection = u.openConnection();
        content = IOUtils.toByteArray(connection);
        rules = parser.parseContent(url, content, "text/plain", agentNames);

        return rules.isAllowed(link);

    }

    public static void scrapeLinks(Elements html) {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("[(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)");
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String link = matcher.group().toUpperCase();
            if (linkSet.add(link));{
                linkQueue.add(link);
            }
        }
    }

    public static void scrapeEmails(Elements html) {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}");
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String email = matcher.group();
            emailSet.add(email.toUpperCase());
        }
    }
}
