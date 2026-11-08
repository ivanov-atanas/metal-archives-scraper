package bg.qa.pages;

import bg.qa.model.Release;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UpcomingAlbumsPage {

    private static final String URL =
            "https://www.metal-archives.com/release/upcoming";

    private static final By NEXT_BUTTON =
            By.xpath(
                    "(//*[self::a or self::button]" +
                            "[normalize-space()='Next' or @aria-label='Next'])[last()]"
            );

    private static final String READ_ROWS_SCRIPT =
            "return Array.from(document.querySelectorAll('#upcomingAlbumList tbody tr'))" +
                    ".filter(tr => tr.cells.length >= 6)" +
                    ".map(tr => Array.from(tr.cells).slice(0, 6).map(td => td.innerText.trim()));";

    private static final String TABLE_HAS_RESULTS_SCRIPT =
            "var rows = document.querySelectorAll('#upcomingAlbumList tbody tr');" +
                    "if (rows.length === 0) { return false; }" +
                    "return rows[0].cells.length >= 6" +
                    " || rows[0].querySelector('.dataTables_empty') !== null;";

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final WebDriverWait tableWait;

    private List<DateFilter> monthlyFilters = List.of();
    private DateFilter appliedFilter;

    @FindBy(xpath = "//h1[normalize-space()='Upcoming albums']")
    private WebElement pageHeading;

    @FindBy(id = "displayType")
    private WebElement showSelect;

    @FindBy(id = "fromDateDay")
    private WebElement fromDaySelect;

    @FindBy(id = "chkVersions")
    private WebElement includeReleasedVersionsCheckbox;

    @FindBy(id = "upcomingAlbumList_info")
    private WebElement tableInfo;

    /*
     * The values the site's date dropdowns are set to. An empty day or
     * month is sent to the server as "00", which is how the site stores
     * releases with an unknown day ("November 2026") or month ("2026").
     */
    private record DateFilter(
            String label,
            String fromYear,
            String fromMonth,
            String fromDay,
            String toYear,
            String toMonth,
            String toDay
    ) {
    }

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
                (Boolean) js().executeScript(
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
     * in flight and the "Processing..." indicator hidden.
     */
    private void waitForTableLoad() {
        tableWait.until(driver ->
                (Boolean) js().executeScript(
                        "var p = document.getElementById('upcomingAlbumList_processing');" +
                                "return window.jQuery.active === 0" +
                                " && (!p || getComputedStyle(p).visibility === 'hidden');"
                )
        );
    }

    /**
     * Chooses the date range to scrape.
     *
     * <p>The server answers a query over a long range in about 20 seconds
     * per page, but a one-month query in under a second, and the speed
     * does not depend on which page is asked for. So the range is split
     * into calendar months, which {@link #getAllReleases()} walks through
     * one after another. Together the months cover exactly the same
     * releases as the whole range. Afterwards the table shows the first month.
     */
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

        monthlyFilters =
                splitIntoMonths(
                        startDate,
                        endDate
                );

        applyFilter(
                monthlyFilters.get(0)
        );
    }

    private static List<DateFilter> splitIntoMonths(
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<DateFilter> filters =
                new ArrayList<>();

        YearMonth firstMonth = YearMonth.from(startDate);
        YearMonth lastMonth = YearMonth.from(endDate);

        for (YearMonth month = firstMonth; !month.isAfter(lastMonth); month = month.plusMonths(1)) {

            String year = String.valueOf(month.getYear());
            String fromMonth;
            String fromDay;

            if (month.equals(firstMonth) && startDate.getDayOfMonth() != 1) {
                fromMonth = pad(startDate.getMonthValue());
                fromDay = pad(startDate.getDayOfMonth());
            } else if (month.getMonthValue() == 1) {
                // a range from 1 January (or crossing into January) covers the whole year
                // "2026-00-00": also releases dated only by year, which sort before January
                fromMonth = "";
                fromDay = "";
            } else {
                // "2026-03-00": also releases dated only by month
                fromMonth = pad(month.getMonthValue());
                fromDay = "";
            }

            boolean isLast = month.equals(lastMonth);

            filters.add(
                    new DateFilter(
                            month.toString(),
                            year,
                            fromMonth,
                            fromDay,
                            String.valueOf(isLast ? endDate.getYear() : month.getYear()),
                            pad(isLast ? endDate.getMonthValue() : month.getMonthValue()),
                            isLast ? pad(endDate.getDayOfMonth()) : "31"
                    )
            );
        }

        return filters;
    }

    /*
     * Every date dropdown has a change handler that reloads the table.
     * Picking the six values one by one fires six requests at once,
     * which the server throttles. Set them all, then reload once.
     */
    private void applyFilter(
            DateFilter filter
    ) {
        Boolean allValuesSet = (Boolean) js().executeScript(
                "var values = arguments[0];" +
                        "for (var id in values) {" +
                        "  $('#' + id).val(values[id]);" +
                        "  if ($('#' + id).val() !== values[id]) { return false; }" +
                        "}" +
                        "refreshTable();" +
                        "return true;",
                Map.of(
                        "fromDateYear", filter.fromYear(),
                        "fromDateMonth", filter.fromMonth(),
                        "fromDateDay", filter.fromDay(),
                        "toDateYear", filter.toYear(),
                        "toDateMonth", filter.toMonth(),
                        "toDateDay", filter.toDay()
                )
        );

        if (!Boolean.TRUE.equals(allValuesSet)) {
            throw new IllegalArgumentException(
                    "The site does not offer dates in " +
                            filter.label() +
                            " (its year lists only go from 1976 to the current year)"
            );
        }

        appliedFilter = filter;

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

        if (monthlyFilters.isEmpty()) {
            throw new IllegalStateException(
                    "Call setDateRange before getAllReleases"
            );
        }

        // a set guards against a release showing up twice when the site's data changes mid-run
        Set<Release> allReleases =
                new LinkedHashSet<>();

        for (DateFilter filter : monthlyFilters) {

            if (!filter.equals(appliedFilter)) {
                applyFilter(filter);
            }

            int pageNumber = 1;
            int monthCount = 0;

            while (true) {

                List<Release> currentPageReleases =
                        getCurrentPageReleases();

                allReleases.addAll(
                        currentPageReleases
                );

                monthCount += currentPageReleases.size();

                System.out.println(
                        filter.label() +
                                ", page " +
                                pageNumber +
                                ": " +
                                currentPageReleases.size() +
                                " releases (month: " +
                                monthCount +
                                ", total: " +
                                allReleases.size() +
                                ")"
                );

                if (!hasNextPage()) {
                    break;
                }

                goToNextPage();

                pageNumber++;
            }
        }

        return new ArrayList<>(allReleases);
    }

    /*
     * Reads the whole table in a single browser call. Asking for each
     * cell separately costs 600 round trips per page of 100 rows.
     */
    @SuppressWarnings("unchecked")
    public List<Release> getCurrentPageReleases() {

        waitForResults();

        List<List<String>> rows =
                (List<List<String>>) js().executeScript(
                        READ_ROWS_SCRIPT
                );

        List<Release> releases =
                new ArrayList<>();

        for (List<String> cells : rows) {
            releases.add(
                    new Release(
                            cells.get(0),
                            cells.get(1),
                            cells.get(2),
                            cells.get(3),
                            cells.get(4),
                            cells.get(5)
                    )
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

        if (classAttribute != null
                && classAttribute.toLowerCase(Locale.ROOT).contains("disabled")) {
            return false;
        }

        return !"true".equalsIgnoreCase(
                ariaDisabled
        );
    }

    private void goToNextPage() {

        String oldInfo =
                tableInfo.getText();

        List<WebElement> nextButtons =
                driver.findElements(
                        NEXT_BUTTON
                );

        if (nextButtons.isEmpty()) {
            throw new NoSuchElementException(
                    "Next page button was not found"
            );
        }

        nextButtons.get(
                nextButtons.size() - 1
        ).click();

        // "Showing 1 to 100 of 3,059 entries" -> "Showing 101 to 200 ..."
        tableWait.until(driver ->
                !tableInfo.getText().equals(oldInfo)
        );

        waitForTableLoad();
        waitForResults();
    }

    /*
     * The table has loaded either release rows or DataTables'
     * "No data available" row (a month without releases).
     */
    private void waitForResults() {
        wait.until(driver ->
                (Boolean) js().executeScript(
                        TABLE_HAS_RESULTS_SCRIPT
                )
        );
    }

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    private static String pad(
            int value
    ) {
        return String.format("%02d", value);
    }
}
