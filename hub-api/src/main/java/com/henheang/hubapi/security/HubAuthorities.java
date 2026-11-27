package com.henheang.hubapi.security;

/** Authorities the Auth V2 pilot chain grants, on top of supabase-auth's USER / MFA. */
public final class HubAuthorities {

    /**
     * The verified Supabase user has a current {@code approved} H-Space admission row - checked on
     * this request, never cached. A valid token alone (e.g. an Orbit-only user of the shared
     * project) does not get it.
     */
    public static final String MEMBER = "HSPACE_MEMBER";

    private HubAuthorities() {}
}
