package com.bitaspire.libs.formula;

import com.bitaspire.libs.formula.expression.ExpressionBuilder;
import com.bitaspire.libs.formula.expression.ExpressionConfig;
import com.bitaspire.libs.formula.expression.ExpressionDictionary;
import com.bitaspire.libs.formula.token.Function;
import com.bitaspire.libs.formula.token.Operator;
import com.bitaspire.libs.formula.token.OperatorType;

import java.util.Collections;
import java.util.stream.Collectors;

public class DoubleExpressionBuilder extends ExpressionBuilder<Double> {

    public DoubleExpressionBuilder() {
        super(new ExpressionConfig<Double>() {
            @Override
            protected Double stringToOperand(String operand) {
                return Double.parseDouble(operand);
            }

            protected String operandToString(Double operand) {
                return operand == operand.intValue() ? String.valueOf(operand.intValue()) : operand.toString();
            }
        });

        this.initialize();
    }

    protected void initialize() {
        ExpressionDictionary<Double> expressionDictionary = this.getExpressionDictionary();

        expressionDictionary.addOperator(new Operator<>("+", OperatorType.PREFIX, Integer.MAX_VALUE, (parameters) -> parameters.get(0).value()));
        expressionDictionary.addOperator(new Operator<>("-", OperatorType.PREFIX, Integer.MAX_VALUE, (parameters) -> -parameters.get(0).value()));

        expressionDictionary.addOperator(new Operator<>("+", OperatorType.INFIX, 1, (parameters) -> parameters.get(0).value() + parameters.get(1).value()));
        expressionDictionary.addOperator(new Operator<>("-", OperatorType.INFIX, 1, (parameters) -> parameters.get(0).value() - parameters.get(1).value()));

        expressionDictionary.addOperator(new Operator<>("*", OperatorType.INFIX, 2, (parameters) -> parameters.get(0).value() * parameters.get(1).value()));
        expressionDictionary.addOperator(new Operator<>("/", OperatorType.INFIX, 2, (parameters) -> parameters.get(0).value() / parameters.get(1).value()));
        expressionDictionary.addOperator(new Operator<>("%", OperatorType.INFIX, 2, (parameters) -> parameters.get(0).value() % parameters.get(1).value()));

        expressionDictionary.addOperator(new Operator<>("^", OperatorType.INFIX_RTL, 3, (parameters) -> Math.pow(parameters.get(0).value(), parameters.get(1).value())));

        expressionDictionary.addOperator(new Operator<>("!", OperatorType.POSTFIX, 5, (parameters) -> DoubleUtils.factorial(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("abs", OperatorType.PREFIX, 4, (parameters) -> Math.abs(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("sin", OperatorType.PREFIX, 4, (parameters) -> Math.sin(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("cos", OperatorType.PREFIX, 4, (parameters) -> Math.cos(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("tan", OperatorType.PREFIX, 4, (parameters) -> Math.tan(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("asin", OperatorType.PREFIX, 4, (parameters) -> Math.asin(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("acos", OperatorType.PREFIX, 4, (parameters) -> Math.acos(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("atan", OperatorType.PREFIX, 4, (parameters) -> Math.atan(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("sinh", OperatorType.PREFIX, 4, (parameters) -> Math.sinh(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("cosh", OperatorType.PREFIX, 4, (parameters) -> Math.cosh(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("tanh", OperatorType.PREFIX, 4, (parameters) -> Math.tanh(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("asinh", OperatorType.PREFIX, 4, (parameters) -> DoubleUtils.asinh(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("acosh", OperatorType.PREFIX, 4, (parameters) -> DoubleUtils.acosh(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("atanh", OperatorType.PREFIX, 4, (parameters) -> DoubleUtils.atanh(parameters.get(0).value())));

        expressionDictionary.addFunction(new Function<>("deg", 1, (parameters) -> Math.toDegrees(parameters.get(0).value())));
        expressionDictionary.addFunction(new Function<>("rad", 1, (parameters) -> Math.toRadians(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("round", OperatorType.PREFIX, 4, (parameters) -> (double) Math.round(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("floor", OperatorType.PREFIX, 4, (parameters) -> Math.floor(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("ceil", OperatorType.PREFIX, 4, (parameters) -> Math.ceil(parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("ln", OperatorType.PREFIX, 4, (parameters) -> Math.log(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("log10", OperatorType.PREFIX, 4, (parameters) -> Math.log10(parameters.get(0).value())));
        expressionDictionary.addFunction(new Function<>("log", 2, (parameters) -> DoubleUtils.log(parameters.get(1).value(), parameters.get(0).value())));

        expressionDictionary.addOperator(new Operator<>("sqrt", OperatorType.PREFIX, 4, (parameters) -> Math.sqrt(parameters.get(0).value())));
        expressionDictionary.addOperator(new Operator<>("cbrt", OperatorType.PREFIX, 4, (parameters) -> Math.cbrt(parameters.get(0).value())));

        expressionDictionary.addFunction(new Function<>("exp", 1, (parameters) -> Math.exp(parameters.get(0).value())));

        expressionDictionary.addFunction(new Function<>("max", (parameters) -> parameters.isEmpty() ? 0.0 : Collections.max(parameters.stream().map(e -> e.value()).collect(Collectors.toList()))));
        expressionDictionary.addFunction(new Function<>("min", (parameters) -> parameters.isEmpty() ? 0.0 : Collections.min(parameters.stream().map(e -> e.value()).collect(Collectors.toList()))));

        expressionDictionary.addFunction(new Function<>("mean", (parameters) -> DoubleUtils.average(parameters.stream().map(e -> e.value()).collect(Collectors.toList()))));
        expressionDictionary.addFunction(new Function<>("average", (parameters) -> DoubleUtils.average(parameters.stream().map(e -> e.value()).collect(Collectors.toList()))));

        expressionDictionary.addFunction(new Function<>("rand", 0, (parameters) -> Math.random()));

        expressionDictionary.addConstant("pi", Math.PI);
        expressionDictionary.addConstant("e", Math.E);
    }
}
