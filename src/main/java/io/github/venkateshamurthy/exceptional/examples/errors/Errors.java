package io.github.venkateshamurthy.exceptional.examples.errors;

import io.github.venkateshamurthy.exceptional.exceptions.ExceptionCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

/**
 * An example enum for modeling errors that implement {@link ExceptionCode}
 */
@RequiredArgsConstructor
@Getter
public enum Errors implements ExceptionCode {
    /** A catchall unknown.*/
    UNKNOWN(StringUtils.EMPTY, UNPROCESSABLE_ENTITY),
    /** A file locked error.*/
    FILE_LOCKED_ERR("Destination file is already locked. Cannot Lock again", DESTINATION_LOCKED),
    /** File length and checksum error..*/
    FILE_LNCK_ERR("File length/checksum did not match", UNPROCESSABLE_ENTITY),
    /** A generic validation error.*/
    VALIDATION_ERR("Invalid Input", BAD_REQUEST),
    /** A generic internal server/service error.*/
    SERVER_ERR("Internal server error", INTERNAL_SERVER_ERROR);

    private final String description;
    private final HttpStatus status;

}
