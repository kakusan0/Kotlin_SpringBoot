package com.example.demo.constants;

/**
 * Common path constants.
 */
public final class ApplicationConstants {
    public static final String ROOT = "/";
    public static final String REDIRECT = "redirect:";
    public static final String USERNAME_CHECK = "/userNameCheck";
    public static final String USER_CHECK = "/userCheck";
    public static final String CONTENT = "/content";
    public static final String ALL = "/**";
    public static final String MAIN = "/main";
    public static final String LOGIN = "/login";
    public static final String REGISTER = "/register";

    private ApplicationConstants() {
    }

    public static final class RegisterConstants {
        public static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        public static final int PASSWORD_LENGTH = 15;

        private RegisterConstants() {
        }
    }
}
