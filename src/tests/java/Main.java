import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static Queue<String> linkQueue = new ConcurrentLinkedQueue<String>();
    private static Set<String> linkSet = Collections.synchronizedSet(new HashSet<String>());
    private static Set<String> emailSet = Collections.synchronizedSet(new HashSet<String>());
    private static Set<String> robotsTxtSet = Collections.synchronizedSet(new HashSet<String>());
    private static Map<String, Boolean> robotsTxtMap = Collections.synchronizedMap(new HashMap<String, Boolean>());
    private static List<String> agentNames = Collections.synchronizedList(new ArrayList<String>());
    private static SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private static BaseRobotRules rules = null;
    private static URL u = null;
    private static URLConnection connection = null;
    private static byte[] content = null;
    private static Document doc = null;
    private static WebScraper webScraper;
    private static Logger logger = LoggerFactory.getLogger(WebScraper.class);
    private static int emailCount = 0;
    public static void main(String[] args) throws IOException {
        WebScraper webScraper = new WebScraper();

        Map<String, String> env = System.getenv();
        String endpoint = env.get("db_connection");
        System.out.println(env.get("user"));

        String connectionUrl =
                "jdbc:sqlserver://" + endpoint + ";"
                        + "database=" + env.get("database") + ";"
                        + "user=" + env.get("user") + ";"
                        + "password=" + env.get("password") + ";"
                        + "encrypt=true;"
                        + "trustServerCertificate=true;"
                        + "loginTimeout=30;";

        try (Connection connection = DriverManager.getConnection(connectionUrl)) {
            // Multi-row insert - Much more efficient
            String sql = "INSERT INTO Emails (EmailID, Email, Source, TimeStamp) VALUES (?, ?, ?, ?)";
            for (int i = 0; i < 3; i++) {
                sql = sql + ", (?, ?, ?, ?)";
            }
            sql = sql + ";";

            PreparedStatement stmt = connection.prepareStatement(sql);
            // Set all parameters in one go
                stmt.setString( 1, String.valueOf(1));
                stmt.setString( 2, String.valueOf("abc@gmail.com"));
                stmt.setString( 3, String.valueOf("HTTPS://www.gmail.com"));
                stmt.setString( 4, String.valueOf(Timestamp.valueOf(LocalDateTime.now())));

            // And so on...
            stmt.execute();

        } catch (SQLException e) {
            e.printStackTrace();
        }

//
//        Document doc = Jsoup.connect("https://touro.edu")
//                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
//                .get();
//
//        Elements html = doc.getElementsByTag("a");
//        scrapeLinks(html);
//        System.out.println(html.toString());
//        System.out.println("linkset:" + linkSet);
//
//
//        System.out.println(isScrapeAllowed("https://www.x.com/privacy"));
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

    }}

//    public static synchronized boolean isScrapeAllowed(String link) throws IOException {
//        Pattern pattern = Pattern.compile("(?i)[-a-zA-Z0-9@%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b");
//        Matcher matcher = pattern.matcher(link);
//        String url = "";
//        if (matcher.find()){
//            url = "https://" + matcher.group() + "/robots.txt";
//        }
//        if (!isValidURL(link)){
//            return false;
//        }
//        if (!robotsTxtSet.add(url)){
//            return robotsTxtMap.get(url);
//        }
//        u = new URL(url);
//        connection = u.openConnection();
//        content = IOUtils.toByteArray(connection);
//        rules = parser.parseContent(url, content, "text/plain", agentNames);
//        boolean isAllowed = rules.isAllowed(url);
//        robotsTxtMap.put(url, isAllowed);
//        return isAllowed;
//    }
//
//    public static void scrapeLinks(Elements html) {
//        String htmlString = html.toString();
//        Pattern pattern = Pattern.compile("(?i)(?!.*(\\.png|\\.jpg|\\.gif|\\.pdf|twitter|x\\.com).*)https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)");
//        Matcher matcher = pattern.matcher(htmlString);
//        while (matcher.find()) {
//            String link = matcher.group(0).toUpperCase();
//            if (linkSet.add(link));{
//                linkQueue.add(link);
//            }
//        }
//    }
//
//    public static void scrapeEmails(Elements html) {
//        String htmlString = html.toString();
//        Pattern pattern = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}");
//        Matcher matcher = pattern.matcher(htmlString);
//        while (matcher.find()) {
//            String email = matcher.group();
//            emailSet.add(email.toUpperCase());
//        }
//    }
//
//    public static synchronized boolean isValidURL(String link) {
//        UrlValidator validator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.NO_FRAGMENTS + UrlValidator.ALLOW_LOCAL_URLS);
//        return validator.isValid(link);
//    }

