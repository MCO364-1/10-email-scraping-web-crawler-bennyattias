import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;


public class EmailData {
    private AtomicInteger emailId;
    private String email;
    private String source;
    private Timestamp timestamp;


    public EmailData(AtomicInteger emailId, String email, String source, Timestamp timestamp) {
        this.emailId = emailId;
        this.email = email;
        this.source = source;
        this.timestamp = timestamp;
    }

    public AtomicInteger getEmailId() {
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
