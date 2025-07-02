import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
Pseudocode:
1- Connect to touro.edu
    - Use jsoup
2- Pull out all emails on touro.edu and all links to be searched for email addresses
    - Use crawler commons library to check if robots.txt allows scraping the link
    - use jsoup to fetch the email html notation
    - use absUrl() for relative urls's
    - implement error/exception handling logic
    - Add rate limiting
    - use regex to extract only the email addresses (group 1): <a\s+href=\"mailto:(\w+@(\w+\.)+?(com|edu|net|org|))\">.*</a>
3- Create a set for the emails to prevent duplicates
4- Canonize the email addresses to be fully capitalized
    - toUpperCase()
5- Add the canonized emails to the set
6- Create a set for the links to prevent duplicates
7- Create a queue for the links to access them in a BFS-consistent way
8- Add the links on touro.edu to the set
9- If the linkSet.add() method returns true, that link has not yet been scraped so it should be added to the queue
10- Go through each link in the queue and repeat steps 2-9 (except for creating new sets and queues)
11- Steps 2-9 should be looped in a while loop that continues until the emailSet has a size of 10,000
12- Once the while loop ends, connect to the database and bulk add the emails to the database
 */
public class WebScraper implements Runnable {
    private static volatile Queue<String> linkQueue = new ConcurrentLinkedQueue<String>();
    private static volatile Set<String> linkSet = Collections.synchronizedSet(new HashSet<String>());
    private static volatile Set<String> emailSet = Collections.synchronizedSet(new HashSet<String>());
    private static volatile Set<String> robotsTxtSet = Collections.synchronizedSet(new HashSet<String>());
    private static volatile Map<String, Boolean> robotsTxtMap = Collections.synchronizedMap(new HashMap<String, Boolean>());
    private static volatile List<String> agentNames = Collections.synchronizedList(new ArrayList<String>());
    private static volatile SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
    private BaseRobotRules rules = null;
    private URL u = null;
    private URLConnection connection = null;
    private byte[] content = null;
    private Document doc = null; //This wont work on multi threading
    private static volatile Logger logger = LoggerFactory.getLogger(WebScraper.class);
    private static volatile AtomicInteger emailCount = new AtomicInteger(0);
    private static volatile Map<String, String> env = Collections.synchronizedMap(new HashMap<>());
    private static volatile Queue<EmailData> emailDataQueue = new ConcurrentLinkedQueue<EmailData>();
    private static final int MAX_CONNECTIONS = 10;
    private static final ConnectionPool connectionPool = new ConnectionPool(MAX_CONNECTIONS);

    public static synchronized void main(String[] args) throws MalformedURLException, IOException {
        agentNames.add("Mozilla/5.0");
        linkQueue.add("HTTPS://WWW.TOURO.EDU");
        linkSet.add("HTTPS://WWW.TOURO.EDU");
        ExecutorService executor = Executors.newFixedThreadPool(550);//10threads & 10tasks = 17 emails in 10 minutes; 16threads & 1000tasks = 40emails; 500threads & 1000tasks = 155 emails;
        for (int i = 0; i < 550; i++) {      //1000 & 1000 = SLOW; 400 & 400 = 216; 300 and 300 = 202; 600 & 600 similar to 500;
            executor.submit(new WebScraper()); //500 - 44s,20s, 40s; 550 - 22s, 29s, 21s; 600 - 60s, 31s,; 575 - 21s, 34s, 21s,
        }
        executor.shutdown();
    }

    public WebScraper() {

    }

