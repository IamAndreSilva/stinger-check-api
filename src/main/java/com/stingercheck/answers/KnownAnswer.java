package com.stingercheck.answers;

/** An answer known without votes; sceneCount is null when unknown (4 means "4 or more"). */
public record KnownAnswer(boolean hasStinger, Integer sceneCount) {}
