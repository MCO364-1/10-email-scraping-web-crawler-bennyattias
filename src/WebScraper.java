import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.apache.commons.io.IOUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
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
public class WebScraper extends Thread{
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

    public static void main(String[] args) throws MalformedURLException, IOException {
        webScraper = new WebScraper();
        webScraper.start();
    }

    public WebScraper() {

    }

    public void run(){
        agentNames.add("Mozilla/5.0");
        linkSet.add("no link found"); //So that any non-links will return false when trying to be added to the set
        emailSet.add("no email found"); //So that any non-emails will return false when trying to be added to the set
        linkQueue.add("WWW.TOURO.EDU");
        linkSet.add("WWW.TOURO.EDU");

        while (emailSet.size() < 10) {
            String link = linkQueue.poll();
                try {
                    webScraper.sleep(3000);
                    this.doc = Jsoup.connect("https://" + link)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                            .get();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    break;
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }

            try {
                    if (isScrapeAllowed(link)) {
                        Elements html = doc.getElementsByTag("a");
                        scrapeLinks(html);
                        html = doc.getAllElements();
                        scrapeEmails(html);
                        System.out.println(doc.title());
                        System.out.println("linkset:" + linkSet);
                        System.out.println("------------------------");
                        System.out.println("linkqueue:" + linkQueue);
                    } else {
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
//        for (String link : linkQueue) {
//            System.out.println(link);
//            System.out.println("---");
//        }
//        System.out.println("-----------------------------------------");
//        for (String email : emailSet) {
//            System.out.println(email);
//        }
    }

        /**
         * Method to remove the surrounding html tags/attributes from the plain email address
         * and make the email uppercase
         * and add the email to the emailSet
         * @param html, an Elements object
         */

        public static void scrapeEmails(Elements html) {
            String htmlString = html.toString();
            Pattern pattern = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}");
            Matcher matcher = pattern.matcher(htmlString);
            while (matcher.find()) {
                String email = matcher.group().toUpperCase();
                emailSet.add(email);
            }
        }

    /**
     * Method to remove the surrounding html tags/attributes from the plain link without the https:// part
     * and make the link uppercase
     * @param html, an Elements object
     */

    public static void scrapeLinks(Elements html) {
        String htmlString = html.toString();
        Pattern pattern = Pattern.compile("[(www\\.)?a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\+.~#?&//=]*)");
        Matcher matcher = pattern.matcher(htmlString);
        while (matcher.find()) {
            String link = matcher.group().toUpperCase();
            if (linkSet.add(link)){
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
      public boolean isScrapeAllowed(String link) throws IOException {
       String url = "https://" + link + "/robots.txt";
       this.u = new URL(url);
       this.connection = u.openConnection();
       this.content = IOUtils.toByteArray(connection);
       this.rules = parser.parseContent(url, content, "text/plain", agentNames);

       return rules.isAllowed(link);
      }
}






