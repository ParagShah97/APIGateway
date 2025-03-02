package com.parag.CloudGateway.models;

import java.util.Collection;

public class AuthenticationResponse {

    private String userId;
    private String accessToken;
    private String refreshToken;
    private long expiresAt;
    private Collection<String> authorityList;

    public AuthenticationResponse() {
    }

    public AuthenticationResponse(String userId, String accessToken, String refreshToken, long expiresAt, Collection<String> authorityList) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.authorityList = authorityList;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Collection<String> getAuthorityList() {
        return authorityList;
    }

    public void setAuthorityList(Collection<String> authorityList) {
        this.authorityList = authorityList;
    }
}
