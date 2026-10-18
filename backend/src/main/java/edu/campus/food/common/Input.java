package edu.campus.food.common;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class Input {
    private Input() {}

    public static String text(Map<String, Object> body, String key, int max) {
        Object value = body.get(key);
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.isBlank()) throw bad(key + "不能为空");
        if (result.length() > max) throw bad(key + "过长");
        return result;
    }

    public static String optionalText(Map<String, Object> body, String key, int max) {
        Object value = body.get(key);
        String result = value == null ? "" : String.valueOf(value).trim();
        if (result.length() > max) throw bad(key + "过长");
        return result;
    }

    public static long id(Map<String, Object> body, String key) {
        Object value = body.get(key);
        try {
            long result = Long.parseLong(String.valueOf(value));
            if (result <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw bad(key + "必须是有效编号");
        }
    }

    public static Long optionalId(Map<String, Object> body, String key) {
        if (body.get(key) == null || String.valueOf(body.get(key)).isBlank()) return null;
        return id(body, key);
    }

    public static BigDecimal positive(Map<String, Object> body, String key) {
        try {
            BigDecimal result = new BigDecimal(String.valueOf(body.get(key)));
            if (result.scale() > 3 || result.signum() <= 0) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw bad(key + "必须是大于零、最多三位小数的数字");
        }
    }

    public static LocalDate date(Map<String, Object> body, String key) {
        try { return LocalDate.parse(String.valueOf(body.get(key))); }
        catch (DateTimeParseException ex) { throw bad(key + "日期格式不正确"); }
    }

    public static LocalDate optionalDate(Map<String, Object> body, String key) {
        if (body.get(key) == null || String.valueOf(body.get(key)).isBlank()) return null;
        return date(body, key);
    }

    public static LocalDateTime dateTime(Map<String, Object> body, String key) {
        try { return LocalDateTime.parse(String.valueOf(body.get(key))); }
        catch (DateTimeParseException ex) { throw bad(key + "时间格式不正确"); }
    }

    public static String oneOf(Map<String, Object> body, String key, String... values) {
        String result = text(body, key, 40);
        for (String value : values) if (value.equals(result)) return result;
        throw bad(key + "取值不正确");
    }

    public static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
