import java.sql.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Testing implements Runnable {
    private volatile Queue<EmailData> emailDataQueue;
    Logger logger = Logger.getLogger(Testing.class.getName());

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(10);//10threads & 10tasks = 17 emails in 10 minutes; 16threads & 1000tasks = 40emails; 500threads & 1000tasks = 155 emails;
        for (int i = 0; i < 10; i++) {      //1000 & 1000 = SLOW; 400 & 400 = 216; 300 and 300 = 202; 600 & 600 similar to 500;
            executor.submit(new Testing()); //500 - 44s,20s, 40s; 550 - 22s, 29s, 21s; 600 - 60s, 31s,; 575 - 21s, 34s, 21s,
        }
        executor.shutdown();
    }

    public Testing() {
        emailDataQueue = new ConcurrentLinkedQueue<EmailData>();
        EmailData data = new EmailData(new AtomicInteger(2), "a@gmail.com", "gmail.com", Timestamp.valueOf(LocalDateTime.now()));

        for (int i = 0; i < 3; i++) {
            emailDataQueue.add(data);
        }
    }

        public void run () {
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

            synchronized (this) {

                try (Connection connection = DriverManager.getConnection(connectionUrl)) {
                    // Multi-row insert - Much more efficient
                    String sql = "INSERT INTO Emails (EmailID, Email, Source, TimeStamp) VALUES (?, ?, ?, ?)";
                    for (int i = 0; i < 2; i++) {
                        sql = sql + ", (?, ?, ?, ?)";
                    }
                    sql = sql + ";";

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
                    logger.info("Executing thread: " + Thread.currentThread().getName());
                    // And so on...
                    stmt.execute();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }