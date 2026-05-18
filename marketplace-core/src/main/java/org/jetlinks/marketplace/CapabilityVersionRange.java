package org.jetlinks.marketplace;

/**
 * 能力依赖版本范围匹配工具.
 *
 * @author zhouhao
 * @since 2.12
 */
public final class CapabilityVersionRange {

    private CapabilityVersionRange() {
    }

    public static boolean matches(String version,
                                  String versionRange) {
        if (!hasText(versionRange)) {
            return true;
        }
        String[] conditions = versionRange.split(",", -1);
        for (String condition : conditions) {
            if (!matchesCondition(version, condition)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesCondition(String version,
                                            String condition) {
        String expr = condition == null ? "" : condition.trim();
        if (!hasText(expr)) {
            throw new IllegalArgumentException("version range can not contain blank condition");
        }

        String operator;
        String expected;
        if (expr.startsWith(">=") || expr.startsWith("<=") || expr.startsWith("==")) {
            operator = expr.substring(0, 2);
            expected = expr.substring(2).trim();
        } else if (expr.startsWith(">") || expr.startsWith("<") || expr.startsWith("=")) {
            operator = expr.substring(0, 1);
            expected = expr.substring(1).trim();
        } else {
            operator = "==";
            expected = expr;
        }
        if (!hasText(expected)) {
            throw new IllegalArgumentException("version range condition must contain version");
        }

        int compared = Version.compare(version, expected);
        return switch (operator) {
            case ">" -> compared > 0;
            case ">=" -> compared >= 0;
            case "<" -> compared < 0;
            case "<=" -> compared <= 0;
            case "=", "==" -> compared == 0;
            default -> throw new IllegalArgumentException("unsupported version range operator: " + operator);
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
