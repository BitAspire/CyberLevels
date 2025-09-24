package com.bitaspire.libs.formula;

import com.bitaspire.libs.formula.exception.Expr4jException;

import java.util.List;

public class DoubleUtils {

    private DoubleUtils() {}

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    public static double acosh(double x) {
        return Math.log(x + Math.sqrt(x * x - 1));
    }

    public static double atanh(double x) {
        return 0.5 * Math.log((1 + x) / (1 - x));
    }

    public static double log(double x, double y) {
        return Math.log(x) / Math.log(y);
    }

    public static Double average(List<Double> list) {
        return list.stream().mapToDouble(d -> d).average().orElse(0.0);
    }

    private static double factorial(int n) {
        double factorial = 1.0;
        for (int i = 2; i <= n; i++) {
            factorial *= i;
        }
        return factorial;
    }

    public static double factorial(double x) {
        if (x < 0 || x != (int) x) {
            throw new Expr4jException("Cannot calculate factorial of " + x);
        }
        return factorial((int) x);
    }

}
