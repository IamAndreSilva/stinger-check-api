package com.stingercheck.movies;

import java.util.List;

/** A TMDB collection: the films of one series, e.g. "Dune Collection". */
public record TmdbCollection(int id, String name, List<TmdbMovieResult> parts) {}
