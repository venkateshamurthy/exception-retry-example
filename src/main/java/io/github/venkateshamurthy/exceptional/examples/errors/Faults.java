package io.github.venkateshamurthy.exceptional.examples.errors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.venkateshamurthy.enums.DynamicEnum;
import io.github.venkateshamurthy.exceptional.exceptions.ExceptionCode;
import io.swagger.v3.oas.annotations.media.Schema;
import io.vavr.control.Try;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

import static org.springframework.http.HttpStatus.*;

/**
 * Faults is an example {@link DynamicEnum} for experimental purpose that could potentially resolve the serialization issues
 * observed in serializing/de-serializing newer instances (This is an issue with static {@link Enum} which i am to trying address)
 */
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@ToString(callSuper = true, includeFieldNames = false)
@Getter
@JsonTypeName
@Slf4j
public final class Faults extends DynamicEnum<Faults> implements ExceptionCode {
    /** The field name and the {@link #name() should match to see this close to an enum.*/
    public static final Faults UNKNOWN = new Faults("UNKNOWN", StringUtils.EMPTY, UNPROCESSABLE_ENTITY);
    public static final Faults FILE_LOCKED_ERR = new Faults("FILE_LOCKED_ERR",
            "Destination file is already locked. Cannot Lock again", DESTINATION_LOCKED);
    public static final Faults FILE_LNCK_ERR = new Faults("FILE_LNCK_ERR",
            "File length/checksum did not match", UNPROCESSABLE_ENTITY);
    public static final Faults VALIDATION_ERR = new Faults("VALIDATION_ERR", "Invalid Input", BAD_REQUEST);
    public static final Faults SERVER_ERR = new Faults("SERVER_ERR", "Internal server error", INTERNAL_SERVER_ERROR);

    @JsonProperty
    @Schema(description = "A short description of the fault/error", example = "File is already locked")
    private final String description;

    @JsonProperty
    @Schema(description = "A HTTP status such as BAD_REQUEST")
    private final HttpStatus status;

    @JsonProperty
    public String name() {return super.name();}

    @Builder
    private Faults(String name, String description, HttpStatus status) {
        super(name);
        this.description = description;
        this.status = status;
    }

    public static Faults[] values() {
        return DynamicEnum.values(Faults.class, Faults[]::new);
    }

    public static ObjectMapper getDefaultMapper() {
        return DynamicEnum.getDefaultMapper(Faults.class);
    }

    @JsonCreator
    public static Faults valueOf(String name) {
        return Try.of(()->DynamicEnum.valueOf(Faults.class, name)).getOrElse(UNKNOWN);
    }

    public static void main(String[] args) {
        var fault = Faults.builder()
                .name("DB_FAULT")
                .description("DB Fault")
                .status(BAD_REQUEST)
                .build();
        Arrays.stream(Faults.values()).forEach(f->log.info("{}",f));
    }
}
