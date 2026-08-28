package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** Unknown, expired, revoked or replayed refresh token — all reported identically, for the same reason as login. */
public class InvalidRefreshTokenException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired or already used");
    }
}
