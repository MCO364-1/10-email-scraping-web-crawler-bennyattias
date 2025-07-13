import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.sql.*;
import java.time.LocalDateTime;
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
    - implement error/exception handling logic
    - Add rate limiting
    - use regex to extract only the email addresses
3- Create a set for the emails to prevent duplicates
4- Canonize the email addresses to be fully capitalized
    - toUpperCase()
5- Add the canonized emails to the set
6- Create a set for the links to prevent duplicates
7- Create a queue for the links to access them in a BFS-consistent way
8- Add the links on the website to the set
9- If the linkSet.add() method returns true, that link has not yet been added so it should be added to the queue
10- Go through each link in the queue and repeat steps 2-9 (except for creating new sets and queues)
11- Once the emails reach a certain amount, connect to the database and bulk add the emails to the database
12- Steps 2-9 should be looped in a while loop that continues until the emailSet has a size of 10,000

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
    private Document doc = null; //This won't work on multi threading
    public static volatile Logger logger = LoggerFactory.getLogger(WebScraper.class);
    private static volatile AtomicInteger emailCount = new AtomicInteger(0);
    private static volatile Map<String, String> env = Collections.synchronizedMap(new HashMap<>());
    public static volatile Queue<EmailData> emailDataQueue = new ConcurrentLinkedQueue<EmailData>();
    public static final Object lock = new Object();
    public static volatile AtomicInteger emailsStored = new AtomicInteger(0);
    public static volatile boolean isThreadInLock = false;
    private static volatile boolean islinkQueueAlmostEmpty = false;




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


    public void run() {

        while (emailsStored.get() < 10000) {
// Check if we have accumulated enough emails for a bulk insert into the database
            int bulkQuantity = 500;
            if (emailDataQueue.size() >= bulkQuantity && !isThreadInLock) {
                logger.info(Thread.currentThread().getName() + ": entered DB section, outside lock");

                synchronized (lock) {
                    isThreadInLock = true; //when this is true, the other threads won't even try entering the lock
                    logger.info(Thread.currentThread().getName() + ": entered DB section, inside lock");

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
                        String sql = "INSERT INTO Emails (EmailID, Email, Source, TimeStamp) VALUES (?, ?, ?, ?)";
                        for (int i = 0; i < bulkQuantity - 1; i++) {
                            sql = sql + ", (?, ?, ?, ?)";
                        }
                        sql = sql + ";";

                        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                            for (int i = 0; i < bulkQuantity; i++) {
                                EmailData emailData = emailDataQueue.poll();
                                emailsStored.incrementAndGet();
                                int index = i * 4 + 1;
                                stmt.setString(index, String.valueOf(emailData.getEmailId()));
                                stmt.setString(index + 1, String.valueOf(emailData.getEmail()));
                                stmt.setString(index + 2, String.valueOf(emailData.getSource()));
                                stmt.setString(index + 3, String.valueOf(emailData.getTimestamp()));
                            }
                            stmt.execute();
                            logger.info("Executing thread: " + Thread.currentThread().getName() + " Stored into DB");
                        }

                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                } isThreadInLock = false; //once thread leaves synchronized section, so that other threads can enter when needed
            }

            String link;
                if (linkQueue.size() <= 300) {
                    islinkQueueAlmostEmpty = true;
                } else if (linkQueue.size() >= 700) {
                    islinkQueueAlmostEmpty = false;
                }

                //To avoid getting an exception from the link being null when inserted into jsoup.connect()
            while (true) {
                link = linkQueue.poll();
                if (link != null) {
                    break;
                }
//                try {
//                    //To have the scraper take a one-second pause in between requests
//                    TimeUnit.SECONDS.sleep(1);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
            }

            try {
                Thread.sleep(3000);
                this.doc = Jsoup.connect(link)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.166 Safari/537.36")
                        .get();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                continue;
            } catch (UnknownHostException e) {
                e.printStackTrace();
                continue;
            } catch (HttpStatusException e) {
                e.printStackTrace();
                continue;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                continue;
            }
            Elements html = doc.getElementsByTag("a");
            //Will only scrape new links if the linkqueue contains under 300 links and continues scraping until it surpasses 700 links
            if (islinkQueueAlmostEmpty) {
                try {
                    scrapeLinks(html);
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
            }

            html = doc.getAllElements();
            scrapeEmails(html, link);
            System.out.println("linkset size: " + linkSet.size() + ", linkqueue size: " + linkQueue.size());
        }
    }


    /**
     * Method to remove the surrounding html tags/attributes from the plain email address
     * and make the email uppercase
     * and add the email to the emailSet
     * @param html, an Elements object
     */

    public void scrapeEmails(Elements html, String link) {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9\\.-_]+\\.[A-Z]{2,10}"); //TODO get a better regex from very good developers on stackoverflow
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String email = matcher.group().toUpperCase();
            if(email.endsWith(".PNG") || email.endsWith(".JPG") || email.endsWith(".JPEG")
                    || email.endsWith(".GIF") || email.endsWith(".PDF") || email.endsWith(".WEBP")
                    || email.contains("SENTRY") || email.endsWith(".SVG") || email.endsWith(".WEBPACK")
                    || email.endsWith(".CSS") || email.endsWith(".JS") || email.endsWith(".HTML")
                    || email.endsWith(".MP") || email.endsWith(".WEBM") || email.endsWith(".XLSX")
                    || email.endsWith(".XLS")) {
                continue;
            }
            if (emailSet.add(email)){
                int currentEmailId = emailCount.incrementAndGet();
                logger.info("emailID: " + currentEmailId + " email: " + email + " from: " + link);
                emailDataQueue.add(new EmailData(currentEmailId, email, link, Timestamp.valueOf(LocalDateTime.now())));
            }
        }
    }

    /**
     * Method to remove the surrounding html tags/attributes from the plain link without the https:// part
     * and make the link uppercase
     * @param html, an Elements object
     */
    //   https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()!@:%_\+.~#?&\/\/=]*)

    public void scrapeLinks(Elements html) throws IOException {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("(?i)(?!.*(\\.png|\\.jpg|\\.gif|\\.pdf|\\.webp|twitter|linkedin|vimeo|x\\.com|facebook|\\.gov).*)https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()!@:%_\\+.~#?&\\/\\/=]*)");
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
        if (!isValidURL(link)){
            return false;
        }
        if (!robotsTxtSet.add(url)){
            return robotsTxtMap.get(url);
        }

        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            robotsTxtMap.put(url, false);
            return false;
        }
        try {
            connection = u.openConnection();
        } catch (IOException e) {
            robotsTxtMap.put(url, false);
            return false;
        }
        try {
            content = IOUtils.toByteArray(connection);
        } catch (IOException e) {
            robotsTxtMap.put(url, false);
            return false;
        }
        rules = parser.parseContent(url, content, "text/plain", agentNames);
        boolean isAllowed = rules.isAllowed(url);
        robotsTxtMap.put(url, isAllowed);
        return isAllowed;
    }

    public boolean isValidURL(String link) {
        UrlValidator validator = new UrlValidator(UrlValidator.ALLOW_2_SLASHES + UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.NO_FRAGMENTS + UrlValidator.ALLOW_LOCAL_URLS);
        if (doc.title().contains("404")
                || doc.title().contains("Not Found")
                || link.contains("TWITTER")
                || link.contains("X.COM")
                || link.contains("SHOPIFY")) {
            return false;
        }
        return validator.isValid(link);
    }
}





