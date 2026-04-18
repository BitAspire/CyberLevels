package com.bitaspire.cyberlevels.level;

import java.math.RoundingMode;

/**
 * Abstraction over the numeric engine used by CyberLevels.
 *
 * <p>The plugin can run either on a lightweight {@code double}-based implementation or on a
 * higher-precision big-decimal implementation. This interface hides those differences behind a
 * shared arithmetic API so the rest of the level system can stay generic.
 *
 * @param <N> numeric type handled by the implementation
 */
public interface Operator<N extends Number> {

    /**
     * Returns the additive identity of the numeric type.
     *
     * @return zero value for the current numeric engine
     */
    N zero();

    /**
     * Parses a textual numeric value into the engine's native number type.
     *
     * @param value textual number to parse
     * @return parsed numeric value
     * @throws NumberFormatException when the input cannot be parsed
     */
    N valueOf(String value) throws NumberFormatException;

    /**
     * Converts a primitive {@code double} into the engine's native number type.
     *
     * @param value primitive value to convert
     * @return converted numeric value
     */
    N fromDouble(double value);

    /**
     * Adds two values using the engine's precision rules.
     *
     * @param a left operand
     * @param b right operand
     * @return addition result
     */
    N add(N a, N b);

    /**
     * Subtracts one value from another using the engine's precision rules.
     *
     * @param a left operand
     * @param b right operand
     * @return subtraction result
     */
    N subtract(N a, N b);

    /**
     * Multiplies two values using the engine's precision rules.
     *
     * @param a left operand
     * @param b right operand
     * @return multiplication result
     */
    N multiply(N a, N b);

    /**
     * Divides one value by another using the engine's default division strategy.
     *
     * @param a dividend
     * @param b divisor
     * @return division result
     * @throws ArithmeticException when the divisor is zero
     */
    N divide(N a, N b);

    /**
     * Divides one value by another with an explicit scale and rounding policy.
     *
     * @param a dividend
     * @param b divisor
     * @param scale amount of fractional precision to keep
     * @param mode rounding mode to apply when needed
     * @return scaled division result
     * @throws ArithmeticException when the divisor is zero
     */
    N divide(N a, N b, int scale, RoundingMode mode);

    /**
     * Compares two values according to the engine's natural ordering.
     *
     * @param a first value
     * @param b second value
     * @return negative, zero, or positive depending on the ordering of {@code a} and {@code b}
     */
    int compare(N a, N b);

    /**
     * Returns the smaller of the two supplied values.
     *
     * @param a first value
     * @param b second value
     * @return smaller value
     */
    N min(N a, N b);

    /**
     * Returns the larger of the two supplied values.
     *
     * @param a first value
     * @param b second value
     * @return larger value
     */
    N max(N a, N b);

    /**
     * Returns the absolute value of the supplied number.
     *
     * @param a value to normalize
     * @return absolute value
     */
    N abs(N a);

    /**
     * Returns the additive inverse of the supplied number.
     *
     * @param a value to negate
     * @return negated value
     */
    N negate(N a);

    /**
     * Formats the supplied value using the engine's canonical string representation.
     *
     * @param value numeric value to format
     * @return engine-specific string form
     */
    String toString(N value);
}
