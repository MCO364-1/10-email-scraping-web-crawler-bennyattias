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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EmailData {
    private int emailId;
    private String email;
    private String source;
    private Timestamp timestamp;


    public EmailData(int emailId, String email, String source, Timestamp timestamp) {
        this.emailId = emailId;
        this.email = email;
        this.source = source;
        this.timestamp = timestamp;
    }

    public int getEmailId() {
        return emailId;
    }

    public String getEmail() {
        return email;
    }

    public String getSource() {
        return source;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }
}






