import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WebScraperTest {

    @org.junit.jupiter.api.Test
    void main() {
    }

    @Test
    void isScrapeAllowed() throws IOException {
        WebScraper scraper = new WebScraper();
        boolean actual = scraper.isScrapeAllowed("touro.edu");

        assertEquals(true, actual);
    }
}