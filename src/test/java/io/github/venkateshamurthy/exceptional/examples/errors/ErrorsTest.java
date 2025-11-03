package io.github.venkateshamurthy.exceptional.examples.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Sets;
import io.github.venkateshamurthy.enums.DynamicEnum;
import io.github.venkateshamurthy.exceptional.exceptions.CommonRuntimeException;
import io.github.venkateshamurthy.exceptional.exceptions.ExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.venkateshamurthy.exceptional.examples.errors.Faults.SERVER_ERR;
import static io.github.venkateshamurthy.exceptional.exceptions.DetailsMessageFormatters.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * <b>Note:</b>This is just an experimental test and bears no significant usage value. this test might as well break if the
 * name and description of the {@link Faults} don't match up with its counterpart in {@link Errors}
 */
@Slf4j
public class ErrorsTest {
    static final String template1 = "Credential Id {} seems {}";
    static final String template2 = "Credential Id {credId} seems {anomaly}";
    static final MessageFormat msgFormat = new MessageFormat("Credential Id {0} seems {1}");
    static final String template3 = msgFormat.toPattern();
    static final String template4 = "Credential Id ${credId} seems ${anomaly}";
    static final String template5 = "Credential Id <<credId>> seems <<anomaly>>";
    static final Object arg1 = UUID.randomUUID(), arg2 = "missing or invalid";
    static final Map<String, Object> map = Map.of("credId", arg1, "anomaly", arg2);
    ZonedDateTime now = ZonedDateTime.now();

    @DisplayName("Testing serialization")
    @Test void testSerDeser() throws JsonProcessingException {
        var mapper = Faults.getDefaultMapper();
        log.info("SERVER Error:{}", mapper.writeValueAsString(SERVER_ERR));
    }

