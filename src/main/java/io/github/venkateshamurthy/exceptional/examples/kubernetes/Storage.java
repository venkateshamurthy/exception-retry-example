package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.StoreUnit.*;

/**
 * A class to model storage.
 */
@RequiredArgsConstructor @Getter
public class Storage {
    /** the quantity inner modeling storage.*/
    final Quantity<Store> inner;
    final StoreUnit unit;

    /**
     * Constructor
     * @param value the numerical value
     * @param storeUnit the unit of this value
     */
    Storage(@NonNull Number value, @NonNull StoreUnit storeUnit) {
        inner = Quantities.getQuantity(value, storeUnit.unit);
        unit = storeUnit;
    }

    /**
     * Creates Storage Quantity instance  in {@link StoreUnit} passed
     * @param value to be treated as bytes
     * @param storeUnit to which the value needs to be converted
     * @return Storage
     */
    public static Storage of(long value, StoreUnit storeUnit) { return storeUnit.toStorage(value);}

    /**
     * Creates Storage Quantity instance  in bytes
     * @param value to be treated as bytes
     * @return Storage
     */
    public static Storage of(long value) { return B.toStorage(value);}

    /**
     * Creates Storage Quantity instance  in Kilobytes
     * @param value to be treated as Kilobytes
     * @return Storage
     */
    public static Storage kb(long value) { return KB.toStorage(value);}

    /**
     * Creates Storage Quantity instance  in Megabytes
     * @param value to be treated as Megabytes
     * @return Storage
     */
    public static Storage mb(long value) {return MB.toStorage(value);}

    /**
     * Creates Storage Quantity instance  in Gigabytes
     * @param value to be treated as Gigabytes
     * @return Storage
     */
    public static Storage gb(long value) {return GB.toStorage(value);}

    /** {@inheritDoc}. This in specific returns only the Bytes representation of this storage*/
    public String toString() {return inner.getValue() + " " + unit;}

    /**
     * Gets bytes
     * @return bytes represented by this storage
     */
    public long getBytes(){
        return inner.to(B.unit).getValue().longValue();
    }

    /**
     * Check if the current storage is greater than the passed storage
     * @param storage to be checked with
     * @return true if the current value is greater than the passed
     */
    public boolean isGreaterThan(Storage storage) { return getBytes() > storage.getBytes();}

    /**
     * Check if the current storage is greater than or equal to the passed storage
     * @param storage to be checked with
     * @return true if the current value is greater than r equal to the passed
     */
    public boolean isGreaterThanOrEqualTo(Storage storage){
        return getBytes() >= storage.getBytes();
    }

    /**
     * Check if the current storage is equivalent to the passed storage
     * @param storage to be checked with
     * @return true if the current value is equivalent to the passed
     */
    public boolean isEquivalentTo(Storage storage){
        return getBytes() == storage.getBytes();
    }

    /**
     * Convert value to passed in unit
     * @param thatUnit an instance of {@link StoreUnit} such that its bytes per unit
     *                 is lower than the current instance's store unit's bytes per unit value
     * @return Quantity&lt;Store&gt; according to passed in StoreUnit
     */
    public Storage to(StoreUnit thatUnit) {
        return Storage.of(getBytes()/thatUnit.bytesPerUnit, thatUnit);
    }

    /** A ZERO unit for convenience.*/
    public static final Storage ZERO = new Storage(0L, B);
}

