package com.stingercheck.movies;

import com.stingercheck.config.CacheNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class TmdbClient {

    // Without timeouts a hanging TMDB would block request threads until the API stops responding.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    // TMDB keywords "aftercreditsstinger" (179430) or ("|") "duringcreditsstinger" (179431).
    private static final String CREDIT_STINGER_KEYWORDS = "179430|179431";

    private final RestClient restClient;
    private final MissingMovies missingMovies;
    private final String apiKey;
    private final String imageBaseUrl;
    private final String backdropBaseUrl;

    public TmdbClient(
            @Value("${tmdb.api.key}") String apiKey,
            @Value("${tmdb.image.base-url}") String imageBaseUrl,
            @Value("${tmdb.image.backdrop-base-url}") String backdropBaseUrl,
            MissingMovies missingMovies) {
        this.missingMovies = missingMovies;
        this.apiKey = apiKey;
        this.imageBaseUrl = imageBaseUrl;
        this.backdropBaseUrl = backdropBaseUrl;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl("https://api.themoviedb.org/3")
                .requestFactory(requestFactory)
                .build();
    }

    @Cacheable(CacheNames.TMDB_SEARCH)
    public List<TmdbMovieResult> search(String query) {
        TmdbMovieResult.SearchResponse response = restClient.get()
                .uri("/search/movie?query={q}&api_key={k}&language=en-US&page=1", query, apiKey)
                .retrieve()
                .body(TmdbMovieResult.SearchResponse.class);
        return resultsOf(response);
    }

    /** @throws MovieNotFoundException when TMDB doesn't know the id */
    @Cacheable(CacheNames.TMDB_MOVIE)
    public TmdbMovieResult getMovie(int tmdbId) {
        if (missingMovies.isKnownMissing(tmdbId)) {
            throw new MovieNotFoundException(tmdbId);
        }

        TmdbMovieResult movie = restClient.get()
                .uri("/movie/{id}?api_key={k}&language=en-US", tmdbId, apiKey)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> {
                    missingMovies.remember(tmdbId);
                    throw new MovieNotFoundException(tmdbId);
                })
                .body(TmdbMovieResult.class);

        if (movie == null) {
            throw new MovieNotFoundException(tmdbId);
        }

        return movie;
    }

    @Cacheable(CacheNames.TMDB_NOW_PLAYING)
    public List<TmdbMovieResult> nowPlaying() {
        TmdbMovieResult.SearchResponse response = restClient.get()
                .uri("/movie/now_playing?api_key={k}&language=en-US&page=1", apiKey)
                .retrieve()
                .body(TmdbMovieResult.SearchResponse.class);
        return resultsOf(response);
    }

    /** @throws org.springframework.web.client.RestClientException when TMDB fails or doesn't know the id */
    @Cacheable(CacheNames.TMDB_COLLECTION)
    public TmdbCollection getCollection(int collectionId) {
        TmdbCollection collection = restClient.get()
                .uri("/collection/{id}?api_key={k}&language=en-US", collectionId, apiKey)
                .retrieve()
                .body(TmdbCollection.class);

        // The cache rejects null, and callers shouldn't have to check parts.
        if (collection == null) {
            return new TmdbCollection(collectionId, null, List.of());
        }
        if (collection.parts() == null) {
            return new TmdbCollection(collection.id(), collection.name(), List.of());
        }
        return collection;
    }

    /**
     * One page of released films TMDB tags with a during- or after-credits scene, most popular first.
     * Not cached: only the daily known-answers import calls it.
     */
    public TmdbMovieResult.SearchResponse discoverCreditStingers(int page) {
        TmdbMovieResult.SearchResponse response = restClient.get()
                .uri("/discover/movie?with_keywords={keywords}&primary_release_date.lte={today}"
                                + "&include_adult=false&sort_by=popularity.desc&page={page}&api_key={k}",
                        CREDIT_STINGER_KEYWORDS, LocalDate.now(ZoneOffset.UTC), page, apiKey)
                .retrieve()
                .body(TmdbMovieResult.SearchResponse.class);

        if (response == null) {
            return new TmdbMovieResult.SearchResponse(List.of(), 0);
        }
        return response;
    }

    // TMDB can answer without a "results" field; an empty list keeps callers and the cache (which rejects null) safe.
    private static List<TmdbMovieResult> resultsOf(TmdbMovieResult.SearchResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results();
    }

    public String backdropUrl(String backdropPath) {
        if (backdropPath == null) {
            return null;
        }
        return backdropBaseUrl + backdropPath;
    }

    public String posterUrl(String posterPath) {
        if (posterPath == null) {
            return null;
        }
        return imageBaseUrl + posterPath;
    }
}
