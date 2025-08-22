package io.github.venkateshamurthy.exceptional.examples.kubernetes;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import tech.units.indriya.AbstractUnit;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.NumberQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Unit;
import javax.measure.quantity.Dimensionless;

import static io.github.venkateshamurthy.exceptional.examples.kubernetes.Storage.UNIT.B;

public class Storage extends NumberQuantity<Dimensionless> {

    @RequiredArgsConstructor
    @Getter
    public enum UNIT {
        B(AbstractUnit.ONE.alternate("B"), "Bytes"),
        KB(B.kiloMultiply(),"Kilobytes"), MB(KB.kiloMultiply(),"Megabytes"),
        GB(MB.kiloMultiply(),"Gigabytes"), TB(GB.kiloMultiply(),"Terabytes");

        final Unit<Dimensionless> unit;
        final String display;

        private Unit<Dimensionless> kiloMultiply() {return unit.multiply((long) 1024).asType(Dimensionless.class);}

        public long toLong(final @NonNull ComparableQuantity<Dimensionless> quantity) {
            return quantity.to(unit).getValue().longValue();
        }

        public long toLong(final @NonNull Storage quantity) {
            return quantity.to(unit).getValue().longValue();
        }

        public ComparableQuantity<Dimensionless> toQuantity(final long value) {
            return Quantities.getQuantity(value, unit);
        }

        public Storage of(final long value) {
            return new Storage(value, unit);
        }
    }

    protected Storage(Number number, Unit<Dimensionless> unit, Scale sc) {
        super(number, unit, sc);
    }

    protected Storage(Number number, Unit<Dimensionless> unit) {
        super(number, unit);
    }

    public Storage(long value, UNIT storeUnit) {
        this(value, storeUnit.unit);
    }

    public long getBytes(){
        return B.toLong(this);
    }

    public static final Storage ZERO = new Storage(0L, B);
}

