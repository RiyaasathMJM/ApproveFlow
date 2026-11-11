package com.neirah.approveflow.auth;

public record LoginResponse(String token, UserResponse user) {}