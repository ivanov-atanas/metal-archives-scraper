package bg.qa.pages;

import bg.qa.model.Release;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UpcomingAlbumsPage {

    private static final String URL =
            "https://www.metal-archives.com/release/upcoming";

    private static final By NEXT_BUTTON =
            By.xpath(
                    "(//*[self::a or self::button]" +
                            "[normalize-space()='Next' or @aria-label='Next'])[last()]"
            );

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final WebDriverWait tableWait;

    @FindBy(xpath = "//h1[normalize-space()='Upcoming albums']")
    private WebElement pageHeading;

    @FindBy(id = "displayType")
    private WebElement showSelect;

    @FindBy(id = "fromDateDay")
    private WebElement fromDaySelect;

    @FindBy(id = "fromDateMonth")
    private WebElement fromMonthSelect;

    @FindBy(id = "fromDateYear")
    private WebElement fromYearSelect;

    @FindBy(id = "toDateDay")
    private WebElement toDaySelect;

    @FindBy(id = "toDateMonth")
    private WebElement toMonthSelect;

    @FindBy(id = "toDateYear")
    private WebElement toYearSelect;

    @FindBy(id = "chkVersions")
    private WebElement includeReleasedVersionsCheckbox;

    @FindBy(css = "#upcomingAlbumList tbody tr")
    private List<WebElement> releaseRows;

    public UpcomingAlbumsPage(WebDriver driver) {
        this.driver = driver;
        this.wait =
                new WebDriverWait(
                        driver,
                        Duration.ofSeconds(30)
                );

        this.tableWait =
                new WebDriverWait(
                        driver,
                        Duration.ofSeconds(120)
                );

        PageFactory.initElements(
                driver,
                this
        );
    }

    public void open() {
        driver.get(URL);

        wait.until(
                driver -> pageHeading.isDisplayed()
        );

        waitForPageScripts();
    }

    /*
     * The site loads its JavaScript through Cloudflare Rocket Loader.
     * Until it finishes, every onchange handler returns early, so
     * choosing "Range" would not reveal the date dropdowns.
     */
    private void waitForPageScripts() {
        wait.until(driver ->
                (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "return document.readyState === 'complete'" +
                                " && typeof window.jQuery === 'function'" +
                                " && typeof window.refreshTable === 'function'" +
                                " && (window.__cfRLUnblockHandlers === true" +
                                " || !document.querySelector('script[src*=\"rocket-loader\"]'));"
                )
        );
    }

    /*
     * Waits until the DataTables reload has finished: no AJAX request
     * in flight and the "Processing..." indicator hidden. Range queries
     * can take 20+ seconds on the server, hence the long timeout.
     */
    private void waitForTableLoad() {
        tableWait.until(driver ->
                (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "var p = document.getElementById('upcomingAlbumList_processing');" +
                                "return window.jQuery.active === 0" +
                                " && (!p || getComputedStyle(p).visibility === 'hidden');"
                )
        );
    }

    public void setDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Start date cannot be after end date"
            );
        }

        new Select(showSelect)
                .selectByVisibleText("Range");

        wait.until(
                ExpectedConditions.visibilityOf(fromDaySelect)
        );

        /*
         * Every date dropdown has a change handler that reloads the table.
         * Picking the six values one by one fires six slow requests at once,
         * which the server throttles. Set them all, then reload once.
         */
        Boolean allValuesSet = (Boolean) ((JavascriptExecutor) driver).executeScript(
                "var values = arguments[0];" +
                        "for (var id in values) {" +
                        "  $('#' + id).val(values[id]);" +
                        "  if ($('#' + id).val() !== values[id]) { return false; }" +
                        "}" +
                        "refreshTable();" +
                        "return true;",
                Map.of(
                        "fromDateDay", String.format("%02d", startDate.getDayOfMonth()),
                        "fromDateMonth", String.format("%02d", startDate.getMonthValue()),
                        "fromDateYear", String.valueOf(startDate.getYear()),
                        "toDateDay", String.format("%02d", endDate.getDayOfMonth()),
                        "toDateMonth", String.format("%02d", endDate.getMonthValue()),
                        "toDateYear", String.valueOf(endDate.getYear())
                )
        );

        if (!Boolean.TRUE.equals(allValuesSet)) {
            throw new IllegalArgumentException(
                    "The site does not offer the date range " +
                            startDate + " - " + endDate
            );
        }

        waitForTableLoad();
        waitForResults();
    }

    public void excludeReleasedAlbumVersions() {

        if (includeReleasedVersionsCheckbox.isSelected()) {
            includeReleasedVersionsCheckbox.click();
        }

        wait.until(
                ExpectedConditions.not(
                        ExpectedConditions.elementToBeSelected(
                                includeReleasedVersionsCheckbox
                        )
                )
        );

        waitForTableLoad();
        waitForResults();
    }

    public List<Release> getAllReleases() {

        List<Release> allReleases =
                new ArrayList<>();

        int pageNumber = 1;

        while (true) {

            List<Release> currentPageReleases =
                    getCurrentPageReleases();

            allReleases.addAll(
                    currentPageReleases
            );

            System.out.println(
                    "Page " +
                            pageNumber +
                            ": " +
                            currentPageReleases.size() +
                            " releases collected"
            );

            System.out.println(
                    "Total collected: " +
                            allReleases.size()
            );

            if (!hasNextPage()) {
                break;
            }

            goToNextPage();

            pageNumber++;
        }

        return allReleases;
    }

    public List<Release> getCurrentPageReleases() {

        waitForResults();

        List<Release> releases =
                new ArrayList<>();

        for (WebElement row : releaseRows) {

            List<WebElement> cells =
                    row.findElements(
                            By.tagName("td")
                    );

            if (cells.size() < 6) {
                continue;
            }

            String band =
                    cells.get(0)
                            .getText()
                            .trim();

            String album =
                    cells.get(1)
                            .getText()
                            .trim();

            String type =
                    cells.get(2)
                            .getText()
                            .trim();

            String genre =
                    cells.get(3)
                            .getText()
                            .trim();

            String releaseDate =
                    cells.get(4)
                            .getText()
                            .trim();

            String addedDate =
                    cells.get(5)
                            .getText()
                            .trim();

            Release release =
                    new Release(
                            band,
                            album,
                            type,
                            genre,
                            releaseDate,
                            addedDate
                    );

            releases.add(
                    release
            );
        }

        return releases;
    }

    private boolean hasNextPage() {

        List<WebElement> nextButtons =
                driver.findElements(
                        NEXT_BUTTON
                );

        if (nextButtons.isEmpty()) {
            return false;
        }

        WebElement nextButton =
                nextButtons.get(
                        nextButtons.size() - 1
                );

        String classAttribute =
                nextButton
                        .getAttribute("class");

        String ariaDisabled =
                nextButton
                        .getAttribute(
                                "aria-disabled"
                        );

        if (classAttribute != null) {

            String classes =
                    classAttribute
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (classes.contains("disabled")) {
                return false;
            }
        }

        return !"true".equalsIgnoreCase(
                ariaDisabled
        );
    }

    private void goToNextPage() {

        waitForResults();

        WebElement oldFirstRow =
                releaseRows.get(0);

        String oldFirstRowText =
                oldFirstRow
                        .getText()
                        .trim();

        List<WebElement> nextButtons =
                driver.findElements(
                        NEXT_BUTTON
                );

        if (nextButtons.isEmpty()) {
            throw new NoSuchElementException(
                    "Next page button was not found"
            );
        }

        WebElement nextButton =
                nextButtons.get(
                        nextButtons.size() - 1
                );

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        nextButton
                )
        );

        nextButton.click();

        // each page is a separate server request that can take 20+ seconds
        tableWait.until(driver -> {

            try {

                if (releaseRows.isEmpty()) {
                    return false;
                }

                String newFirstRowText =
                        releaseRows
                                .get(0)
                                .getText()
                                .trim();

                return !newFirstRowText.equals(
                        oldFirstRowText
                );

            } catch (
                    StaleElementReferenceException e
            ) {

                return true;
            }
        });

        waitForTableLoad();
        waitForResults();
    }

    private void waitForResults() {

        wait.until(driver -> {

            try {

                if (releaseRows.isEmpty()) {
                    return false;
                }

                List<WebElement> cells =
                        releaseRows
                                .get(0)
                                .findElements(
                                        By.tagName("td")
                                );

                return cells.size() >= 6;

            } catch (
                    StaleElementReferenceException e
            ) {

                return false;
            }
        });
    }
}