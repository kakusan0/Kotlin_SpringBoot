package com.example.demo.config

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import jakarta.servlet.http.HttpServletRequest

/**
 * グローバルエラーハンドラー
 * バリデーションエラーやその他の例外を適切に処理します
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    data class ErrorResponse(
        val message: String,
        val errors: Map<String, String>? = null
    )

    /**
     * バリデーションエラー（@Valid）のハンドリング
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.allErrors.associate { error ->
            val fieldName = (error as? FieldError)?.field ?: "unknown"
            val errorMessage = error.defaultMessage ?: "検証エラー"
            fieldName to errorMessage
        }

        logger.warn("バリデーションエラー: {}", errors)

        return ResponseEntity.badRequest().body(
            ErrorResponse(message = "入力値に問題があります", errors = errors)
        )
    }

    /**
     * 制約違反エラーのハンドリング
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val errors = ex.constraintViolations.associate { violation ->
            violation.propertyPath.toString() to (violation.message ?: "制約違反")
        }

        logger.warn("制約違反: {}", errors)

        return ResponseEntity.badRequest().body(
            ErrorResponse(message = "入力値の制約違反があります", errors = errors)
        )
    }

    /**
     * 型変換エラーのハンドリング
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("型変換エラー: parameter={}, value={}", ex.name, ex.value)

        return ResponseEntity.badRequest().body(
            ErrorResponse(message = "パラメータの型が正しくありません: ${ex.name}")
        )
    }

    /**
     * 不正なアクセス試行の検出とログ記録
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.error("SECURITY_ALERT | 不正な引数検出: {}", ex.message)

        return ResponseEntity.badRequest().body(
            ErrorResponse(message = "不正な入力値が検出されました")
        )
    }

    /**
     * その他の例外のハンドリング
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("予期しないエラーが発生: {}", ex.message, ex)

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(message = "サーバー内部エラーが発生しました")
        )
    }

    /**
     * 静的リソース未検出（例: base.jsp など） -> 404
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.debug("NoResourceFound: path={}", request.requestURI)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(message = "リソースが見つかりません: ${request.requestURI}")
        )
    }

    /**
     * ハンドラ未検出（存在しないURL） -> 404
     * spring.mvc.throw-exception-if-no-handler-found=true が有効な場合に到達
     */
    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandler(ex: NoHandlerFoundException): ResponseEntity<ErrorResponse> {
        logger.debug("NoHandlerFound: method={}, path={}", ex.httpMethod, ex.requestURL)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(message = "ページが見つかりません: ${ex.requestURL}")
        )
    }
}
