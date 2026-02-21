package com.example.demo.config;

import com.example.demo.service.TimesheetConflictException;
import com.example.demo.service.TimesheetNotFoundException;
import com.example.demo.service.TimesheetValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * グローバルエラーハンドラー
 * バリデーションエラーやその他の例外を適切に処理します
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * バリデーションエラー（@Valid）のハンドリング
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.toMap(
                        error -> error instanceof FieldError fieldError ? fieldError.getField() : "unknown",
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "検証エラー",
                        (a, b) -> a
                ));

        log.warn("バリデーションエラー: {}", errors);

        return ResponseEntity.badRequest().body(
                new ErrorResponse("入力値に問題があります", errors)
        );
    }

    /**
     * 制約違反エラーのハンドリング
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage() != null ? violation.getMessage() : "制約違反",
                        (a, b) -> a
                ));

        log.warn("制約違反: {}", errors);

        return ResponseEntity.badRequest().body(
                new ErrorResponse("入力値の制約違反があります", errors)
        );
    }

    /**
     * 型変換エラーのハンドリング
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("型変換エラー: parameter={}, value={}", ex.getName(), ex.getValue());

        return ResponseEntity.badRequest().body(
                new ErrorResponse("パラメータの型が正しくありません: " + ex.getName(), null)
        );
    }

    /**
     * 不正なアクセス試行の検出とログ記録
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("SECURITY_ALERT | 不正な引数検出: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                new ErrorResponse("不正な入力値が検出されました", null)
        );
    }

    /**
     * Timesheet系例外のハンドリング
     */
    @ExceptionHandler(TimesheetConflictException.class)
    public ResponseEntity<ErrorResponse> handleTimesheetConflict(TimesheetConflictException ex) {
        log.warn("Timesheet conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage() != null ? ex.getMessage() : "競合", null));
    }

    @ExceptionHandler(TimesheetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTimesheetNotFound(TimesheetNotFoundException ex) {
        log.debug("Timesheet not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage() != null ? ex.getMessage() : "未検出", null));
    }

    @ExceptionHandler(TimesheetValidationException.class)
    public ResponseEntity<ErrorResponse> handleTimesheetValidation(TimesheetValidationException ex) {
        log.debug("Timesheet validation: {}", ex.getMessage());
        Map<String, String> errs = null;
        if (ex.getMessage() != null) {
            errs = java.util.Arrays.stream(ex.getMessage().split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toMap(s -> s, s -> "INVALID", (a, b) -> a));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("入力値に問題があります", errs));
    }

    /**
     * その他の例外のハンドリング
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("予期しないエラーが発生: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("サーバー内部エラーが発生しました", null));
    }

    /**
     * 静的リソース未検出（例: base.jsp など） -> 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        log.debug("NoResourceFound: path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("リソースが見つかりません: " + request.getRequestURI(), null));
    }

    /**
     * ハンドラ未検出（存在しないURL） -> 404
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex) {
        log.debug("NoHandlerFound: method={}, path={}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("ページが見つかりません: " + ex.getRequestURL(), null));
    }

    public record ErrorResponse(String message, Map<String, String> errors) {
    }
}