    @ParameterizedTest(name = "Template:{0}")
    @DisplayName("Test with set detailed message using var args. Template:{0}")
    @ValueSource(strings = {template1, template2, "Credential Id {0} seems {1}", template4, template5})
    void testBySetDetailedMessageWithVarArgs(String template) {
        var errEx = Errors.VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat(template, arg1, arg2)).setTimeStamp(now);
        var fltEx = Faults.VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat(template, arg1, arg2)).setTimeStamp(now);
        assertExceptions(errEx, fltEx);
    }

    @ParameterizedTest(name = "Template:{0}")
    @DisplayName("Test with set detailed message using Map of key-value pairs. Template:{0}")
    @ValueSource(strings = { template2, template4, template5})
    void testBySetDetailedMessageWithMap(String template) {
        var errEx = Errors.VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat(template, map)).setTimeStamp(now);
        var fltEx = Faults.VALIDATION_ERR.toCommonRTE().setDetailedMessage(detectAndFormat(template, map)).setTimeStamp(now);
        assertExceptions(errEx, fltEx);
    }

    @ParameterizedTest(name = "Input-1:{0}, Input-2:{1}")
    @DisplayName("Test with SLF4J style details and equivalence of enum:'{0}' and dynamic-enum:'{1}'")
    @ArgumentsSource(testFaults__Args.class)
    void testErrorsAndFaultsWithEmptyBraces(ExceptionCode error, ExceptionCode fault) {
        assertThat(error.getDescription()).isEqualTo(fault.getDescription());
        assertThat(Faults.VALIDATION_ERR.ordinal()).isEqualTo(Errors.VALIDATION_ERR.ordinal());
        var errEx = error.toCommonRTE("Exception Message", template1, arg1, arg2).setTimeStamp(now);
        var fltEx = fault.toCommonRTE("Exception Message", template1, arg1, arg2).setTimeStamp(now);
        assertExceptions(errEx, fltEx);

        errEx = error.toCommonRTE().setDetailedMessage(detectAndFormat(template1, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE().setDetailedMessage(detectAndFormat(template1, arg1, arg2)).setTimeStamp(now);
        assertExceptions(errEx, fltEx);

        Throwable cause = new TimeoutException("Timed out");
        errEx = error.toCommonRTE(cause, "Timed out exception message").setDetailedMessage(SLF4J.format(template1, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE(cause, "Timed out exception message").setDetailedMessage(SLF4J.format(template1, arg1, arg2)).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE(cause, "Timed out exception message", template1, arg1, arg2).setTimeStamp(now);
        fltEx = fault.toCommonRTE(cause, "Timed out exception message", template1, arg1, arg2).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE( "Timed out exception message", template1, arg1, arg2).setTimeStamp(now);
        fltEx = fault.toCommonRTE( "Timed out exception message", template1, arg1, arg2).setTimeStamp(now);
        assertExceptions(fltEx, errEx);
    }

    @ParameterizedTest(name = "Input-1:{0}, Input-2:{1}")
    @DisplayName("Test with JAVA style details and equivalence of enum:'{0}' and dynamic-enum:'{1}'")
    @ArgumentsSource(testFaults__Args.class)
    void testErrorsAndFaultsWithIndexedBraces(ExceptionCode error, ExceptionCode fault) {
        assertThat(Errors.valueOf(error.name()).getStatus()).isEqualTo(Faults.valueOf(fault.name()).getStatus());
        assertThat(error.getDescription()).isEqualTo(fault.getDescription());
        var errEx = error.toCommonRTE("Exception Message",template3, arg1, arg2).setTimeStamp(now);
        var fltEx = fault.toCommonRTE("Exception Message",template3, arg1, arg2).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        Throwable cause = new TimeoutException("Timed out");
        errEx = error.toCommonRTE(cause, "Exception Message",template3, arg1, arg2).setTimeStamp(now);
        fltEx = fault.toCommonRTE(cause, "Exception Message",template3, arg1, arg2).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE().setDetailedMessage(detectAndFormat(template3, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE().setDetailedMessage(detectAndFormat(template3, arg1, arg2)).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE().setDetailedMessage(JAVA.format(template3, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE().setDetailedMessage(JAVA.format(template3, arg1, arg2)).setTimeStamp(now);
        assertExceptions(fltEx, errEx);
    }

    @ParameterizedTest(name = "Input-1:{0}, Input-2:{1}")
    @DisplayName("Test with NAMEDARGS style details and equivalence of enum:'{0}' and dynamic-enum:'{1}'")
    @ArgumentsSource(testFaults__Args.class)
    void testErrorsAndFaultsWithKeyedBraces(ExceptionCode error, ExceptionCode fault) {
        assertThat(error.getDescription()).isEqualTo(fault.getDescription());
        var errEx = error.toCommonRTE("Exception Message",template2, map).setTimeStamp(now);
        var fltEx = fault.toCommonRTE("Exception Message",template2, map).setTimeStamp(now);
        assertExceptions(errEx, fltEx);

        errEx = error.toCommonRTE("Exception Message",template2, arg1, arg2).setTimeStamp(now);
        fltEx = fault.toCommonRTE("Exception Message",template2, arg1, arg2).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE().setDetailedMessage(detectAndFormat(template2, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE().setDetailedMessage(detectAndFormat(template2, arg1, arg2)).setTimeStamp(now);
        assertExceptions(fltEx, errEx);

        errEx = error.toCommonRTE().setDetailedMessage(NAMEDARGS.format(template2, arg1, arg2)).setTimeStamp(now);
        fltEx = fault.toCommonRTE().setDetailedMessage(NAMEDARGS.format(template2, arg1, arg2)).setTimeStamp(now);
        assertExceptions(fltEx, errEx);
    }

    @Test
    void testNewFaults() {
        int size = Errors.values().length;
        var fault = Faults.builder()
                .name("DB_FAULT")
                .description("DB Fault")
                .status(BAD_REQUEST)
                .build();
        assertEquals(size + 1, Faults.values().length);
        assertEquals(Set.of(fault.name()), Sets.difference(
                Arrays.stream(Faults.values()).map(Faults::name).collect(Collectors.toSet()),
                Arrays.stream(Errors.values()).map(Errors::name).collect(Collectors.toSet())));
    }

    private static void assertExceptions(CommonRuntimeException fltEx, CommonRuntimeException errEx) {
        assertThat(fltEx)
                .extracting(CommonRuntimeException::getCode, CommonRuntimeException::getMessage,
                        CommonRuntimeException::getDetailedMessage, CommonRuntimeException::getHttpStatus,
                        CommonRuntimeException::getTimeStamp, CommonRuntimeException::getCause)
                .containsExactly(errEx.getCode(), errEx.getMessage(), errEx.getDetailedMessage(),
                        errEx.getHttpStatus(), errEx.getTimeStamp(), errEx.getCause());
    }

    private static class testFaults__Args implements ArgumentsProvider {
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
            return Arrays.stream(Faults.values())
                    .filter(f-> EnumUtils.isValidEnum(Errors.class, f.name()))
                    .map(f->Arguments.of(Errors.valueOf(f.name()), f));
        }
    }
}
