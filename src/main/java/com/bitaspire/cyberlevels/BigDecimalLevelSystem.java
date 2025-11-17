package com.bitaspire.cyberlevels;

import com.bitaspire.cyberlevels.user.UserManager;
import com.bitaspire.libs.formula.BigDecimalExpressionBuilder;
import com.bitaspire.cyberlevels.level.Formula;
import com.bitaspire.cyberlevels.level.Operator;
import com.bitaspire.cyberlevels.user.LevelUser;
import com.bitaspire.libs.formula.expression.ExpressionBuilder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
final class BigDecimalLevelSystem extends BaseSystem<BigDecimal> {

    private final Operator<BigDecimal> operator;

    BigDecimalLevelSystem(CyberLevels main) {
        super(main);
        setLeaderboardFunction(BigDecimalLeaderboard::new);

        operator = new Operator<BigDecimal>() {
            @Override
            public BigDecimal zero() {
                return BigDecimal.ZERO;
            }

            @Override
            public BigDecimal valueOf(String value) throws NumberFormatException {
                return new BigDecimal(value);
            }

            @Override
            public BigDecimal fromDouble(double value) {
                return BigDecimal.valueOf(value);
            }

            @Override
            public BigDecimal add(BigDecimal a, BigDecimal b) {
                return a.add(b);
            }

            @Override
            public BigDecimal subtract(BigDecimal a, BigDecimal b) {
                return a.subtract(b);
            }

            @Override
            public BigDecimal multiply(BigDecimal a, BigDecimal b) {
                return a.multiply(b);
            }

            @Override
            public BigDecimal divide(BigDecimal a, BigDecimal b) {
                return a.divide(b, RoundingMode.HALF_UP);
            }

            @Override
            public BigDecimal divide(BigDecimal a, BigDecimal b, int scale, RoundingMode mode) {
                return b.signum() == 0 ? zero() : a.divide(b, scale, mode);
            }

            @Override
            public BigDecimal min(BigDecimal a, BigDecimal b) {
                return a.min(b);
            }

            @Override
            public BigDecimal max(BigDecimal a, BigDecimal b) {
                return a.max(b);
            }

            @Override
            public int compare(BigDecimal a, BigDecimal b) {
                return a.compareTo(b);
            }

            @Override
            public BigDecimal abs(BigDecimal a) {
                return a.abs();
            }

            @Override
            public BigDecimal negate(BigDecimal a) {
                return a.negate();
            }

            @Override
            public String toString(BigDecimal value) {
                return value.toPlainString();
            }
        };
    }

    class BigDecimalLeaderboard extends BaseLeaderboard<BigDecimal> {

        BigDecimalLeaderboard(UserManager<BigDecimal> manager) {
            super(manager);
        }

        @Override
        Entry<BigDecimal> toEntry(LevelUser<BigDecimal> user) {
            return new Entry<BigDecimal>(
                    user.getUuid(), user.getName(),
                    user.getLevel(), user.getExp(), user
            ) {
                @Override
                public int compareTo(@NotNull Entry<BigDecimal> other) {
                    if (getLevel() != other.getLevel())
                        return Long.compare(other.getLevel(), getLevel());

                    return other.getExp().compareTo(getExp());
                }
            };
        }
    }

    @Override
    Formula<BigDecimal> createFormula(String string) {
        return new BaseFormula<BigDecimal>(operator, string) {
            @NotNull
            ExpressionBuilder<BigDecimal> builder() {
                return new BigDecimalExpressionBuilder();
            }
        };
    }
}
