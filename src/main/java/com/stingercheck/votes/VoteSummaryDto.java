package com.stingercheck.votes;

/** Tallies for one movie, the caller's own vote (null if none) and the resulting verdict. */
public record VoteSummaryDto(long yes, long no, long total, Boolean userVote, StingerVerdict verdict) {}
