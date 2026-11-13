package nl.andresilva.pcschecker.movies;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TmdbMovieResult(
        int id,
        String title,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        String overview,
        Integer runtime
) {
    public int year() {
        if (releaseDate == null || releaseDate.length() < 4) return 0;
        try { return Integer.parseInt(releaseDate.substring(0, 4)); }
        catch (NumberFormatException e) { return 0; }
    }

    record SearchResponse(List<TmdbMovieResult> results) {}
}
