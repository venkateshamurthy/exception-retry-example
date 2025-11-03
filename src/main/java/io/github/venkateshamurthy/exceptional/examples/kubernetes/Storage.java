package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.quantity.NumberQuantity;

import javax.measure.Unit;
import javax.measure.quantity.Dimensionless;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.B;

/**
 * A {@link NumberQuantity} of type {@link Dimensionless} to model storage.
 */
public class Storage extends NumberQuantity<Dimensionless> {
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
            return quantity.to(unit).getValue().longValue();
        }

        /**
         * Builds Storage instance
         * @param value in long corresponding to the unit to be passed in long
         * @return Storage
         */
        public Storage of(final long value) {
            return new Storage(value, unit);
        }
    }

    /**
     * Builds {@link Storage}
     * @param number a value of {@link Number} type
     * @param unit the {@link UNIT} to be used while building for the value
     * @param sc the {@link Scale} being used
     */
    protected Storage(Number number, Unit<Dimensionless> unit, Scale sc) {
        super(number, unit, sc);
    }

    /**
     * Builds {@link Storage}
     * @param number a value of {@link Number} type
     * @param unit the {@link UNIT} to be used while building for the value
     */
    protected Storage(Number number, Unit<Dimensionless> unit) {
        super(number, unit);
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

    /** A ZERO unit for convenience.*/
    public static final Storage ZERO = new Storage(0L, B);
}

