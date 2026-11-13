package com.stingercheck.votes;

// Boxed so a missing field (an empty body or the pre-rename "hasPcs") is rejected
// instead of silently defaulting to a "no" vote.
public record VoteRequestDto(Boolean hasStinger) {}
