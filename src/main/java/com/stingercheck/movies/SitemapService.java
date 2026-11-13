package com.stingercheck.movies;

import com.stingercheck.votes.VoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds sitemap.xml so search engines can find movie pages: every movie that has votes,
 * plus what's in cinemas now (the films people are searching post-credits scenes for).
 */
@Service
public class SitemapService {

    public static final String CACHE_NAME = "sitemap";

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

    @Cacheable(CACHE_NAME)
    public String sitemapXml() {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        STATIC_PATHS.forEach(path -> appendUrl(xml, path));
        moviePaths().values().forEach(path -> appendUrl(xml, path));

        return xml.append("</urlset>\n").toString();
    }

    /** Movie paths keyed by TMDB id, so a movie that is both voted on and now playing is listed once. */
    private Map<Integer, String> moviePaths() {
        Map<Integer, String> paths = new LinkedHashMap<>();

        for (Integer tmdbId : voteRepository.findDistinctMovieIds()) {
            if (paths.size() >= MAX_SITEMAP_MOVIES) {
                return paths;
            }

            TmdbMovieResult movie = findMovie(tmdbId);
            if (movie != null) {
                paths.put(movie.id(), Slugs.moviePath(movie.id(), movie.title()));
            }
        }

        for (TmdbMovieResult movie : tmdbClient.nowPlaying()) {
            if (paths.size() >= MAX_SITEMAP_MOVIES) {
                return paths;
            }

            paths.putIfAbsent(movie.id(), Slugs.moviePath(movie.id(), movie.title()));
        }

        return paths;
    }

    private TmdbMovieResult findMovie(int tmdbId) {
        try {
            return tmdbClient.getMovie(tmdbId);
        } catch (RestClientException e) {
            log.warn("Leaving movie {} out of the sitemap: {}", tmdbId, e.getMessage());
            return null;
        }
    }

    // Paths contain only the site URL, digits, a-z and dashes, so no XML escaping is needed.
    private void appendUrl(StringBuilder xml, String path) {
        xml.append("  <url><loc>").append(siteUrl).append(path).append("</loc></url>\n");
    }
}
