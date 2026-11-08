package bg.qa.utils;

import bg.qa.model.Release;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvWriter {

    private CsvWriter() {
    }

    public static void writeReleases(
            Path outputFile,
            List<Release> releases
    ) {
        try {
            Path parentDirectory = outputFile.getParent();

            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }

            try (
                    BufferedWriter writer =
                            Files.newBufferedWriter(
                                    outputFile,
                                    StandardCharsets.UTF_8
                            )
            ) {
                writer.write(
                        "Band,Album,Type,Genre,Release date,Added date"
                );

                writer.newLine();

                for (Release release : releases) {
                    writer.write(
                            toCsvLine(release)
                    );

                    writer.newLine();
                }
            }

            System.out.println(
                    "CSV created: " +
                            outputFile.toAbsolutePath()
            );

            System.out.println(
                    "Rows written: " +
                            releases.size()
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to write CSV file: " +
                            outputFile.toAbsolutePath(),
                    e
            );
        }
    }

    private static String toCsvLine(
            Release release
    ) {
        return String.join(
                ",",
                escapeCsv(release.getBand()),
                escapeCsv(release.getAlbum()),
                escapeCsv(release.getType()),
                escapeCsv(release.getGenre()),
                escapeCsv(release.getReleaseDate()),
                escapeCsv(release.getAddedDate())
        );
    }

    private static String escapeCsv(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String escapedValue =
                value.replace(
                        "\"",
                        "\"\""
                );

        return "\"" +
                escapedValue +
                "\"";
    }
}