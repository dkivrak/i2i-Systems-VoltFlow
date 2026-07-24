package com.voltflow.core.auth;

public final class UserContext {
    private static final ThreadLocal<String> CURRENT_USER_EMAIL = new ThreadLocal<>();

    private UserContext() {}

    public static void setCurrentUserEmail(String email) {
        if (email != null) {
            CURRENT_USER_EMAIL.set(email.trim().toLowerCase(java.util.Locale.ROOT));
        } else {
            CURRENT_USER_EMAIL.remove();
        }
    }

    public static String getCurrentUserEmail() {
        return CURRENT_USER_EMAIL.get();
    }

    public static void clear() {
        CURRENT_USER_EMAIL.remove();
    }
}
