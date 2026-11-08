# Metal Archives Scraper

Collects upcoming album releases from
[Encyclopaedia Metallum (Metal Archives)](https://www.metal-archives.com/release/upcoming)
for a date range you choose, filters them by genre and saves the results as CSV files.

It drives a real Chrome browser with Selenium, using the Page Object pattern.

## What it does

1. Asks which genre you are interested in and which dates to look at.
2. Opens the *Upcoming albums* page and sets the date range filter.
3. Excludes new versions of albums that were already released.
4. Goes through every result page and reads each release.
5. Writes two CSV files to `output/`:

| File | Contents |
|---|---|
| `all-releases.csv` | every release in the date range |
| `<genre>-releases.csv` | only releases whose genre contains your keyword, e.g. `power-releases.csv` |

Columns: `Band, Album, Type, Genre, Release date, Added date`. Files are UTF-8, and every value is quoted.

## Requirements

- **Java 26** (JDK)
- **Google Chrome**. You don't need to install ChromeDriver: Selenium Manager downloads a matching driver on the first run.
- Internet access

You don't need to install Maven, because the project includes the Maven Wrapper (`mvnw`).

## Running

```bash
./mvnw compile exec:java
```

On Windows use `mvnw.cmd compile exec:java`. In IntelliJ IDEA, run the `main` method of
`bg.qa.MetalArchivesScraper` and answer the questions in the *Run* window.

### Example session

```
Genre (e.g. power metal): power metal
Searching for genres containing: power
Start date (any format, e.g. 8.11.2026, Nov 8 2026, next month; Enter for 2026-11-08): today
  -> Sunday, 8 November 2026
End date (any format, e.g. 8.11.2026, Nov 8 2026, next month; Enter for 2026-12-08): +2 days
  -> Tuesday, 10 November 2026
Page 1: 11 releases collected
Total collected: 11
CSV created: .../output/all-releases.csv
Rows written: 11
CSV created: .../output/power-releases.csv
```

Each answer is printed back the way it was understood, so you can spot a misread date immediately.

### Genre

Type a genre the way you would say it. The word *metal* is dropped, because nearly every genre
on the site contains it, and the rest is matched as part of the genre name, ignoring case:

| You type | Searches for | Matches, for example |
|---|---|---|
| `power metal` | `power` | Power Metal, Heavy/Power Metal, Symphonic Power Metal |
| `Black/Death Metal` | `black/death` | Black/Death Metal |
| `metalcore` | `metalcore` | Metalcore, Deathcore/Metalcore |

### Dates

Almost any way of writing a date works:

| Kind | Examples |
|---|---|
| Numbers, any separator | `2026-11-08`, `08.11.2026`, `8/11/26`, `2026/11/8`, `8 11 2026` |
| Digits only | `20261108`, `08112026`, `081126` |
| Month names (English or Bulgarian, full or 3+ letters) | `8 November 2026`, `Nov 8th, 2026`, `Sunday 8 Nov 2026`, `8nov2026`, `8 ноември 2026 г.` |
| Parts left out | `Dec 24` and `24.12` use the current year, and `15` means the 15th of this month |
| Whole month or year | `November 2026`, `11/2026`, `2026-11`, `2026` |
| Words | `today`, `tomorrow`, `yesterday`, `днес`, `утре`, `вчера` |
| Relative | `+3 days`, `in 2 weeks`, `1 month ago`, `-1 year`, `3 месеца`, `next month`, `last week` |

How some inputs are read:

- A whole month or year means its **first** day as a start date and its **last** day as an end date.
  So start `November 2026`, end `November 2026` covers the whole month.
- All-number dates are read **day first** when both orders are possible (`01/02/2026` is 1 February).
  The program says so when it happens. If the first number cannot be a month, as in `11/25/2026`, it is read as month/day.
- Two-digit years: `26` is 2026, `99` is 1999.
- Pressing Enter uses the default: today for the start date, one month after the start for the end date.

## How long it takes

The site returns at most 100 releases per page, and its server is slow for date-range queries.
A single page can take around 20 seconds. Figures measured in November 2026:

| Range | Releases | Pages |
|---|---|---|
| 8–10 November 2026 | 11 | 1 |
| Everything from today on | ~440 | 5 |
| 1 November 2025 – 31 December 2026 | ~34,000 | ~340 (1–2 hours) |

Ranges that reach into the past are much bigger, because they contain everything already released.
Progress is printed after every page.

## Running the tests

```bash
./mvnw test -Dtest=DateInputTest -Dsurefire.failIfNoSpecifiedTests=false   # date parser only, offline, about 1 second
./mvnw test                                                                # also opens Chrome and tests the live site
```

## Project structure

```
src/main/java/bg/qa/
├── MetalArchivesScraper.java     entry point: asks for input, runs the scrape, writes CSVs
├── pages/UpcomingAlbumsPage.java page object for the Upcoming albums page
├── model/Release.java            one release (band, album, type, genre, dates)
└── utils/
    ├── CsvWriter.java            writes releases to a CSV file
    └── DateInput.java            turns free-form text into a date
src/test/java/bg/qa/
├── MetalArchivesScraperTest.java end-to-end test against the live site
└── utils/DateInputTest.java      unit tests for the date parser
```

## Notes about the site

Two behaviours of the site affect how the scraper works:

- **Scripts load late.** The site loads its JavaScript through Cloudflare Rocket Loader. Until that
  finishes, the filters do nothing, so the scraper waits for it before touching the page.
- **One reload, not six.** Every date dropdown reloads the table when it changes. Setting the six dropdowns one by
  one would send six slow requests at once, and the server throttles that. The scraper sets all six
  and reloads the table once.

Please be considerate: every page is a request to a community-run site. Prefer shorter date ranges
and don't run many scrapes in parallel.
