package com.stingercheck.movies;

import java.util.List;

/** The series a movie belongs to, with every film in release order (the movie itself included). */
public record CollectionDto(String name, List<MovieDto> movies) {}
