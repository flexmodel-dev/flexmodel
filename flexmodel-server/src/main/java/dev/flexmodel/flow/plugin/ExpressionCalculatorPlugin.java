package dev.flexmodel.flow.plugin;

import dev.flexmodel.flow.common.util.ExpressionCalculator;

public interface ExpressionCalculatorPlugin extends Plugin {
  ExpressionCalculator getExpressionCalculator();
}
