package io.github.venkateshamurthy.enums.examples;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.function.Supplier;

import static java.time.ZonedDateTime.now;

/**
 * {@code CommonRTE} is a unified runtime exception class that can be used across the
 * project to encapsulate error information in a structured and consistent format.
 * <p>
 * This class extends {@link RuntimeException} and adds contextual fields such as:
 * <ul>
 *     <li>{@code code} - Application or domain-specific error code</li>
 *     <li>{@code detailedMessage} - Detailed, formatted explanation of the error</li>
 *     <li>{@code timeStamp} - UTC timestamp when the error was created</li>
 *     <li>{@code httpStatus} - The {@link HttpStatus} representing the error category</li>
 * </ul>
 * <p>
 * The class supports message formatting with templates, placeholder substitution, and structured logging
 * to simplify debugging and monitoring.
 *
 * <p>Example usage:
 * <pre>{@code
 * throw new CommonRTE(FaultCode.CREDENTIAL_MISSING,
 *     "Missing or invalid credential ID: {}", request.getCredentialId())
 *     .setHttpStatus(HttpStatus.BAD_REQUEST)
 *     .logInfo();
 * }</pre>
 *
 * <p>Another example:
 *  * <pre>{@code
 *  * throw FaultCodes.CREDENTIAL_MISSING.toCommonRTE(
 *  *     "Missing or invalid credential ID: {}", request.getCredentialId())
 *  *     .setHttpStatus(HttpStatus.BAD_REQUEST)
 *  *     .logInfo();
 *  * }</pre>
 * @author venkateshamurthy
 * @since 1.0
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@Accessors(chain = true)
@Builder(toBuilder = true)
@Slf4j
public class CommonRTE extends RuntimeException implements Serializable {
    /** A supplier of current instant.*/
    public static final Supplier<ZonedDateTime> NOW = () -> now(ZoneId.of("UTC"));

    /** Error code.*/
    @Schema(description = "Error code")    private String code;

    /** Error details formatted from variety of ways using argument substitution.*/
    @Schema(description = "Error details") private String detailedMessage;

    /** Tme of error occurance.*/
    @Schema(description = "Error time")    private ZonedDateTime timeStamp;

    /** HTTP Status.*/
    @Schema(description = "Http Status")   private HttpStatus httpStatus;

    /**
     * Constructs a new {@code CommonRTE} with the specified message.
     * Automatically sets the timestamp to current UTC time.
     *
     * @param message the exception message
     */
    public CommonRTE(final String message) {
        super(message);
        setTimeStamp(NOW.get());
    }

    /**
     * Constructs a new {@code CommonRTE} with the specified message and cause.
     * Automatically sets the timestamp to current UTC time.
     *
     * @param message the exception message
     * @param cause   the underlying cause of this exception
     */
    public CommonRTE(final String message, Throwable cause) {
        super(message, cause);
        setTimeStamp(NOW.get());
    }

    /**
     * Sets the detailed message by substituting positional placeholders ("{}") in the given template.
     *
     * @param template the message template
     * @param values   values to replace in template
     * @return this instance for fluent chaining
     */
    public CommonRTE detailedMessage(String template, Object... values) {
        return setDetailedMessage(MessageFormatter.basicArrayFormat(template, values));
    }

    /**
     * Sets the detailed message by replacing named placeholders in the form of {@code {key}}.
     * <p>
     * Example:
     * <pre>{@code
     * exception.setDetailedMessage("Error in {module} at {time}", Map.of("module", "Auth", "time", "12:00 UTC"));
     * }</pre>
     *
     * @param template the message template with named placeholders
     * @param values   the key-value pairs for substitution
     * @return this instance for fluent chaining
     */
    public CommonRTE setDetailedMessage(String template, Map<String, Object> values) {
        return setDetailedMessage(StringSubstitutor.replace(template, values, "{", "}"));
    }

    /**
     * Sets the detailed message using a {@link MessageFormat} instance for advanced message formatting.
     *
     * @param template the {@link MessageFormat} instance
     * @param values   the argument values
     * @return this instance for fluent chaining
     */
    public CommonRTE formatDetailedMessage(MessageFormat template, Object... values) {
        return setDetailedMessage(MessageFormat.format(template.toPattern(), values));
    }

    /**
     * Logs this exception’s summary at INFO level, including error code, timestamp, and details.
     *
     * @return this instance for fluent chaining
     */
    public CommonRTE logInfo() {
        log.info("Error:{} | Time:{} | Details:{}", code, timeStamp, detailedMessage);
        return this;
    }

    /**
     * Logs this exception at DEBUG level (though implemented as INFO here for visibility),
     * including message, code, timestamp, details, and HTTP status.
     *
     * @return this instance for fluent chaining
     */
    public CommonRTE logDebug() {
        log.info("Message:{} | Error:{} | Time:{} | Details:{} | Status:{}",
                getMessage(), code, timeStamp, detailedMessage, httpStatus);
        return this;
    }
}