package bg.qa.model;

import java.util.Objects;

public class Release {

    private final String band;
    private final String album;
    private final String type;
    private final String genre;
    private final String releaseDate;
    private final String addedDate;

    public Release(
            String band,
            String album,
            String type,
            String genre,
            String releaseDate,
            String addedDate
    ) {
        this.band = band;
        this.album = album;
        this.type = type;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.addedDate = addedDate;
    }

    public String getBand() {
        return band;
    }

    public String getAlbum() {
        return album;
    }

    public String getType() {
        return type;
    }

    public String getGenre() {
        return genre;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public String getAddedDate() {
        return addedDate;
    }

    @Override
    public String toString() {
        return "Release{" +
                "band=" + band +
                ", album=" + album +
                ", type=" + type +
                ", genre=" + genre +
                ", releaseDate=" + releaseDate +
                ", addedDate=" + addedDate +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof Release release)) {
            return false;
        }

        return Objects.equals(band, release.band)
                && Objects.equals(album, release.album)
                && Objects.equals(type, release.type)
                && Objects.equals(genre, release.genre)
                && Objects.equals(releaseDate, release.releaseDate)
                && Objects.equals(addedDate, release.addedDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                band,
                album,
                type,
                genre,
                releaseDate,
                addedDate
        );
    }
}