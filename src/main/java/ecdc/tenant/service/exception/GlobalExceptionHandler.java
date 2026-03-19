package ecdc.tenant.service.exception;

import ecdc.tenant.service.enums.StatusCode;
import ecdc.tenant.service.record.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static ecdc.tenant.service.utility.AppUtils.extractViolationMessage;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Response> handleMethodArgumentNotValidExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((error) -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return Response
                .builder()
                .message(errors.toString())
                .httpStatus(ex.getStatusCode().value())
                .statusCode(StatusCode.CLIENT_ERROR.value)
                .build()
                .toResponseEntity();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Response> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("Business error: {}", ex.getReason());
        return Response
                .builder()
                .message(ex.getReason())
                .httpStatus(ex.getStatusCode().value())
                .statusCode(StatusCode.CLIENT_ERROR.value)
                .build()
                .toResponseEntity();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Response> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data violation error: {}", ex.getMessage(), ex);
        String message = extractViolationMessage(ex);
        return Response
                .builder()
                .message(message)
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .statusCode(StatusCode.CLIENT_ERROR.value)
                .build()
                .toResponseEntity();
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Response> handleAuthorizationException(AuthorizationDeniedException ex) {
        log.error("Data violation error: {}", ex.getMessage(), ex);
        return Response
                .builder()
                .message(ex.getMessage())
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .statusCode(StatusCode.CLIENT_ERROR.value)
                .build()
                .toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleAllExceptions(Exception ex) {
        log.error("System error: {}", ex.getMessage(), ex);
        return Response
                .builder()
                .message(ex.getMessage())
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .statusCode(StatusCode.SERVER_ERROR.value)
                .build()
                .toResponseEntity();
    }
}
