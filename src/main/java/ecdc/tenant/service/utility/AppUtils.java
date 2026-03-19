package ecdc.tenant.service.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ecdc.tenant.service.enums.StatusCode;
import ecdc.tenant.service.record.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ecdc.tenant.service.utility.AppConstants.*;

@Slf4j
public class AppUtils {

    public static Response getResponse(String message, HttpStatus status, StatusCode code, Object data) {
        return Response
                .builder()
                .message(message)
                .httpStatus(status.value())
                .statusCode(code.value)
                .data(data)
                .build();
    }

    public static Response getResponse(String message, HttpStatus status, StatusCode code) {
        return Response
                .builder()
                .message(message)
                .httpStatus(status.value())
                .statusCode(code.value)
                .build();
    }

    public static <E,D> Response buildListResponse(List<E> entityList,
                                                   Function<E,D> toDtoMapper) {
        List<D> dtoList = entityList.stream().map(toDtoMapper).toList();
        return getResponse(FETCH_SUCCESS_MESSAGE,HttpStatus.OK,StatusCode.SUCCESS,dtoList);
    }

    public static <E,D> Response buildPageResponse(Page<E> entityPage,
                                                   Function<E,D> toDtoMapper) {
        Page<D> dtoPage = entityPage.map(toDtoMapper);
        return getResponse(FETCH_SUCCESS_MESSAGE, HttpStatus.OK,StatusCode.SUCCESS,dtoPage);

    }


