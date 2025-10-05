package com.example.demo.config

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * グローバルエラーハンドラー
 * バリデーションエラーやその他の例外を適切に処理します
 */
@RestControllerAdvice
class GlobalExceptionHandler {

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

        val response = ErrorResponse(
            message = "入力値に問題があります",
            errors = errors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    /**
     * 制約違反エラーのハンドリング
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val errors = ex.constraintViolations.associate { violation ->
            violation.propertyPath.toString() to (violation.message ?: "制約違反")
        }

        val response = ErrorResponse(
            message = "入力値の制約違反があります",
            errors = errors
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    /**
     * 型変換エラーのハンドリング
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse(
            message = "パラメータの型が正しくありません: ${ex.name}"
        )

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    /**
     * その他の例外のハンドリング
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        // 本番環境では詳細なエラーメッセージを隠すことを推奨
        val response = ErrorResponse(
            message = "サーバーエラーが発生しました"
        )

        // ログには詳細を記録
        ex.printStackTrace()

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}

