package bg.qa;

import bg.qa.model.Release;
import bg.qa.pages.UpcomingAlbumsPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MetalArchivesScraperTest {

    private static final LocalDate START_DATE =
            LocalDate.of(2025, 11, 1);

    private static final LocalDate END_DATE =
            LocalDate.of(2026, 12, 31);

    private WebDriver driver;
    private UpcomingAlbumsPage upcomingAlbumsPage;

    @BeforeEach
    void setUp() {
        driver = new ChromeDriver();

        driver.manage()
                .window()
                .maximize();

        upcomingAlbumsPage =
                new UpcomingAlbumsPage(driver);
    }

    @Test
    void shouldOpenPageSetFiltersAndReadReleases() {
        upcomingAlbumsPage.open();

        upcomingAlbumsPage.setDateRange(
                START_DATE,
                END_DATE
        );

        upcomingAlbumsPage.excludeReleasedAlbumVersions();

        List<Release> releases =
                upcomingAlbumsPage.getCurrentPageReleases();

        assertFalse(
                releases.isEmpty(),
                "The results table should contain releases"
        );

        for (Release release : releases) {
            assertNotNull(release.getBand());
            assertNotNull(release.getAlbum());
            assertNotNull(release.getType());
            assertNotNull(release.getGenre());
            assertNotNull(release.getReleaseDate());
            assertNotNull(release.getAddedDate());
        }
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}