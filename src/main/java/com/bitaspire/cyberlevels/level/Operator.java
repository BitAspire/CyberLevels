package com.bitaspire.cyberlevels.level;

import java.math.RoundingMode;

/**
 * Represents a mathematical operator for performing arithmetic operations on numbers.
 *
 * @param <N> the numeric type used for calculations
 */
public interface Operator<N extends Number> {

    /**
     * Returns the zero value of the numeric type.
     * @return the zero value
     */
    N zero();

    /**
     * Converts a string representation of a number to the numeric type.
     *
     * @param value the string representation of the number
     *
     * @return the numeric value
     * @throws NumberFormatException if the string cannot be parsed to a number
     */
    N valueOf(String value) throws NumberFormatException;

    /**
     * Converts a double value to the numeric type.
     *
     * @param value the double value
     * @return the numeric value
     */
    N fromDouble(double value);

    /**
     * Adds two numeric values.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the sum of a and b
     */
    N add(N a, N b);

    /**
     * Subtracts the second numeric value from the first.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the result of a - b
     */
    N subtract(N a, N b);

    /**
     * Multiplies two numeric values.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the product of a and b
     */
    N multiply(N a, N b);

    /**
     * Divides the first numeric value by the second.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the result of a / b
     * @throws ArithmeticException if division by zero occurs
     */
    N divide(N a, N b);

    /**
     * Divides the first numeric value by the second with specified scale and rounding mode.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     * @param scale the number of digits to the right of the decimal point
     * @param mode the rounding mode to apply
     *
     * @return the result of a / b with specified scale and rounding
     * @throws ArithmeticException if division by zero occurs
     */
    N divide(N a, N b, int scale, RoundingMode mode);

    /**
     * Compares two numeric values.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return a negative integer, zero, or a positive integer as a is less than, equal to, or greater than b
     */
    int compare(N a, N b);

    /**
     * Returns the minimum of two numeric values.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the minimum of a and b
     */
    N min(N a, N b);

    /**
     * Returns the maximum of two numeric values.
     *
     * @param a the first numeric value
     * @param b the second numeric value
     *
     * @return the maximum of a and b
     */
    N max(N a, N b);

    /**
     * Returns the absolute value of the numeric value.
     *
     * @param a the numeric value
     * @return the absolute value of a
     */
    N abs(N a);

    /**
     * Returns the negation of the numeric value.
     *
     * @param a the numeric value
     * @return the negation of a
     */
    N negate(N a);

    /**
     * Converts the numeric value to its string representation.
     *
     * @param value the numeric value
     * @return the string representation of the numeric value
     */
    String toString(N value);
}
