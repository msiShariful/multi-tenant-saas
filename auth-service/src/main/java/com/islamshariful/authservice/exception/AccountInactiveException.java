package com.islamshariful.authservice.exception;

import java.io.Serial;
import org.springframework.http.HttpStatus;

/** The credentials were correct but the account or its tenant may not be used. */
public class AccountInactiveException extends ApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    private AccountInactiveException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public static AccountInactiveException disabledUser() {
        return new AccountInactiveException("ACCOUNT_DISABLED", "This account has been disabled");
    }

    public static AccountInactiveException suspendedTenant() {
        return new AccountInactiveException("TENANT_SUSPENDED", "This tenant is suspended");
    }
}
