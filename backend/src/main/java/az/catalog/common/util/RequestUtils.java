package az.catalog.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestUtils {

    private RequestUtils() {
    }

    /**
     * Client address behind a reverse proxy. Only the left-most entry of
     * {@code X-Forwarded-For} is used, and it is trusted only because Nginx is
     * configured to overwrite (not append to) that header.
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp);
        }
        return truncate(request.getRemoteAddr());
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String agent = request.getHeader("User-Agent");
        return agent == null ? null : (agent.length() > 255 ? agent.substring(0, 255) : agent);
    }

    private static String truncate(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }
}
