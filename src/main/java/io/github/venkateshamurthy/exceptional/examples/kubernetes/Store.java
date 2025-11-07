package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import tech.units.indriya.unit.BaseUnit;

import javax.measure.Quantity;
import javax.measure.Unit;

/**
 * An interface modeling Quantity&lt;Store&gt; for storage/memory.
 */
public interface Store extends Quantity<Store> {
    /** Basic Byte.*/
    BaseUnit<Store> BYTE = new BaseUnit<>("B");
    /** Kilobyte.*/
    Unit<Store> KIBIBYTE = BYTE.multiply(1024).asType(Store.class);
    /** Megabyte.*/
    Unit<Store> MEBIBYTE = KIBIBYTE.multiply(1024).asType(Store.class);
    /**Gigabyte.*/
    Unit<Store> GIBIBYTE = MEBIBYTE.multiply(1024).asType(Store.class);
    /**Terabyte.*/
    Unit<Store> TEBIBYTE = GIBIBYTE.multiply(1024).asType(Store.class);
}
