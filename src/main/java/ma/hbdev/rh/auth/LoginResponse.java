package ma.hbdev.rh.auth;

public record LoginResponse(String token, UserResponse user) {}
