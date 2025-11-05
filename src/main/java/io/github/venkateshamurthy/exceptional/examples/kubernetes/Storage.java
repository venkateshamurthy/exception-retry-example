package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Unit;
import javax.measure.quantity.Dimensionless;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.B;

/**
 * A class to model storage.
 */
@RequiredArgsConstructor
@Getter
public class Storage {
    /** the quantity inner modeling storage.*/
    final ComparableQuantity<Dimensionless> inner;

    /**
     * Constructor
     * @param value the numerical value
     * @param unit the unit of this value
     */
    Storage(Number value, Unit<Dimensionless> unit) {
        inner = Quantities.getQuantity(value, unit);
    }
    /**
     * Creates Storage Quantity instance  in bytes
     * @param value to be treated as bytes
     * @return Storage
     */
    public static Storage of(long value) {return new Storage(value, UNIT.B);}

    /**
     * Creates Storage Quantity instance  in Kilobytes
     * @param value to be treated as Kilobytes
     * @return Storage
     */
    public static Storage kb(long value) {return new Storage(value, UNIT.KB);}

    /**
     * Creates Storage Quantity instance  in Megabytes
     * @param value to be treated as Megabytes
     * @return Storage
     */
    public static Storage mb(long value) {return new Storage(value, UNIT.MB);}

    /**
     * Creates Storage Quantity instance  in Gigabytes
     * @param value to be treated as Gigabytes
     * @return Storage
     */
    public static Storage gb(long value) {return new Storage(value, UNIT.GB);}

    /** {@inheritDoc}. This in specific returns only the Bytes representation of this storage*/
    public String toString() {
        return B.toDisplayString(this);
    }


    /**
     * An enum of UNITs used in storage
     */
    @RequiredArgsConstructor
    @Getter
    public enum UNIT {
        /** Bytes representation.*/
        B(AbstractUnit.ONE.alternate("B"), "Bytes"),
        /** Kilobytes.*/
        KB(B.kiloMultiply(),"Kilobytes"),
        /** Megabytes.*/
        MB(KB.kiloMultiply(),"Megabytes"),
        /** Gigabytes.*/
        GB(MB.kiloMultiply(),"Gigabytes"),
        /** Terabytes.*/
        TB(GB.kiloMultiply(),"Terabytes");

        final Unit<Dimensionless> unit;
        final String display;

        private Unit<Dimensionless> kiloMultiply() {return unit.multiply((long) 1024).asType(Dimensionless.class);}

        /**
         * returns long quantity that represents this storage according to the unit
         * @param quantity storage quantity whose value to be retrieved according to the unit
         * @return value of this storage.
         */
        public long toLong(final @NonNull Storage quantity) {
            return quantity.inner.to(unit).getValue().longValue();
        }

        /**
         * Builds Storage instance
         * @param value in long corresponding to the unit to be passed in long
         * @return Storage
         */
        public Storage of(final long value) {
            return new Storage(value, unit);
        }

        /**
         * Gets a displayable string for the Storage
         * @param quantity to be displayed
         * @return string representation.
         */
        public String toDisplayString(final @NonNull Storage quantity) {
            return toLong(quantity)+" "+display;
        }
    }


    /**
     * Builds {@link Storage}
     * @param value in long
     * @param storeUnit the {@link UNIT} to be used while building for the value
     */
    public Storage(long value, UNIT storeUnit) {
        this(value, storeUnit.unit);
    }

    /**
     * Gets bytes
     * @return bytes represented by this storage
     */
    public long getBytes(){
        return B.toLong(this);
    }

    /**
     * Check if the current storage is greater than the passed storage
     * @param storage to be checked with
     * @return true if the current value is greater than the passed
     */
    public boolean isGreaterThan(Storage storage){
        return inner.isGreaterThan(storage.inner);
    }

    /**
     * Check if the current storage is greater than or equal to the passed storage
     * @param storage to be checked with
     * @return true if the current value is greater than r equal to the passed
     */
    public boolean isGreaterThanOrEqualTo(Storage storage){
        return inner.isGreaterThanOrEqualTo(storage.inner);
    }

    /**
     * Check if the current storage is equivalent to the passed storage
     * @param storage to be checked with
     * @return true if the current value is equivalent to the passed
     */
    public boolean isEquivalentTo(Storage storage){
        return inner.isEquivalentTo(storage.inner);
    }

    /**
     * convert value to passed in unit
     * @param unit passed
     * @return ComparableQuantity
     */
    public ComparableQuantity<Dimensionless> to(Unit<Dimensionless> unit){
        return inner.to(unit);
    }

    /** A ZERO unit for convenience.*/
    public static final Storage ZERO = new Storage(0L, B);
}

