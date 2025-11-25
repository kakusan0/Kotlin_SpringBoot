package com.example.demo.util

import org.slf4j.LoggerFactory

/**
 * DB 呼び出しを共通化するユーティリティ。例外発生時にログを出して再送出する。
 * 使用例: dbCall("selectByUser", userName) { mapper.selectByUser(userName) }
 */
inline fun <T> dbCall(action: String, vararg context: Any?, block: () -> T): T {
    try {
        return block()
    } catch (ex: Exception) {
        val logger = LoggerFactory.getLogger("com.example.demo.util.DbUtils")
        logger.error("DB {} failed; context={}", action, context.joinToString(), ex)
        throw ex
    }
}

