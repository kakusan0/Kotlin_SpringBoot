package com.example.demo.constants;

import lombok.experimental.UtilityClass;

/**
 * Common path constants.
 */
@UtilityClass
public class ApplicationConstants {
    public final String ROOT = "/";
    public final String REDIRECT = "redirect:";
    public final String USERNAME_CHECK = "/userNameCheck";
    public final String USER_CHECK = "/userCheck";
    public final String CONTENT = "/content";
    public final String ALL = "/**";
    public final String MAIN = "/main";
    public final String LOGIN = "/login";
    public final String REGISTER = "/register";

    @UtilityClass
    public class RegisterConstants {
        public final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        public final int PASSWORD_LENGTH = 15;
    }
}
