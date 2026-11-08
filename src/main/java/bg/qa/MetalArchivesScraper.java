package bg.qa;

import bg.qa.model.Release;
import bg.qa.pages.UpcomingAlbumsPage;
import bg.qa.utils.CsvWriter;
import bg.qa.utils.DateInput;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class MetalArchivesScraper {

    private static final DateTimeFormatter SHOW_FORMAT =
            DateTimeFormatter.ofPattern(
                    "EEEE, d MMMM uuuu",
                    Locale.ENGLISH
            );

    /*
     * Metal Archives' type for a studio album. The other types are
     * Single, EP, Demo, Split, Compilation, Live album, Collaboration,
     * Boxed set, Video and Split video.
     */
    private static final String FULL_LENGTH = "Full-length";

    private static final Path OUTPUT_DIRECTORY =
            Path.of("output");

    public static void main(String[] args) {

        Scanner scanner =
                new Scanner(System.in);

        String genreKeyword =
                askGenreKeyword(scanner);

        boolean onlyAlbums =
                askYesNo(
                        scanner,
                        "Only full-length albums, no singles, EPs, live albums, compilations etc.? (y/N): "
                );

        LocalDate startDate =
                askDate(
                        scanner,
                        "Start date",
                        LocalDate.now(),
                        false
                );

        LocalDate endDate =
                askEndDate(
                        scanner,
                        startDate
                );

        WebDriver driver =
                new ChromeDriver();

        try {
            driver.manage()
                    .window()
                    .maximize();

            UpcomingAlbumsPage upcomingAlbumsPage =
                    new UpcomingAlbumsPage(driver);

            upcomingAlbumsPage.open();

            upcomingAlbumsPage.setDateRange(
                    startDate,
                    endDate
            );

            upcomingAlbumsPage.excludeReleasedAlbumVersions();

            List<Release> allReleases =
                    upcomingAlbumsPage.getAllReleases();

            List<Release> matchingReleases =
                    filterByGenre(
                            allReleases,
                            genreKeyword
                    );

            if (onlyAlbums) {
                matchingReleases =
                        filterFullLengthAlbums(
                                matchingReleases
                        );
            }

            Path allReleasesFile =
                    OUTPUT_DIRECTORY.resolve(
                            "all-releases.csv"
                    );

            Path matchingReleasesFile =
                    OUTPUT_DIRECTORY.resolve(
                            createGenreFileName(
                                    genreKeyword,
                                    onlyAlbums
                            )
                    );

            CsvWriter.writeReleases(
                    allReleasesFile,
                    allReleases
            );

            CsvWriter.writeReleases(
                    matchingReleasesFile,
                    matchingReleases
            );

            System.out.println();

            System.out.println(
                    "Scraping complete"
            );

            System.out.println(
                    "Date range: " +
                            startDate +
                            " to " +
                            endDate
            );

            System.out.println(
                    "Total releases: " +
                            allReleases.size()
            );

            System.out.println(
                    "Genre keyword: " +
                            genreKeyword
            );

            System.out.println(
                    "Release types: " +
                            (onlyAlbums ? "full-length albums only" : "all")
            );

            System.out.println(
                    "Matching releases: " +
                            matchingReleases.size()
            );

        } finally {
            driver.quit();
        }
    }

    private static String askGenreKeyword(
            Scanner scanner
    ) {
        while (true) {
            System.out.print(
                    "Genre (e.g. power metal): "
            );

            String keyword =
                    toGenreKeyword(
                            scanner.nextLine()
                    );

            if (!keyword.isEmpty()) {
                System.out.println(
                        "Searching for genres containing: " +
                                keyword
                );

                return keyword;
            }

            System.out.println(
                    "Please type a genre other than just \"metal\"."
            );
        }
    }

    /*
     * "Power Metal" -> "power", "Black/Death metal" -> "black/death".
     * Only the separate word "metal" is removed, so "metalcore" stays.
     */
    static String toGenreKeyword(
            String input
    ) {
        return input
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\bmetal\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static LocalDate askDate(
            Scanner scanner,
            String label,
            LocalDate defaultDate,
            boolean endOfPeriod
    ) {
        while (true) {
            System.out.print(
                    label +
                            " (any format, e.g. 8.11.2026, Nov 8 2026, next month; Enter for " +
                            defaultDate +
                            "): "
            );

            String input =
                    scanner.nextLine()
                            .trim();

            if (input.isEmpty()) {
                return defaultDate;
            }

            try {
                DateInput.Result result =
                        DateInput.parse(
                                input,
                                LocalDate.now(),
                                endOfPeriod
                        );

                System.out.println(
                        "  -> " +
                                result.date().format(SHOW_FORMAT) +
                                (result.note() == null ? "" : " (" + result.note() + ")")
                );

                return result.date();
            } catch (RuntimeException e) {
                System.out.println(
                        "  Could not understand \"" + input + "\": " +
                                e.getMessage() +
                                ". Example: 31.12.2026"
                );
            }
        }
    }

    private static LocalDate askEndDate(
            Scanner scanner,
            LocalDate startDate
    ) {
        while (true) {
            LocalDate endDate =
                    askDate(
                            scanner,
                            "End date",
                            startDate.plusMonths(1),
                            true
                    );

            if (!endDate.isBefore(startDate)) {
                return endDate;
            }

            System.out.println(
                    "End date cannot be before the start date (" +
                            startDate +
                            ")."
            );
        }
    }

    private static List<Release> filterByGenre(
            List<Release> releases,
            String genreKeyword
    ) {
        String normalizedKeyword =
                genreKeyword
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (normalizedKeyword.isEmpty()) {
            throw new IllegalArgumentException(
                    "Genre keyword cannot be empty"
            );
        }

        return releases.stream()
                .filter(release ->
                        release.getGenre() != null
                                &&
                                release.getGenre()
                                        .toLowerCase(
                                                Locale.ROOT
                                        )
                                        .contains(
                                                normalizedKeyword
                                        )
                )
                .toList();
    }

    private static boolean askYesNo(
            Scanner scanner,
            String question
    ) {
        while (true) {
            System.out.print(question);

            String answer =
                    scanner.nextLine()
                            .trim()
                            .toLowerCase(Locale.ROOT);

            switch (answer) {
                case "y", "yes", "д", "да":
                    return true;
                case "", "n", "no", "н", "не":
                    return false;
                default:
                    System.out.println(
                            "  Please answer y or n."
                    );
            }
        }
    }

    private static List<Release> filterFullLengthAlbums(
            List<Release> releases
    ) {
        return releases.stream()
                .filter(release ->
                        FULL_LENGTH.equalsIgnoreCase(
                                release.getType()
                        )
                )
                .toList();
    }

    private static String createGenreFileName(
            String genreKeyword,
            boolean onlyAlbums
    ) {
        String safeGenreName =
                genreKeyword
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9]+",
                                "-"
                        )
                        .replaceAll(
                                "^-+|-+$",
                                ""
                        );

        return safeGenreName +
                (onlyAlbums ? "-albums.csv" : "-releases.csv");
    }
}