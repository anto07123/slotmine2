package dev.slotmine.core;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Números grandes + escala larga española.
 *   0 -> 999k -> 1M (millón, 10^6) -> 1B (billón, 10^12) -> 1T (trillón, 10^18) -> 1Qa (10^24)
 *   -> 1Qi -> 1Sx -> 1Sp -> 1Oc -> 1No -> 1Dc (decillón, 10^60) -> ... -> 1Vg (vigintillón, 10^120).
 *   Tope: 999.999Vg.
 * El sufijo solo cambia al superar 999.999 (mantisa de hasta 6 dígitos = 6 rodillos).
 */
public final class BigNum {
    public static final String[] SUFFIX = {"", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc",
            "UD", "DD", "TD", "QaD", "QiD", "SxD", "SpD", "OcD", "NoD", "Vg"};
    public static final int MAX_TIER = SUFFIX.length - 1;
    private static final BigInteger MILLION = BigInteger.valueOf(1_000_000L);
    public static final BigInteger MAX = BigInteger.valueOf(999_999L).multiply(BigInteger.TEN.pow(6 * MAX_TIER));

    private BigNum() {}

    /** mantissa 0..999999 en unidades de 10^(6*tier). remainder/unit sirven para decimales. */
    public record Scaled(int mantissa, int tier, BigInteger remainder, BigInteger unit) {
        public String suffix() { return SUFFIX[tier]; }
    }

    public static BigInteger clamp(BigInteger v) {
        if (v == null || v.signum() < 0) return BigInteger.ZERO;
        return v.compareTo(MAX) > 0 ? MAX : v;
    }

    public static Scaled scale(BigInteger raw) {
        BigInteger v = clamp(raw);
        int tier = 0;
        BigInteger unit = BigInteger.ONE;
        while (tier < MAX_TIER && v.compareTo(unit.multiply(MILLION)) >= 0) {
            unit = unit.multiply(MILLION);
            tier++;
        }
        BigInteger[] qr = v.divideAndRemainder(unit);
        return new Scaled(qr[0].intValue(), tier, qr[1], unit);
    }

    /** Formato exacto de la especificación: 999, 999k, 1M, 999.999M, 1B, ... */
    public static String format(BigInteger v) {
        Scaled s = scale(v);
        int m = s.mantissa();
        if (s.tier() == 0) return m < 1000 ? Integer.toString(m) : (m / 1000) + "k";
        return dotted(m) + SUFFIX[s.tier()];
    }

    /** Igual que format pero con hasta 2 decimales (coma española) para precios y textos: 1,25M · 12,5k */
    public static String formatPrecise(BigInteger v) {
        Scaled s = scale(v);
        int m = s.mantissa();
        if (s.tier() == 0) {
            if (m < 1000) return Integer.toString(m);
            return withFrac(m / 1000, (m % 1000) / 10) + "k";
        }
        if (m >= 1000) return dotted(m) + SUFFIX[s.tier()];
        int frac = s.remainder().multiply(BigInteger.valueOf(100)).divide(s.unit()).intValue();
        return withFrac(m, frac) + SUFFIX[s.tier()];
    }

    public static String dotted(long n) {
        String s = Long.toString(n);
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            if (++c % 3 == 0 && i > 0) sb.append('.');
        }
        return sb.reverse().toString();
    }

    private static String withFrac(long whole, int frac2) {
        if (frac2 <= 0) return Long.toString(whole);
        String f = (frac2 < 10 ? "0" : "") + frac2;
        if (f.endsWith("0")) f = f.substring(0, 1);
        return whole + "," + f;
    }

    public static BigInteger fromDouble(double d) {
        if (Double.isNaN(d) || d <= 0) return BigInteger.ZERO;
        if (Double.isInfinite(d)) return MAX;
        return clamp(new BigDecimal(d).toBigInteger());
    }

    public static BigInteger mul(BigInteger a, double m) {
        if (a == null || a.signum() == 0 || Double.isNaN(m) || m <= 0) return BigInteger.ZERO;
        if (Double.isInfinite(m)) return MAX;
        return clamp(new BigDecimal(a).multiply(new BigDecimal(m)).toBigInteger());
    }
}
