package io.github.venkateshamurthy.enums.examples;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

/**
 * An enum to model issues - just as an example
 */
@RequiredArgsConstructor @Getter
public enum Issues implements FaultCode {
    /** Unknown error. The field name and the {@link #name()} should match to see this close to an enum.*/
    UNKNOWN (StringUtils.EMPTY, UNPROCESSABLE_ENTITY),

    /** File already locked fault with a corresponding {@link HttpStatus#DESTINATION_LOCKED}.*/
    FILE_LOCKED_ERR("Destination file is already locked. Cannot Lock again", DESTINATION_LOCKED),

    /** File checksum error with a corresponding {@link HttpStatus#UNPROCESSABLE_ENTITY}.*/
    FILE_LNCK_ERR("File length/checksum did not match", UNPROCESSABLE_ENTITY),

    /** General validation error  with a corresponding {@link HttpStatus#BAD_REQUEST}.*/
    VALIDATION_ERR("Invalid Input", BAD_REQUEST),

    /** Server error with a corresponding {@link HttpStatus#INTERNAL_SERVER_ERROR}*/
    SERVER_ERR("Internal server error", INTERNAL_SERVER_ERROR);

    /** A short description of the error that aligns/augments the semantics of error name/code.*/
    @JsonProperty private final String description;

    /** A HTTP Status which can be mapped (potemtially).*/
    @JsonProperty private final HttpStatus status;
}
