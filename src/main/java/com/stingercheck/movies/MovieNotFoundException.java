package com.stingercheck.movies;

/** TMDB has no movie with this id; mapped to 404. */
public class MovieNotFoundException extends RuntimeException {
    public MovieNotFoundException(int tmdbId) {
        super("Movie " + tmdbId + " not found");
    }
}
