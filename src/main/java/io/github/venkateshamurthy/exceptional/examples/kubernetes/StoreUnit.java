package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.measure.Quantity;
import javax.measure.Unit;

/**
 * An enum representing different Storage Units
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public enum StoreUnit {
    /** Bytes.*/
    B ("Bytes",     Store.BYTE,     1L),

    /** Kilobytes.*/
    KB("Kilobytes", Store.KIBIBYTE, 1024L),

    /** Megabytes.*/
    MB("Megabytes", Store.MEBIBYTE, 1024 * 1024L),

    /** Gigabytes.*/
    GB("Gigabytes", Store.GIBIBYTE, 1024 * 1024 * 1024L),

    /** Terabytes.*/
    TB("Terabytes", Store.TEBIBYTE, 1024 * 1024 * 1024 * 1024L);

    // For Peta, Zeta, hexa etc; the long value is not enough.. you need to change that to BigInteger

    /** A string symbol*/
    final String symbol;

    /** A Unit of store */
    final Unit<Store> unit;

    /** Bytes / unitl*/
    final long bytesPerUnit;

    /**
     * Get the bytes after multiplying by bytes per unit
     * @param value to be evaluated with
     * @return bytes after multiplying by bytes per unit
     */
    public long of  (long value) { return value * bytesPerUnit;}

    /**
     * Number of units (or how many units) evaluated after dividing by bytes per unit
     * @param value to be evaluated
     * @return Number of units
     */
    public long noOfUnits(long value) { return value / bytesPerUnit;}

    /**
     * Check whether current unit is lower than passed by ordinal position
     * @param other unit to be compared
     * @return true if this is lesser than the other
     */
    public boolean isLesser(StoreUnit other)  {return this.compareTo(other) < 0;}

    /**
     * Check whether current unit is greater than passed by ordinal position
     * @param other unit to be compared
     * @return true if this is greater than the other
     */
    public boolean isGreater(StoreUnit other) {return this.compareTo(other) > 0;}

    /**
     * A Quantity to be created based on the current store unit
     * @param value to be converted to the {@link Quantity}&lt;{@link }Store}&gt;
     * @return Quantity of Store
     */
    public Storage toStorage(Number value) { return new Storage(value, this);}

    /**
     * String representation
     * @return the symbol
     */
    public String toString() {return symbol;}

}
