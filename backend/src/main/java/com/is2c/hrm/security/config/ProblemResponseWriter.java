package com.is2c.hrm.security.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.is2c.hrm.common.web.ApiExceptionHandler;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** Writes problem+json from servlet filters, where MVC exception handling does not apply. */
@Component
public class ProblemResponseWriter {

    private final JsonMapper mapper;

    public ProblemResponseWriter(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiExceptionHandler.body(status, code, detail, List.of()));
    }
}
