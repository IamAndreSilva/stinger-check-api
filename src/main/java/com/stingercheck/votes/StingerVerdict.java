package com.stingercheck.votes;

public enum StingerVerdict {
    YES, NO, UNKNOWN;

    // Also the number of answers a scene count needs before it's shown (VoteService).
    static final long MIN_VOTES = 3;
    private static final double YES_THRESHOLD = 0.6;
    private static final double NO_THRESHOLD = 0.4;

    public static StingerVerdict fromVotes(long yes, long no) {
        long total = yes + no;

        if (total < MIN_VOTES) {
            return UNKNOWN;
        }

        double ratio = (double) yes / total;

        if (ratio >= YES_THRESHOLD) {
            return YES;
        }

        if (ratio <= NO_THRESHOLD) {
            return NO;
        }

        return UNKNOWN;
    }
}
