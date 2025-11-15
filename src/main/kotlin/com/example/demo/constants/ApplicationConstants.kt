package com.example.demo.constants

/**
 * アプリケーション内で使用する共通のパス定数
 */
object ApplicationConstants {
    // ルート（トップページ）パス
    const val ROOT = "/"
    const val HOME = "/home"
    const val REDIRECT = "redirect:"
    const val USERNAME_CHECK = "/userNameCheck"
    const val USER_CHECK = "/userCheck"
    const val CONTENT = "/content"
    const val ALL = "/**"
    const val MAIN = "/main"
    const val LOGIN = "/login"
    const val REGISTER = "/register"

    /**
     * 会員登録に関する定数
     */
    object RegisterConstants {
        // パスワードに使用する文字集合
        const val CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        // 生成パスワード長の既定値
        const val PASSWORD_LENGTH = 15
    }
}