    public synchronized void run() {

            while (emailSet.size() < 10000) {

                if (emailSet.size() >= 3) {
                    logger.info("entered database insertion section");
                    env = System.getenv();
                    String endpoint = env.get("db_connection");

                    String connectionUrl =
                            "jdbc:sqlserver://" + endpoint + ";"
                                    + "database=" + env.get("database") + ";"
                                    + "user=" + env.get("user") + ";"
                                    + "password=" + env.get("password") + ";"
                                    + "encrypt=true;"
                                    + "trustServerCertificate=true;"
                                    + "loginTimeout=30;";

                    synchronized (this) {
                        try (Connection connection = DriverManager.getConnection(connectionUrl);
                             Statement statement = connection.createStatement()) {
                            logger.info("entered database insertion section synchronized part 1");
                            // Multi-row insert - Much more efficient
                            String sql = "INSERT INTO Emails (EmailID, Email, Source, TimeStamp) VALUES (?, ?, ?, ?)";
                            for (int i = 0; i < 3; i++) {
                                sql = sql + ", (?, ?, ?, ?)";
                            }
                            sql = sql + ";";
                            logger.info("entered database insertion section synchronized part 2");
                            PreparedStatement stmt = connection.prepareStatement(sql);
                            // Set all parameters in one go
                            int counter = 0;
                            for (int i = 0; i < 3; i++) {
                                EmailData emailData = emailDataQueue.poll();
                                stmt.setString(counter + i + 1, String.valueOf(emailData.getEmailId()));
                                stmt.setString(counter + i + 2, String.valueOf(emailData.getEmail()));
                                stmt.setString(counter + i + 3, String.valueOf(emailData.getSource()));
                                stmt.setString(counter + i + 4, String.valueOf(emailData.getTimestamp()));
                                counter += 3;
                            }
                            logger.info("entered database insertion section synchronized part 3");
                            // And so on...
                            stmt.execute();
                            logger.info("executed database insertion section");
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                }


                String link;
                while (true) {
                    System.out.println("trying to get next");
                    link = linkQueue.poll();
                    if (link != null) {
                        break;
                    }
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    //TimeUnit.SECONDS.sleep(1);
//                        logger.info("Link: " + link);
//                        logger.warn("Link: " + link);
//                        logger.debug("Link: " + link);
                    this.doc = Jsoup.connect(link)
                            //.ignoreHttpErrors(true)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36")
                            .get();
//                        logger.info("Link: " + link);
//                        logger.warn("Link: " + link);
//                        logger.debug("Link: " + link);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    continue;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    continue;
                } catch (HttpStatusException e) {
                    e.printStackTrace();
                    continue;
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }


//                            logger.info("Scraping html of" + link);
                        Elements html = doc.getElementsByTag("a");
                try {
                    scrapeLinks(html);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
//                            logger.info("scraped html of " + link);
//                            logger.info("Scraping emails of " + link);
                        html = doc.getAllElements();
//                            logger.info("scraped emails of " + link);
                        scrapeEmails(html, link);
//                            logger.info("scraped emails of " + link);
                            System.out.println(doc.title());
                            System.out.println("emailSet:" + emailSet);
                            System.out.println("emailset size:" + emailSet.size());

            }
    }


    /**
     * Method to remove the surrounding html tags/attributes from the plain email address
     * and make the email uppercase
     * and add the email to the emailSet
     * @param html, an Elements object
     */

    public void scrapeEmails(Elements html, String link) {
//        logger.info("from scrapeEmails method: scraping emails of " + link);
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9\\.-_]+\\.[A-Z]{2,10}"); //TODO get a better regex from very good developers on stackoverflow
        Matcher matcher = pattern.matcher(htmlString);
//        logger.info("before while loop");
        while (matcher.find()) {
//            logger.info("inside while loop");
            String email = matcher.group().toUpperCase();
            if(email.endsWith(".PNG") || email.endsWith(".JPG") || email.endsWith(".JPEG")
            || email.endsWith(".GIF") || email.endsWith(".PDF") || email.endsWith(".WEBP")
            || email.contains("SENTRY") || email.endsWith(".SVG") || email.endsWith(".WEBPACK")
            || email.endsWith(".CSS") || email.endsWith(".JS") || email.endsWith(".HTML")) {
                continue;
            }
//            logger.info("inside while loop after to upper case");//(?!.*(\.png|\.jpg|\.gif|\.pdf).*)
            if (emailSet.add(email)){
                logger.info("emailID: " + emailCount.incrementAndGet() + " email: " + email);
                emailDataQueue.add(new EmailData(emailCount, email, link, Timestamp.valueOf(LocalDateTime.now())));
            }
        }
//        logger.info("after while loop");
    }

    /**
     * Method to remove the surrounding html tags/attributes from the plain link without the https:// part
     * and make the link uppercase
     * @param html, an Elements object
     */
    //   https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()!@:%_\+.~#?&\/\/=]*)

    public void scrapeLinks(Elements html) throws IOException {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)(?!.*(\\.png|\\.jpg|\\.gif|\\.pdf|twitter|vimeo|x\\.com|\\.gov).*)https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)");
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String link = matcher.group(0).toUpperCase();
            if (isScrapeAllowed(link) && linkSet.add(link)) {
                linkQueue.add(link);
            }
        }
    }

    /**
     * Method to check the link's robot.txt to check whether the site allows scraping
     * @param link
     * @return a boolean which represents the website's allowing or prohibiting scraping
     * @throws IOException
     */
    public boolean isScrapeAllowed(String link) { //todo once thigns are working try to remove this seemingly redundant syncrhonized
        Pattern pattern = Pattern.compile("(?i)[-a-zA-Z0-9@%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b");
        Matcher matcher = pattern.matcher(link);
        String url = "";
        if (matcher.find()){
            url = "https://" + matcher.group() + "/robots.txt";
        }
//        logger.info("link: " + link);
        if (!isValidURL(link)){
//            logger.info("link: " + link + " is not a valid URL");
            return false;
        }
        if (!robotsTxtSet.add(url)){
//            logger.info("robotsTxtSet: " + url + " already exists");
            return robotsTxtMap.get(url);
        }

        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            robotsTxtMap.put(url, false);
//            logger.info("isAllowed: " + false);
            return false;
        }
        try {
            connection = u.openConnection();
        } catch (IOException e) {
            robotsTxtMap.put(url, false);
//            logger.info("isAllowed: " + false);
            return false;
        }
