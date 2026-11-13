package com.stingercheck.auth;

import java.util.UUID;

public record UserDto(UUID id, String name, String avatarUrl) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getAvatarUrl());
    }
}
