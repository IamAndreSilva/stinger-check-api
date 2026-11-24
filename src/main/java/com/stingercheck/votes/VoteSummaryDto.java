package com.stingercheck.votes;

/**
 * Tallies for one movie, the caller's own vote (null if none) and the resulting verdict.
 *
 * <p>sceneCount is the number of extra scenes most "yes" voters who answered gave (null when
 * nobody answered or answers are tied; 4 means "4 or more"); userSceneCount is the caller's own.
 *
 * <p>knownAnswer is true when the verdict comes from a known answer (TMDB's tags or the
 * hand-written list) instead of the votes; the votes are still counted and shown.
 */
public record VoteSummaryDto(long yes, long no, long total, Boolean userVote, StingerVerdict verdict,
                             Integer sceneCount, Integer userSceneCount, boolean knownAnswer) {}
