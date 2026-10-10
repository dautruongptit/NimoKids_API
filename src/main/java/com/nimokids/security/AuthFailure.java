package com.nimokids.security;

/** The filter records WHY a token was refused on the request, so the entry point can answer with the precise code. */
public final class AuthFailure {

    public static final String ATTRIBUTE = AuthFailure.class.getName() + ".errorCode";

    private AuthFailure() {
    }
}
