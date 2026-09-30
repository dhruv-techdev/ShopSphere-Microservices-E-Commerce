package com.shopsphere.userservice.auth;

/** What a refresh token maps to. familyId groups every rotation of one login session. */
public record RefreshSession(Long userId, String familyId) {
}
