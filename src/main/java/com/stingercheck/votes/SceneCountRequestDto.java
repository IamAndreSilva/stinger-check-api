package com.stingercheck.votes;

/** 1 to 4, where 4 means "4 or more"; null clears an earlier answer. */
public record SceneCountRequestDto(Integer sceneCount) {}
