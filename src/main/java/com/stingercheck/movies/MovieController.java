package com.stingercheck.movies;

import com.stingercheck.common.BadRequestException;
import com.stingercheck.votes.StingerVerdict;
import com.stingercheck.votes.VoteService;
import com.stingercheck.votes.VoteSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private static final Logger log = LoggerFactory.getLogger(MovieController.class);

    // Each distinct search costs a TMDB call and a cache entry, so the term is capped and normalised.
    static final int MAX_SEARCH_LENGTH = 100;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // Unreleased films without a date go last.
    private static final Comparator<TmdbMovieResult> BY_RELEASE_DATE = Comparator.comparing(
            (TmdbMovieResult m) -> m.releaseDate() == null || m.releaseDate().isBlank() ? "9999" : m.releaseDate());

    private final TmdbClient tmdbClient;
    private final VoteService voteService;

    public MovieController(TmdbClient tmdbClient, VoteService voteService) {
        this.tmdbClient = tmdbClient;
        this.voteService = voteService;
    }

    /** 400 when q is longer than MAX_SEARCH_LENGTH; an empty list without a TMDB call when it's blank. */
    @GetMapping("/search")
    public ResponseEntity<List<MovieDto>> search(@RequestParam String q) {
        String query = normalizeSearch(q);

        if (query.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(toListDtos(tmdbClient.search(query)));
    }

    @GetMapping("/now-playing")
    public ResponseEntity<List<MovieDto>> nowPlaying() {
        return ResponseEntity.ok(toListDtos(tmdbClient.nowPlaying()));
    }

    /** 404 (via MovieNotFoundException) when TMDB doesn't know the id. */
    @GetMapping("/{tmdbId}")
    public ResponseEntity<MovieDto> getMovie(
            @PathVariable Integer tmdbId,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken,
            @AuthenticationPrincipal OAuth2User oauthUser) {

        TmdbMovieResult movie = tmdbClient.getMovie(tmdbId);
        VoteSummaryDto votes = voteService.getSummary(tmdbId, anonymousToken, oauthUser);

        return ResponseEntity.ok(new MovieDto(
                movie.id(), movie.title(), Slugs.of(movie.title()), movie.year(),
                tmdbClient.posterUrl(movie.posterPath()),
                tmdbClient.backdropUrl(movie.backdropPath()),
                movie.overview(),
                movie.runtime(),
                votes.verdict(),
                votes,
                collectionOf(movie)
        ));
    }

    // TMDB search ignores case and extra spaces, so "Dune ", "dune" and "DUNE" share one cache entry.
    private static String normalizeSearch(String q) {
        String query = WHITESPACE.matcher(q.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);

        if (query.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("q must be at most " + MAX_SEARCH_LENGTH + " characters");
        }

        return query;
    }

    // The series is extra: if TMDB fails here, the movie page still works without it.
    private CollectionDto collectionOf(TmdbMovieResult movie) {
        if (movie.belongsToCollection() == null) {
            return null;
        }

        try {
            TmdbCollection collection = tmdbClient.getCollection(movie.belongsToCollection().id());
            if (collection.parts().size() < 2) {
                return null;
            }

            List<TmdbMovieResult> parts = collection.parts().stream().sorted(BY_RELEASE_DATE).toList();
            String name = collection.name() != null ? collection.name() : movie.belongsToCollection().name();
            return new CollectionDto(name, toListDtos(parts));
        } catch (RuntimeException e) {
            log.warn("Leaving out the collection of movie {}: {}", movie.id(), e.getMessage());
            return null;
        }
    }

    private List<MovieDto> toListDtos(List<TmdbMovieResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = results.stream().map(TmdbMovieResult::id).toList();
        Map<Integer, StingerVerdict> verdicts = voteService.verdictsFor(ids);

        return results.stream().map(r -> new MovieDto(
                r.id(), r.title(), Slugs.of(r.title()), r.year(),
                tmdbClient.posterUrl(r.posterPath()),
                tmdbClient.backdropUrl(r.backdropPath()),
                null,
                null,
                verdicts.getOrDefault(r.id(), StingerVerdict.UNKNOWN),
                null,
                null
        )).toList();
    }
}
