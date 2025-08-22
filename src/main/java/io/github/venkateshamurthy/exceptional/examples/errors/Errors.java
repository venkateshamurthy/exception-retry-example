package io.github.venkateshamurthy.exceptional.examples.errors;

import io.github.venkateshamurthy.exceptional.exceptions.ExceptionCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@Getter
public enum Errors implements ExceptionCode {
    FILE_LOCKED_ERR("Destination file is already locked. Cannot Lock again", DESTINATION_LOCKED),
    FILE_LNCK_ERR("File length/checksum did not match", UNPROCESSABLE_ENTITY),
    VALIDATION_ERR("Invalid Input", BAD_REQUEST),
    SERVER_ERR("Internal server error", INTERNAL_SERVER_ERROR);
    private final String description;
    private final HttpStatus status;

}
