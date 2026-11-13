package com.stingercheck.movies;

import com.stingercheck.votes.VoteSummaryDto;

public record MovieDto(
        int tmdbId,
        String title,
        int year,
        String posterUrl,
        String backdropUrl,
        String overview,
        Integer runtime,
        StingerVerdict verdict,
        VoteSummaryDto votes
) {}
