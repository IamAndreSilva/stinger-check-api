package nl.andresilva.pcschecker.movies;

import nl.andresilva.pcschecker.votes.VoteService;
import nl.andresilva.pcschecker.votes.VoteSummaryDto;
import nl.andresilva.pcschecker.votes.VoteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final TmdbClient tmdbClient;
    private final VoteRepository voteRepository;
    private final VoteService voteService;

    public MovieController(TmdbClient tmdbClient, VoteRepository voteRepository, VoteService voteService) {
        this.tmdbClient = tmdbClient;
        this.voteRepository = voteRepository;
        this.voteService = voteService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<MovieDto>> search(@RequestParam String q) {
        return ResponseEntity.ok(toListDtos(tmdbClient.search(q)));
    }

    @GetMapping("/now-playing")
    public ResponseEntity<List<MovieDto>> nowPlaying() {
        return ResponseEntity.ok(toListDtos(tmdbClient.nowPlaying()));
    }

    @GetMapping("/{tmdbId}")
    public ResponseEntity<MovieDto> getMovie(
            @PathVariable Integer tmdbId,
            @RequestHeader(value = "X-Anonymous-Token", required = false) String anonymousToken,
            @AuthenticationPrincipal OAuth2User oauthUser) {

        TmdbMovieResult movie = tmdbClient.getMovie(tmdbId);
        if (movie == null) return ResponseEntity.notFound().build();

        VoteSummaryDto votes = voteService.getSummary(tmdbId, anonymousToken, oauthUser);
        PcsVerdict verdict = PcsVerdict.fromVotes(votes.yes(), votes.no());

        return ResponseEntity.ok(new MovieDto(
                movie.id(), movie.title(), movie.year(),
                tmdbClient.posterUrl(movie.posterPath()),
                tmdbClient.backdropUrl(movie.backdropPath()),
                movie.overview(),
                movie.runtime(),
                verdict,
                votes
        ));
    }

    private List<MovieDto> toListDtos(List<TmdbMovieResult> results) {
        if (results.isEmpty()) return List.of();

        List<Integer> ids = results.stream().map(TmdbMovieResult::id).toList();
        Map<Integer, long[]> voteCounts = buildVoteCountMap(ids);

        return results.stream().map(r -> {
            long[] counts = voteCounts.getOrDefault(r.id(), new long[]{0, 0});
            return new MovieDto(
                    r.id(), r.title(), r.year(),
                    tmdbClient.posterUrl(r.posterPath()),
                    tmdbClient.backdropUrl(r.backdropPath()),
                    null,
                    null,
                    PcsVerdict.fromVotes(counts[0], counts[1]),
                    null
            );
        }).toList();
    }

    private Map<Integer, long[]> buildVoteCountMap(List<Integer> ids) {
        return voteRepository.countVotesByMovieIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Integer) row[0],
                        row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}
                ));
    }
}