    public static Response handleException(Exception e) {
        if (e instanceof ResponseStatusException rse) {
            log.error("Exception Occurred! StatusCode -> {} and Message -> {} and Cause -> {}",
                    rse.getStatusCode(), rse.getMessage(), rse.getReason());
            return getResponse(rse.getReason(), HttpStatus.INTERNAL_SERVER_ERROR, StatusCode.SERVER_ERROR);
        } else {
            log.error("Exception Occurred! StatusCode -> {} and Cause -> {} and Message -> {}",
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getCause(), e.getMessage());
            return getResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, StatusCode.SERVER_ERROR);

        }

    }

    public static String getRootCauseMessage(Exception ex) {
        return Optional.ofNullable(ex.getCause())
                .map(Throwable::getMessage)
                .orElse(ex.getMessage());
    }

    public static String extractColumnName(String message, String pattern) {
        try {
            return message.substring(message.indexOf(pattern) + pattern.length())
                    .split("\\.")[1]
                    .replace("]", "");
        } catch (Exception e) {
            return "Required field missing";

        }

    }

    public static String extractViolationMessage(DataIntegrityViolationException ex) {
        String rootMsg = ex.getMostSpecificCause().getMessage();
        if (rootMsg.contains("unique constraint")) {
            return "This record already exists";
        } else if (rootMsg.contains("foreign key")) {
            return "Related record not found";
        } else if (rootMsg.contains("not-null")) {
            return "Required field missing";
        }
        return "Invalid data provided";
    }


    public static boolean isNotNullOrEmpty(Object str) {
        try {
            if (str == null) {
                return false;
            }
            if (str instanceof Collection<?>)
                return !((Collection<?>) str).isEmpty();
            if (str instanceof LinkedHashMap<?, ?>)
                return !((LinkedHashMap<?, ?>) str).isEmpty();
            if (str instanceof Map<?, ?>)
                return !((Map<?, ?>) str).isEmpty();
            if (str instanceof Optional<?>)
                return ((Optional<?>) str).isPresent();
            if (str instanceof JsonNode node) {
                return !(node.isNull() || node.isEmpty() || node.isMissingNode());
            }
            if (str instanceof String)
                return !str.toString().trim().equalsIgnoreCase("");
            return true;
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
        }
        return false;
    }

    public static String convertToJson(Object pojo) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(pojo);
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            return null;
        }

    }


    private static UUID parseUuidParam(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: {}", value);
            return null;
        }
    }

    public static Pageable getPageRequest(Map<String,String> params) {
        if (isNotNullOrEmpty(params)) {
            params = new HashMap<>(params);
        }
        Sort sort = Sort.by(Sort.Direction.fromString(
                        params.getOrDefault(PARAM_SORT_DIR,DEFAULT_PAGE_SORT_DIR).trim()),
                params.getOrDefault(PARAM_SORT_BY,DEFAULT_PAGE_SORT_BY).trim());
        Integer pageNo = parseIntegerParam(params.getOrDefault(PARAM_PAGE_NO,DEFAULT_PAGE_NUMBER));
        Integer pageSize = parseIntegerParam(params.getOrDefault(PARAM_PAGE_SIZE,DEFAULT_PAGE_SIZE));
        return PageRequest.of(pageNo - 1, pageSize, sort);
    }


    private static Integer parseIntegerParam(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }

    }

    public static List<String> parseStringParams(Map<String, String> params, String paramName) {
        String paramValue = params != null ? params.getOrDefault(paramName, "") : "";
        List<String> parsedList = new ArrayList<>();
        if (!paramValue.isEmpty()) {
            parsedList = Arrays.asList(paramValue.split(","));
        }
        return parsedList;
    }

    public static List<Long> parseLongParams(Map<String, String> params, String paramName) {
        String paramValue = params != null ? params.getOrDefault(paramName, "") : "";
        List<Long> parsedList = new ArrayList<>();
        if (!paramValue.isEmpty()) {
            String[] split = paramValue.split(",");
            for (String s : split) {
                parsedList.add(Long.parseLong(s));
            }
        }
        return parsedList;
    }


    public static ZonedDateTime parseZoneDateTime(String date) {
        if (date == null || date.equalsIgnoreCase("")) {
            return null;
        }

        /**
         * Calculating if the Date was ZonedDateTime
         */
        try {
            ZonedDateTime parsedZone = ZonedDateTime.parse(date);
            return parsedZone;
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        /**
         * Calculating if the Date was LocalDateTime
         */
        try {
            LocalDateTime parsedDateTime = LocalDateTime.parse(date);
            return parsedDateTime.atZone(ZoneId.systemDefault());

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        /**
         * Calculating if the Date was LocalDate
         */
        try {
            LocalDate parsedLocal = LocalDate.parse(date);
            return parsedLocal.atStartOfDay(ZoneId.systemDefault());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;

    }

    public static ZonedDateTime parseFullZoneDateTime(String dateStr) {
        if (!isNotNullOrEmpty(dateStr)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(dateStr + "T00:00:00Z", DateTimeFormatter.ISO_ZONED_DATE_TIME);
        } catch (Exception e) {
            log.error("Failed to parse date: {}", dateStr, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format: " + dateStr);
        }
    }

    public static String formatDate(Timestamp date) {
        if (!isNotNullOrEmpty(date)) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return date.toLocalDateTime().format(formatter);

    }

    public static void validateDateRange(String startDate, String endDate) {
        if (isNotNullOrEmpty(startDate) && isNotNullOrEmpty(endDate)) {
            ZonedDateTime start = parseZoneDateTime(startDate);
            ZonedDateTime end = parseZoneDateTime(endDate);
            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DATE_VALIDATION);

            }
        }
    }



    public static StatusCode getStatusCode(String strStatus) {
        try {
            int status = Integer.parseInt(strStatus);
            if (status < 0) {
                return StatusCode.SERVER_ERROR;
            }
            return (status == 0) ? StatusCode.SUCCESS : StatusCode.CLIENT_ERROR;
        } catch (Exception e) {
            return StatusCode.CLIENT_ERROR;
        }

    }

    public static HttpStatus getHttpStatusCode(StatusCode statusCode) {
        return switch (statusCode) {
            case SUCCESS -> HttpStatus.OK;
            case CLIENT_ERROR -> HttpStatus.BAD_REQUEST;
            case SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

    }

    public static String createUuid() {
        return UUID.randomUUID().toString();
    }

    public static String generateRandomString(Integer stringLength) {
        final String ALPHANUMERICS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(stringLength);
        for (int i = 0; i < stringLength; i++)
            sb.append(ALPHANUMERICS.charAt(rnd.nextInt(ALPHANUMERICS.length())));
        return sb.toString();
    }


    public static String removeSQLInjection(String input) {
        if(!isNotNullOrEmpty(input)){
            return input;
        }

        String regex = "[=%*'\";]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        String sanitizedInput = matcher.replaceAll("");
        return sanitizedInput.trim();

    }
}
