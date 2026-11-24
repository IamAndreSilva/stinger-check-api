package com.stingercheck.answers;

import com.stingercheck.movies.TmdbClient;
import com.stingercheck.movies.TmdbMovieResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Imports TMDB's "has a scene during or after the credits" tags as known answers, so famous
 * films (Iron Man, The Avengers...) answer "Yes" before anyone votes. Runs shortly after
 * start-up and then daily.
 */
@Component
public class KnownAnswerImportJob {

    private static final Logger log = LoggerFactory.getLogger(KnownAnswerImportJob.class);

    // TMDB's discover endpoint serves at most 500 pages.
    static final int MAX_PAGES = 500;

    // About a thousand films are tagged; far fewer means TMDB answered oddly, so the
    // answers we already have are kept instead of being replaced by a partial list.
    static final int MIN_IMPORTED_MOVIES = 100;

    private final TmdbClient tmdbClient;
    private final KnownAnswerRepository knownAnswerRepository;

    public KnownAnswerImportJob(TmdbClient tmdbClient, KnownAnswerRepository knownAnswerRepository) {
        this.tmdbClient = tmdbClient;
        this.knownAnswerRepository = knownAnswerRepository;
    }

    @Scheduled(initialDelayString = "${app.known-answers.import-initial-delay:PT1M}",
            fixedDelayString = "${app.known-answers.import-interval:PT24H}")
    public void importOnSchedule() {
        try {
            importFromTmdb();
        } catch (RuntimeException e) {
            log.warn("Keeping the current known answers, the TMDB import failed: {}", e.getMessage());
        }
    }

    /**
     * @return how many films were imported, or 0 when too few came back and nothing changed
     * @throws RuntimeException when a TMDB page fails; nothing is changed then either
     */
    public int importFromTmdb() {
        Set<Integer> tmdbMovieIds = new LinkedHashSet<>();
        int totalPages = 1;

        for (int page = 1; page <= Math.min(totalPages, MAX_PAGES); page++) {
            TmdbMovieResult.SearchResponse response = tmdbClient.discoverCreditStingers(page);
            if (response == null) {
                break;
            }

            if (page == 1 && response.totalPages() != null) {
                totalPages = response.totalPages();
            }

            List<TmdbMovieResult> results = response.results() == null ? List.of() : response.results();
            results.forEach(movie -> tmdbMovieIds.add(movie.id()));
        }

        if (tmdbMovieIds.size() < MIN_IMPORTED_MOVIES) {
            log.warn("Keeping the current known answers, TMDB listed only {} tagged film(s)", tmdbMovieIds.size());
            return 0;
        }

        knownAnswerRepository.replaceTmdbAnswers(tmdbMovieIds);
        log.info("Imported {} known answers from TMDB", tmdbMovieIds.size());
        return tmdbMovieIds.size();
    }
}
