package com.stingercheck.movies;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class TmdbClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String imageBaseUrl;
    private final String backdropBaseUrl;

    public TmdbClient(
            @Value("${tmdb.api.key}") String apiKey,
            @Value("${tmdb.image.base-url}") String imageBaseUrl,
            @Value("${tmdb.image.backdrop-base-url:https://image.tmdb.org/t/p/w1280}") String backdropBaseUrl) {
        this.apiKey = apiKey;
        this.imageBaseUrl = imageBaseUrl;
        this.backdropBaseUrl = backdropBaseUrl;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .build();
    }

    @Cacheable("tmdb-search")
    public List<TmdbMovieResult> search(String query) {
        TmdbMovieResult.SearchResponse response = restClient.get()
                .uri("/search/movie?query={q}&api_key={k}&language=en-US&page=1", query, apiKey)
                .retrieve()
                .body(TmdbMovieResult.SearchResponse.class);
        return response != null ? response.results() : List.of();
    }

    @Cacheable("tmdb-movie")
    public TmdbMovieResult getMovie(int tmdbId) {
        return restClient.get()
                .uri("/movie/{id}?api_key={k}&language=en-US", tmdbId, apiKey)
                .retrieve()
                .body(TmdbMovieResult.class);
    }

    @Cacheable("tmdb-now-playing")
    public List<TmdbMovieResult> nowPlaying() {
        TmdbMovieResult.SearchResponse response = restClient.get()
                .uri("/movie/now_playing?api_key={k}&language=en-US&page=1", apiKey)
                .retrieve()
                .body(TmdbMovieResult.SearchResponse.class);
        return response != null ? response.results() : List.of();
    }

    public String backdropUrl(String backdropPath) {
        if (backdropPath == null) return null;
        return backdropBaseUrl + backdropPath;
    }

    public String posterUrl(String posterPath) {
        if (posterPath == null) return null;
        return imageBaseUrl + posterPath;
    }
}
