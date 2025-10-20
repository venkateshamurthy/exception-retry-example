package io.github.venkateshamurthy.enums.examples;

import org.springframework.http.HttpStatus;

import java.text.MessageFormat;
import java.util.Map;

/**
 * FaultCode is a general interface modeling fault;
 */
public interface FaultCode {
    /**
     * name of the fault
     * @return name
     */
    String name();

    /**
     * Gets status
     * @return {@link HttpStatus} of the fault
     */
    HttpStatus getStatus();

    /**
     * Gets description
     * @return description
     */
    String getDescription();

    /**
     * A {@link CommonRTE} generator given
     *
     * @param message for Exception
     * @return {@link CommonRTE}
     */
    default CommonRTE toCommonRTE(String message) {
        return new CommonRTE(message).setCode(name()).setHttpStatus(getStatus());
    }

    /**
     * A {@link CommonRTE} generator setting the message of {@link #getDescription()}
     *
     * @param template for a detailed message that needs variable interpolated based on substitution markers
     * @param args     the parameters to be used to replace the markered template
     * @return {@link CommonRTE}
     */
    default CommonRTE toCommonRTE(String template, Object... args) {
        return toCommonRTE(getDescription()).detailedMessage(template, args);
    }

    /**
     * A {@link CommonRTE} with message set with {@link #getDescription()} with following other parameters.
     *
     * @param template for a detailed message that needs variable interpolated based on substitution markers
     * @param args     the parameters to be used to replace the markered template
     * @return {@link CommonRTE}
     */
    default CommonRTE toCommonRTE(String template, Map<String, Object> args) {
        return toCommonRTE(getDescription()).setDetailedMessage(template, args);
    }

    /**
     * A {@link CommonRTE} generator given
     *
     * @param template a {@link MessageFormat formatted detailed message} that would be processed by  {@code MessageFormat}
     * @param args     the parameters to be used to replace the markers supported by {@link MessageFormat}
     * @return {@link CommonRTE}
     */
    default CommonRTE toCommonRTE(MessageFormat template, Object... args) {
        return toCommonRTE(getDescription()).formatDetailedMessage(template, args);
    }
}
