package com.stingercheck.movies;

import com.stingercheck.config.CacheNames;
import com.stingercheck.votes.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds sitemap.xml so search engines can find movie pages: every movie that has votes,
 * plus what's in cinemas now (the films people are searching post-credits scenes for).
 */
@Service
public class SitemapService {

    // Far below the sitemap protocol's 50,000 URL limit; each voted movie costs a (cached) TMDB lookup.
    static final int MAX_SITEMAP_MOVIES = 1000;

    private static final List<String> STATIC_PATHS = List.of("/", "/privacy");

    private static final Logger log = LoggerFactory.getLogger(SitemapService.class);

    private final TmdbClient tmdbClient;
    private final VoteRepository voteRepository;
    private final String siteUrl;

    public SitemapService(TmdbClient tmdbClient, VoteRepository voteRepository,
                          @Value("${app.frontend-url}") String siteUrl) {
        this.tmdbClient = tmdbClient;
        this.voteRepository = voteRepository;
        this.siteUrl = siteUrl.replaceAll("/+$", "");
    }

    // sync: concurrent crawlers share one rebuild instead of each calling TMDB.
    @Cacheable(value = CacheNames.SITEMAP, sync = true)
    public String sitemapXml() {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        STATIC_PATHS.forEach(path -> appendUrl(xml, path));
        moviePaths().values().forEach(path -> appendUrl(xml, path));

        return xml.append("</urlset>\n").toString();
    }

    /**
     * Movie paths keyed by TMDB id, so a movie that is both now playing and voted on is listed once.
     * Now playing goes first (one cached call), then the most recently voted movies fill the remaining slots.
     */
    private Map<Integer, String> moviePaths() {
        Map<Integer, String> paths = new LinkedHashMap<>();

        for (TmdbMovieResult movie : nowPlaying()) {
            if (paths.size() >= MAX_SITEMAP_MOVIES) {
                return paths;
            }

            paths.put(movie.id(), Slugs.moviePath(movie.id(), movie.title()));
        }

        for (Integer tmdbId : voteRepository.findRecentlyVotedMovieIds(Limit.of(MAX_SITEMAP_MOVIES))) {
            if (paths.size() >= MAX_SITEMAP_MOVIES) {
                return paths;
            }

            if (paths.containsKey(tmdbId)) {
                continue;
            }

            TmdbMovieResult movie = findMovie(tmdbId);
            if (movie != null) {
                paths.put(movie.id(), Slugs.moviePath(movie.id(), movie.title()));
            }
        }

        return paths;
    }

    // A TMDB outage, rate limit or empty response leaves movies out instead of failing the whole sitemap.
    private List<TmdbMovieResult> nowPlaying() {
        try {
            return tmdbClient.nowPlaying();
        } catch (RuntimeException e) {
            log.warn("Leaving now-playing movies out of the sitemap: {}", e.getMessage());
            return List.of();
        }
    }

    private TmdbMovieResult findMovie(int tmdbId) {
        try {
            return tmdbClient.getMovie(tmdbId);
        } catch (RuntimeException e) {
            log.warn("Leaving movie {} out of the sitemap: {}", tmdbId, e.getMessage());
            return null;
        }
    }

    // Paths contain only digits, a-z and dashes, and the site URL is our own config value, so no XML escaping is needed.
    private void appendUrl(StringBuilder xml, String path) {
        xml.append("  <url><loc>").append(siteUrl).append(path).append("</loc></url>\n");
    }
}
