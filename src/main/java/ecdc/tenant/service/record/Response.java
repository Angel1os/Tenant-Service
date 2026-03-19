package ecdc.tenant.service.record;

import lombok.Builder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.ZonedDateTime;

@Builder
public record Response(
        String message,
        int httpStatus,
        int statusCode,
        Object data,
        ZonedDateTime dateTime
) {
    public ResponseEntity<Response> toResponseEntity(){
        return new ResponseEntity<>(this, HttpStatus.valueOf(this.httpStatus));
    }
}
