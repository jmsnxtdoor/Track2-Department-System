package edu.department.portal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void preservesExpectedStatusAndReasonForApiErrors() {
        var response = handler.handleStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, "Student ID already exists."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        ProblemDetail problem = response.getBody();
        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("Student ID already exists.", problem.getDetail());
    }

    @Test
    void usesStatusTextWhenApiErrorHasNoReason() {
        var response = handler.handleStatus(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("404 NOT_FOUND", response.getBody().getDetail());
    }
}
