package nl.andresilva.pcschecker.movies;

import nl.andresilva.pcschecker.votes.VoteSummaryDto;

public record MovieDto(
        int tmdbId,
        String title,
        int year,
        String posterUrl,
        String backdropUrl,
        String overview,
        Integer runtime,
        PcsVerdict verdict,
        VoteSummaryDto votes
) {}
