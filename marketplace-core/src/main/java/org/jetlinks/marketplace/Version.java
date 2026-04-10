package org.jetlinks.marketplace;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 通用版本号对象，支持常见版本规则解析与比对。
 *
 * <p>支持的常见规则包括：</p>
 * <ul>
 *     <li>数字段按数值比较，例如 {@code 1.0.10 > 1.0.2}</li>
 *     <li>可解析前缀 {@code v}，例如 {@code v1.2.3}</li>
 *     <li>支持常见预发布标识：{@code alpha/beta/m/rc/snapshot}</li>
 *     <li>支持发布别名：{@code final/release/ga/stable}</li>
 *     <li>支持常见补丁标识：{@code sp/patch/hotfix}</li>
 *     <li>忽略 {@code +build} 形式的构建元数据</li>
 * </ul>
 *
 * @author zhouhao
 * @since 2.12
 */
public final class Version implements Comparable<Version>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int STAGE_DEV = -60;
    private static final int STAGE_ALPHA = -50;
    private static final int STAGE_BETA = -40;
    private static final int STAGE_MILESTONE = -30;
    private static final int STAGE_PREVIEW = -25;
    private static final int STAGE_RC = -20;
    private static final int STAGE_NUMERIC_PRE = -70;
    private static final int STAGE_SNAPSHOT = -10;
    private static final int STAGE_UNKNOWN = -5;
    private static final int STAGE_RELEASE = 0;
    private static final int STAGE_SERVICE_PACK = 10;

    private static final Map<String, QualifierInfo> QUALIFIER_MAPPING = createQualifierMapping();

    private final String value;
    private final List<BigInteger> numbers;
    private final List<Token> qualifiers;

    private Version(String value, List<BigInteger> numbers, List<Token> qualifiers) {
        this.value = value;
        this.numbers = Collections.unmodifiableList(numbers);
        this.qualifiers = Collections.unmodifiableList(qualifiers);
    }

    public static Version of(String value) {
        String source = normalizeSource(value);
        String normalized = stripBuildMetadata(stripVersionPrefix(source));
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("version can not be blank");
        }

        List<Segment> segments = tokenize(normalized);
        List<BigInteger> numbers = normalizeNumbers(extractNumbers(segments));
        List<Token> qualifiers = normalizeQualifiers(extractQualifierSegments(segments));

        return new Version(source, numbers, qualifiers);
    }

    public static Version parse(String value) {
        return of(value);
    }

    public static Version parseNullable(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return of(value);
    }

    public static int compare(String left, String right) {
        if (left == null || left.trim().isEmpty()) {
            return right == null || right.trim().isEmpty() ? 0 : -1;
        }
        if (right == null || right.trim().isEmpty()) {
            return 1;
        }
        return Version.of(left).compareTo(Version.of(right));
    }

    public String getValue() {
        return value;
    }

    public List<BigInteger> getNumbers() {
        return numbers;
    }

    public boolean isBefore(Version other) {
        return compareTo(other) < 0;
    }

    public boolean isAfter(Version other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Version other) {
        if (other == null) {
            return 1;
        }

        int numCompare = compareNumbers(this.numbers, other.numbers);
        if (numCompare != 0) {
            return numCompare;
        }
        return compareQualifiers(this.qualifiers, other.qualifiers);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Version that)) {
            return false;
        }
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numbers, qualifiers);
    }

    @Override
    public String toString() {
        return value;
    }

    private static int compareNumbers(List<BigInteger> left, List<BigInteger> right) {
        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            BigInteger l = i < left.size() ? left.get(i) : BigInteger.ZERO;
            BigInteger r = i < right.size() ? right.get(i) : BigInteger.ZERO;
            int compare = l.compareTo(r);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static int compareQualifiers(List<Token> left, List<Token> right) {
        if (left.isEmpty()) {
            return right.isEmpty() ? 0 : compareReleaseWithQualifier(right);
        }
        if (right.isEmpty()) {
            return -compareReleaseWithQualifier(left);
        }

        int stageCompare = Integer.compare(resolveStage(left), resolveStage(right));
        if (stageCompare != 0) {
            return stageCompare;
        }

        int max = Math.max(left.size(), right.size());
        for (int i = 0; i < max; i++) {
            if (i >= left.size()) {
                return remainingQualifierCompare(right, i);
            }
            if (i >= right.size()) {
                return -remainingQualifierCompare(left, i);
            }

            int compare = left.get(i).compareTo(right.get(i));
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static int compareReleaseWithQualifier(List<Token> qualifier) {
        int stage = resolveStage(qualifier);
        if (stage > STAGE_RELEASE) {
            return -1;
        }
        if (stage < STAGE_RELEASE) {
            return 1;
        }
        return qualifier.isEmpty() ? 0 : -1;
    }

    private static int remainingQualifierCompare(List<Token> tokens, int offset) {
        for (int i = offset; i < tokens.size(); i++) {
            if (tokens.get(i).isSignificant()) {
                return 1;
            }
        }
        return 0;
    }

    private static int resolveStage(List<Token> qualifiers) {
        if (qualifiers.isEmpty()) {
            return STAGE_RELEASE;
        }
        Token token = qualifiers.get(0);
        if (token.numeric != null) {
            return STAGE_NUMERIC_PRE;
        }
        return token.stage;
    }

    private static List<BigInteger> extractNumbers(List<Segment> segments) {
        List<BigInteger> numbers = new ArrayList<>();
        int qualifierIndex = findQualifierStart(segments);
        for (int i = 0; i < qualifierIndex; i++) {
            Segment segment = segments.get(i);
            if (segment.isNumeric()) {
                numbers.add(new BigInteger(segment.value));
            }
        }
        if (numbers.isEmpty()) {
            numbers.add(BigInteger.ZERO);
        }
        return numbers;
    }

    private static List<Segment> extractQualifierSegments(List<Segment> segments) {
        int qualifierIndex = findQualifierStart(segments);
        if (qualifierIndex >= segments.size()) {
            return List.of();
        }
        return segments.subList(qualifierIndex, segments.size());
    }

    private static int findQualifierStart(List<Segment> segments) {
        boolean seenNumeric = false;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.isNumeric()) {
                seenNumeric = true;
            }
            if (seenNumeric && segment.separatorBefore == '-') {
                return i;
            }
            if (!segment.isNumeric()) {
                return i;
            }
        }
        return segments.size();
    }

    private static List<BigInteger> normalizeNumbers(List<BigInteger> numbers) {
        int end = numbers.size();
        while (end > 1 && BigInteger.ZERO.equals(numbers.get(end - 1))) {
            end--;
        }
        return new ArrayList<>(numbers.subList(0, end));
    }

    private static List<Token> normalizeQualifiers(List<Segment> segments) {
        if (segments.isEmpty()) {
            return List.of();
        }

        List<Token> tokens = new ArrayList<>();
        for (Segment segment : segments) {
            Token token = Token.of(segment.value);
            if (token.isReleaseAlias()) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static List<Segment> tokenize(String version) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char separator = 0;

        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);

            if (isSeparator(c)) {
                if (current.length() > 0) {
                    segments.add(new Segment(current.toString(), separator));
                    current.setLength(0);
                }
                separator = c;
                continue;
            }

            if (current.length() > 0 && isDigit(current.charAt(current.length() - 1)) != isDigit(c)) {
                segments.add(new Segment(current.toString(), separator));
                current.setLength(0);
                separator = 0;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            segments.add(new Segment(current.toString(), separator));
        }

        return segments;
    }

    private static String normalizeSource(String value) {
        if (value == null) {
            throw new IllegalArgumentException("version can not be null");
        }
        String source = value.trim();
        if (source.isEmpty()) {
            throw new IllegalArgumentException("version can not be blank");
        }
        return source;
    }

    private static String stripVersionPrefix(String value) {
        if (value.length() > 1 && (value.charAt(0) == 'v' || value.charAt(0) == 'V') && isDigit(value.charAt(1))) {
            return value.substring(1);
        }
        return value;
    }

    private static String stripBuildMetadata(String value) {
        int idx = value.indexOf('+');
        return idx >= 0 ? value.substring(0, idx) : value;
    }

    private static boolean isSeparator(char c) {
        return c == '.' || c == '-' || c == '_';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static Map<String, QualifierInfo> createQualifierMapping() {
        Map<String, QualifierInfo> mapping = new HashMap<>();
        register(mapping, "", STAGE_RELEASE);
        register(mapping, "alpha", STAGE_ALPHA, "a");
        register(mapping, "beta", STAGE_BETA, "b");
        register(mapping, "milestone", STAGE_MILESTONE, "m");
        register(mapping, "preview", STAGE_PREVIEW, "pre", "pr");
        register(mapping, "rc", STAGE_RC, "cr");
        register(mapping, "snapshot", STAGE_SNAPSHOT);
        register(mapping, "dev", STAGE_DEV, "nightly", "canary", "ea");
        register(mapping, "", STAGE_RELEASE, "final", "release", "ga", "stable");
        register(mapping, "sp", STAGE_SERVICE_PACK, "servicepack", "patch", "p", "pl", "hotfix", "hf");
        return Collections.unmodifiableMap(mapping);
    }

    private static void register(Map<String, QualifierInfo> mapping,
                                 String canonical,
                                 int stage,
                                 String... aliases) {
        QualifierInfo info = new QualifierInfo(canonical, stage);
        mapping.put(canonical, info);
        for (String alias : aliases) {
            mapping.put(alias, info);
        }
    }

    private static final class Segment {
        private final String value;
        private final char separatorBefore;

        private Segment(String value, char separatorBefore) {
            this.value = value;
            this.separatorBefore = separatorBefore;
        }

        private boolean isNumeric() {
            for (int i = 0; i < value.length(); i++) {
                if (!isDigit(value.charAt(i))) {
                    return false;
                }
            }
            return !value.isEmpty();
        }
    }

    private static final class QualifierInfo {
        private final String canonical;
        private final int stage;

        private QualifierInfo(String canonical, int stage) {
            this.canonical = canonical;
            this.stage = stage;
        }
    }

    private static final class Token implements Comparable<Token>, Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final BigInteger numeric;
        private final String text;
        private final int stage;

        private Token(BigInteger numeric, String text, int stage) {
            this.numeric = numeric;
            this.text = text;
            this.stage = stage;
        }

        private static Token of(String raw) {
            if (raw.chars().allMatch(Character::isDigit)) {
                return new Token(new BigInteger(raw), null, STAGE_NUMERIC_PRE);
            }

            String key = raw.toLowerCase(Locale.ROOT);
            QualifierInfo info = QUALIFIER_MAPPING.get(key);
            if (info != null) {
                return new Token(null, info.canonical, info.stage);
            }
            return new Token(null, key, STAGE_UNKNOWN);
        }

        private boolean isReleaseAlias() {
            return numeric == null && stage == STAGE_RELEASE && (text == null || text.isEmpty());
        }

        private boolean isSignificant() {
            return numeric != null || !isReleaseAlias();
        }

        @Override
        public int compareTo(Token other) {
            if (other == null) {
                return 1;
            }
            if (numeric != null && other.numeric != null) {
                return numeric.compareTo(other.numeric);
            }
            if (numeric != null) {
                return -1;
            }
            if (other.numeric != null) {
                return 1;
            }

            int stageCompare = Integer.compare(stage, other.stage);
            if (stageCompare != 0) {
                return stageCompare;
            }

            String left = text == null ? "" : text;
            String right = other.text == null ? "" : other.text;
            return left.compareTo(right);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Token that)) {
                return false;
            }
            return stage == that.stage
                && Objects.equals(numeric, that.numeric)
                && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numeric, text, stage);
        }
    }
}
