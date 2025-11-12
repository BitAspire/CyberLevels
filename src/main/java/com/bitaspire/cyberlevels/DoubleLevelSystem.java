package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.user.UserManager;
import com.bitaspire.libs.formula.DoubleExpressionBuilder;
import com.bitaspire.cyberlevels.level.Formula;
import com.bitaspire.cyberlevels.level.Operator;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.libs.formula.expression.ExpressionBuilder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;

@Getter
final class DoubleLevelSystem extends BaseSystem<Double> {

    private final Operator<Double> operator;

    DoubleLevelSystem(CyberLevels main) {
        super(main);
        setLeaderboardFunction(DoubleLeaderboard::new);

        operator = new Operator<Double>() {
            @Override
            public Double zero() {
                return 0.0;
            }

            @Override
            public Double valueOf(String value) throws NumberFormatException {
                return Double.parseDouble(value);
            }

            @Override
            public Double fromDouble(double value) {
                return value;
            }

            @Override
            public Double add(Double a, Double b) {
                return a + b;
            }

            @Override
            public Double subtract(Double a, Double b) {
                return a - b;
            }

            @Override
            public Double multiply(Double a, Double b) {
                return a * b;
            }

            @Override
            public Double divide(Double a, Double b) {
                return a / b;
            }

            @Override
            public Double divide(Double a, Double b, int scale, RoundingMode mode) {
                if (b == 0.0) return 0.0;

                double result = a / b;
                double factor = Math.pow(10, scale);

                switch (mode) {
                    case UP:
                        return Math.ceil(result * factor) / factor;
                    case DOWN:
                        return Math.floor(result * factor) / factor;
                    case HALF_UP: default:
                        return Math.round(result * factor) / factor;
                }
            }

            @Override
            public int compare(Double a, Double b) {
                return Double.compare(a, b);
            }

            @Override
            public Double min(Double a, Double b) {
                return Math.min(a, b);
            }

            @Override
            public Double max(Double a, Double b) {
                return Math.max(a, b);
            }

            @Override
            public Double abs(Double a) {
                return Math.abs(a);
            }

            @Override
            public Double negate(Double a) {
                return -a;
            }

            @Override
            public String toString(Double value) {
                return value.toString();
            }
        };
    }

    class DoubleLeaderboard extends BaseLeaderboard<Double> {

        DoubleLeaderboard(UserManager<Double> manager) {
            super(manager);
        }

        @Override
        Entry<Double> toEntry(LevelUser<Double> user) {
            return new Entry<Double>(
                    user.getUuid(), user.getName(),
                    user.getLevel(), user.getExp(), user
            ) {
                @Override
                public int compareTo(@NotNull Entry<Double> other) {
                    if (getLevel() != other.getLevel())
                        return Long.compare(other.getLevel(), getLevel());

                    return Double.compare(other.getExp(), getExp());
                }
            };
        }
    }

    @Override
    Formula<Double> createFormula(String string) {
        return new BaseFormula<Double>(operator, string) {
            @NotNull
            ExpressionBuilder<Double> builder() {
                return new DoubleExpressionBuilder();
            }
        };
    }
}
