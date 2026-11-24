package com.stingercheck.movies;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TmdbMovieResult(
        int id,
        String title,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        String overview,
        Integer runtime,
        // Only filled by the movie details call, and only for movies in a series.
        @JsonProperty("belongs_to_collection") CollectionRef belongsToCollection
) {
    public TmdbMovieResult(int id, String title, String releaseDate, String posterPath,
                           String backdropPath, String overview, Integer runtime) {
        this(id, title, releaseDate, posterPath, backdropPath, overview, runtime, null);
    }

    public int year() {
        if (releaseDate == null || releaseDate.length() < 4) return 0;
        try { return Integer.parseInt(releaseDate.substring(0, 4)); }
        catch (NumberFormatException e) { return 0; }
    }

    public record CollectionRef(int id, String name) {}

    /** totalPages is only read by discoverCreditStingers. */
    public record SearchResponse(List<TmdbMovieResult> results, @JsonProperty("total_pages") Integer totalPages) {}
}
