package com.danielhw99.auth.security;

import java.io.IOException;
import java.util.List;
import com.danielhw99.common.error.ApiError;
import com.danielhw99.common.error.ErrorCode;
import com.danielhw99.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorWriter {
    private final JsonMapper mapper;
    public SecurityErrorWriter(JsonMapper mapper) { this.mapper=mapper; }
    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        String traceId = TraceIdFilter.getOrCreateTraceId(request);
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader(TraceIdFilter.TRACE_HEADER, traceId);
        mapper.writeValue(response.getOutputStream(), new ApiError(code.name(), code.getMessage(), List.of(), traceId));
    }
}
