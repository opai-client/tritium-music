package tritium.utils;

import lombok.Getter;

/**
 * @author IzumiiKonata
 * Date: 2025/5/10 10:39
 */
@Getter
public class Tuple<A, B> {

    public final A a;
    public final B b;

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public static <X, Y> Tuple<X, Y> of(X x, Y y) {
        return new Tuple<>(x, y);
    }

}
