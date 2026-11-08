package bg.qa.utils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever the user typed into a date.
 *
 * <p>Accepted, among others:
 * <ul>
 *     <li>numbers with any separator: 2026-11-08, 08.11.2026, 8/11/26, 2026/11/8, 8 11 2026</li>
 *     <li>compact: 20261108, 08112026, 081126</li>
 *     <li>month names (English or Bulgarian, full or 3+ letters):
 *         8 November 2026, Nov 8th, 2026, Sunday 8 Nov 2026, 8 ноември 2026 г.</li>
 *     <li>month or year only: November 2026, 11/2026, 2026-11, 2026</li>
 *     <li>words: today, tomorrow, yesterday, днес, утре, вчера</li>
 *     <li>relative: +3 days, in 2 weeks, 1 month ago, -1 year, next month, last week</li>
 * </ul>
 *
 * <p>A month or year on its own means its first day for a start date
 * and its last day for an end date. All-number dates whose first two
 * parts could both be the day are read day-first (08/11 = 8 November).
 */
public final class DateInput {

    public record Result(
            LocalDate date,
            String note
    ) {
    }

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("january", 1), Map.entry("february", 2), Map.entry("march", 3),
            Map.entry("april", 4), Map.entry("may", 5), Map.entry("june", 6),
            Map.entry("july", 7), Map.entry("august", 8), Map.entry("september", 9),
            Map.entry("october", 10), Map.entry("november", 11), Map.entry("december", 12),
            Map.entry("януари", 1), Map.entry("февруари", 2), Map.entry("март", 3),
            Map.entry("април", 4), Map.entry("май", 5), Map.entry("юни", 6),
            Map.entry("юли", 7), Map.entry("август", 8), Map.entry("септември", 9),
            Map.entry("октомври", 10), Map.entry("ноември", 11), Map.entry("декември", 12)
    );

    private static final List<String> IGNORED_WORDS = List.of(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "mon", "tue", "tues", "wed", "thu", "thur", "thurs", "fri", "sat", "sun",
            "понеделник", "вторник", "сряда", "четвъртък", "петък", "събота", "неделя",
            "пон", "вто", "сря", "чет", "пет", "съб", "нед",
            "of", "the", "on", "г", "год", "година"
    );

    private static final Pattern RELATIVE = Pattern.compile(
            "^(?:in\\s+)?([+-]?)\\s*(\\d+)\\s*" +
                    "(d|days?|w|weeks?|m|months?|y|years?|дни|ден|седмици|седмица|месеца|месец|години|година)" +
                    "(\\s+(?:ago|from now|later|назад))?$"
    );

    private static final Pattern NEXT_OR_LAST = Pattern.compile(
            "^(next|last|this)\\s+(week|month|year)$"
    );

    private DateInput() {
    }

    public static Result parse(
            String input,
            LocalDate today,
            boolean endOfPeriod
    ) {
        String text = input.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        if (text.isEmpty()) {
            throw new IllegalArgumentException("Nothing was typed");
        }

        Result special = parseSpecial(text, today, endOfPeriod);

        if (special != null) {
            return special;
        }

        // 8th -> 8, "8 ноември 2026 г." -> "8 ноември 2026 г"
        text = text.replaceAll("(\\d+)(st|nd|rd|th|ти|ви|ри|ми)\\b", "$1");

        List<String> words = new ArrayList<>();
        List<String> numbers = new ArrayList<>();

        for (String token : text.split("[^\\p{L}\\d]+")) {
            if (token.isEmpty()) {
                continue;
            }

            if (token.chars().allMatch(Character::isDigit)) {
                numbers.add(token);
            } else if (token.chars().allMatch(Character::isLetter)) {
                words.add(token);
            } else {
                // e.g. "8nov2026" -> split letters from digits
                for (String part : token.split("(?<=\\d)(?=\\p{L})|(?<=\\p{L})(?=\\d)")) {
                    (Character.isDigit(part.charAt(0)) ? numbers : words).add(part);
                }
            }
        }

        Integer month = null;

        for (String word : words) {
            if (IGNORED_WORDS.contains(word)) {
                continue;
            }

            Integer found = monthFromWord(word);

            if (found == null) {
                throw new IllegalArgumentException("Unknown word \"" + word + "\"");
            }

            if (month != null && !month.equals(found)) {
                throw new IllegalArgumentException("Two different months were typed");
            }

            month = found;
        }

        if (month != null) {
            return withMonthName(month, numbers, today, endOfPeriod);
        }

        return numbersOnly(numbers, today, endOfPeriod);
    }

    private static Result parseSpecial(
            String text,
            LocalDate today,
            boolean endOfPeriod
    ) {
        switch (text) {
            case "today", "now", "днес", "сега":
                return new Result(today, null);
            case "tomorrow", "утре":
                return new Result(today.plusDays(1), null);
            case "yesterday", "вчера":
                return new Result(today.minusDays(1), null);
            default:
                break;
        }

        Matcher relative = RELATIVE.matcher(text);

        if (relative.matches()) {
            long amount = Long.parseLong(relative.group(2));
            boolean past = "-".equals(relative.group(1)) || relative.group(4) != null
                    && relative.group(4).trim().matches("ago|назад");

            if (past) {
                amount = -amount;
            }

            String unit = relative.group(3);

            LocalDate date = switch (unit.charAt(0)) {
                case 'd', 'д' -> today.plusDays(amount);
                case 'w', 'с' -> today.plusWeeks(amount);
                case 'm' -> today.plusMonths(amount);
                case 'y', 'г' -> today.plusYears(amount);
                default -> unit.startsWith("мес") ? today.plusMonths(amount) : today.plusDays(amount);
            };

            return new Result(date, null);
        }

        Matcher nextOrLast = NEXT_OR_LAST.matcher(text);

        if (nextOrLast.matches()) {
            int shift = switch (nextOrLast.group(1)) {
                case "next" -> 1;
                case "last" -> -1;
                default -> 0;
            };

            return switch (nextOrLast.group(2)) {
                case "week" -> {
                    LocalDate monday = today.plusWeeks(shift)
                            .with(java.time.DayOfWeek.MONDAY);
                    yield new Result(endOfPeriod ? monday.plusDays(6) : monday, null);
                }
                case "month" -> monthResult(YearMonth.from(today).plusMonths(shift), endOfPeriod);
                default -> yearResult(today.getYear() + shift, endOfPeriod);
            };
        }

        return null;
    }

    private static Result withMonthName(
            int month,
            List<String> numbers,
            LocalDate today,
            boolean endOfPeriod
    ) {
        Integer day = null;
        Integer year = null;

        for (String number : numbers) {
            int value = Integer.parseInt(number);

            if (number.length() >= 3 || value > 31 || day != null) {
                if (year != null) {
                    throw new IllegalArgumentException("Too many numbers");
                }

                year = toFullYear(number);
            } else {
                day = value;
            }
        }

        if (day == null) {
            return monthResult(
                    YearMonth.of(year != null ? year : today.getYear(), month),
                    endOfPeriod
            );
        }

        return new Result(
                LocalDate.of(year != null ? year : today.getYear(), month, day),
                year == null ? "year not given, using " + today.getYear() : null
        );
    }

    private static Result numbersOnly(
            List<String> numbers,
            LocalDate today,
            boolean endOfPeriod
    ) {
        if (numbers.size() == 1) {
            return compact(numbers.get(0), today, endOfPeriod);
        }

        if (numbers.size() == 2) {
            String first = numbers.get(0);
            String second = numbers.get(1);

            // 2026-11 or 11/2026
            if (first.length() == 4) {
                return monthResult(YearMonth.of(toFullYear(first), Integer.parseInt(second)), endOfPeriod);
            }

            if (second.length() == 4) {
                return monthResult(YearMonth.of(toFullYear(second), Integer.parseInt(first)), endOfPeriod);
            }

            Result dayMonth = dayAndMonth(first, second, today.getYear());

            return new Result(
                    dayMonth.date(),
                    join(dayMonth.note(), "year not given, using " + today.getYear())
            );
        }

        if (numbers.size() == 3) {
            String first = numbers.get(0);

            // 2026-11-08 / 2026.11.8
            if (first.length() == 4) {
                return new Result(
                        LocalDate.of(
                                toFullYear(first),
                                Integer.parseInt(numbers.get(1)),
                                Integer.parseInt(numbers.get(2))
                        ),
                        null
                );
            }

            return dayAndMonth(first, numbers.get(1), toFullYear(numbers.get(2)));
        }

        throw new IllegalArgumentException(
                numbers.isEmpty() ? "No date found" : "Too many numbers"
        );
    }

    private static Result compact(
            String digits,
            LocalDate today,
            boolean endOfPeriod
    ) {
        switch (digits.length()) {
            case 4: // 2026
                return yearResult(Integer.parseInt(digits), endOfPeriod);
            case 6: // 081126 (ddMMyy) or 202611 (yyyyMM)
                if (digits.startsWith("19") || digits.startsWith("20")) {
                    int month = Integer.parseInt(digits.substring(4));

                    if (month >= 1 && month <= 12) {
                        return monthResult(YearMonth.of(Integer.parseInt(digits.substring(0, 4)), month), endOfPeriod);
                    }
                }

                return dayAndMonth(digits.substring(0, 2), digits.substring(2, 4), toFullYear(digits.substring(4)));
            case 8: // 20261108 (yyyyMMdd) or 08112026 (ddMMyyyy)
                if (digits.startsWith("19") || digits.startsWith("20")) {
                    try {
                        return new Result(
                                LocalDate.of(
                                        Integer.parseInt(digits.substring(0, 4)),
                                        Integer.parseInt(digits.substring(4, 6)),
                                        Integer.parseInt(digits.substring(6))
                                ),
                                null
                        );
                    } catch (DateTimeException ignored) {
                        // fall through to ddMMyyyy
                    }
                }

                return dayAndMonth(digits.substring(0, 2), digits.substring(2, 4), Integer.parseInt(digits.substring(4)));
            default:
                if (digits.length() <= 2) {
                    // just a day: that day of the current month
                    return new Result(today.withDayOfMonth(Integer.parseInt(digits)), "month and year not given");
                }

                throw new IllegalArgumentException("Cannot read \"" + digits + "\" as a date");
        }
    }

    /*
     * Day-first unless that is impossible: 08/11 -> 8 Nov, 11/25 -> 25 Nov.
     */
    private static Result dayAndMonth(
            String first,
            String second,
            int year
    ) {
        int a = Integer.parseInt(first);
        int b = Integer.parseInt(second);

        if (a > 12 || b > 12) {
            return a > 12
                    ? new Result(LocalDate.of(year, b, a), null)
                    : new Result(LocalDate.of(year, a, b), "read as month/day");
        }

        return new Result(
                LocalDate.of(year, b, a),
                a == b ? null : "read as day/month; type " + year + "-" + pad(a) + "-" + pad(b) + " if you meant month/day"
        );
    }

    private static Integer monthFromWord(
            String word
    ) {
        if (word.length() < 3) {
            return null;
        }

        Integer match = null;

        for (Map.Entry<String, Integer> month : MONTHS.entrySet()) {
            if (month.getKey().startsWith(word) || word.startsWith(month.getKey())) {
                if (match != null && !match.equals(month.getValue())) {
                    return null;
                }

                match = month.getValue();
            }
        }

        return match;
    }

    private static int toFullYear(
            String digits
    ) {
        int value = Integer.parseInt(digits);

        if (digits.length() > 2) {
            return value;
        }

        // 26 -> 2026, 99 -> 1999 (the site's oldest year is 1976)
        return value < 70 ? 2000 + value : 1900 + value;
    }

    private static Result monthResult(
            YearMonth month,
            boolean endOfPeriod
    ) {
        return new Result(
                endOfPeriod ? month.atEndOfMonth() : month.atDay(1),
                "whole month: using its " + (endOfPeriod ? "last" : "first") + " day"
        );
    }

    private static Result yearResult(
            int year,
            boolean endOfPeriod
    ) {
        return new Result(
                endOfPeriod ? Year.of(year).atMonthDay(java.time.MonthDay.of(12, 31)) : Year.of(year).atDay(1),
                "whole year: using its " + (endOfPeriod ? "last" : "first") + " day"
        );
    }

    private static String pad(
            int value
    ) {
        return String.format("%02d", value);
    }

    private static String join(
            String first,
            String second
    ) {
        return first == null ? second : first + "; " + second;
    }
}