//        logger.info("connection: " + connection);
        try {
            content = IOUtils.toByteArray(connection);
        } catch (IOException e) {
            robotsTxtMap.put(url, false);
//            logger.info("isAllowed: " + false);
            return false;
        } //emailSet:[%20MARKETING@HIRINGTHING.COM, PRIVACY@IMPERVA.COM, CANGRADE@4X.PNG, HT-ATS-AND-OB-ILLUSTRATION-HOMEPAGE@2X-1024X893.PNG, SUPPORT@HIRINGTHING.COM, HT-ATS-ILLUSTRATION-BOTH-HEX-FOR-LIGHT@2X-1024X685.PNG, 3BBE57A973254129BCB93E47DC0CC46F@O343074.INGEST.SENTRY.IO, INFO@HRLOGICS.COM, HIRINGTHING-LOGO-BLUE@2X.PNG, CUSTOMERSERVICE@ADVANCED-ONLINE.COM, MARKETING@HIRINGTHING.COM, INFO@JOBMA.COM]
        rules = parser.parseContent(url, content, "text/plain", agentNames);
            boolean isAllowed = rules.isAllowed(url);
            robotsTxtMap.put(url, isAllowed);
//            logger.info("isAllowed: " + isAllowed);
            return isAllowed;
    }

    public boolean isValidURL(String link) {
        UrlValidator validator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.NO_FRAGMENTS + UrlValidator.ALLOW_LOCAL_URLS);
        if (doc.title().contains("404")
                || doc.title().contains("Not Found")
                || link.contains("TWITTER")
                || link.contains("X.COM")
                || link.contains("SHOPIFY")) {
//            logger.info("404 Not Found");
            return false;
        }
        return validator.isValid(link);
    }
}




