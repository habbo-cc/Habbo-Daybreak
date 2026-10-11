package com.eu.habbo.habbohotel.wired.variables;

import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded, signed-32 arithmetic for July AIR Change Variable action 41.
 *
 * <p>The AIR client defines the operation codes but not the emulator's
 * overflow policy. Invalid operands and results outside the signed-32 domain
 * therefore fail without producing a mutation. Unknown operations are never
 * silently treated as Assign.</p>
 */
public final class WiredVariableActionMath {
    public static final int ASSIGN = 0;
    public static final int ADD = 1;
    public static final int SUBTRACT = 2;
    public static final int MULTIPLY = 3;
    public static final int DIVIDE = 4;
    public static final int POWER = 5;
    public static final int MODULO = 6;
    public static final int SET_MINIMUM = 40;
    public static final int SET_MAXIMUM = 41;
    public static final int RANDOM_UPPER_BOUND = 50;
    public static final int ABSOLUTE = 60;
    public static final int BITWISE_AND = 100;
    public static final int BITWISE_OR = 101;
    public static final int BITWISE_XOR = 102;
    public static final int BITWISE_NOT = 103;
    public static final int LEFT_SHIFT = 104;
    public static final int RIGHT_SHIFT = 105;
    public static final int BIT_COUNT = 110;
    public static final int NEXT_LOW_BIT = 111;
    public static final int NEXT_HIGH_BIT = 112;
    public static final int PREVIOUS_LOW_BIT = 113;
    public static final int PREVIOUS_HIGH_BIT = 114;
    public static final int GET_BIT = 115;
    public static final int SET_BIT = 116;
    public static final int CLEAR_BIT = 117;
    public static final int TOGGLE_BIT = 118;

    private WiredVariableActionMath() {
    }

    public static boolean requiresOperand(int operation) {
        return operation != BITWISE_NOT && operation != ABSOLUTE && operation != BIT_COUNT;
    }

    public static boolean isImplementedOperation(int operation) {
        return switch (operation) {
            case ASSIGN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO,
                    POWER, SET_MINIMUM, SET_MAXIMUM, RANDOM_UPPER_BOUND, ABSOLUTE,
                    BITWISE_AND, BITWISE_OR, BITWISE_XOR, BITWISE_NOT,
                    LEFT_SHIFT, RIGHT_SHIFT, BIT_COUNT,
                    NEXT_LOW_BIT, NEXT_HIGH_BIT, PREVIOUS_LOW_BIT, PREVIOUS_HIGH_BIT,
                    GET_BIT, SET_BIT, CLEAR_BIT, TOGGLE_BIT -> true;
            default -> false;
        };
    }

    public static OptionalInt apply(int operation, int current, int operand) {
        try {
            return switch (operation) {
                case ASSIGN -> OptionalInt.of(operand);
                case ADD -> OptionalInt.of(Math.addExact(current, operand));
                case SUBTRACT -> OptionalInt.of(Math.subtractExact(current, operand));
                case MULTIPLY -> OptionalInt.of(Math.multiplyExact(current, operand));
                case POWER -> powExact(current, operand);
                // Invalid integer operations fail without mutating the value.
                case DIVIDE -> operand == 0 || (current == Integer.MIN_VALUE && operand == -1)
                        ? OptionalInt.empty() : OptionalInt.of(current / operand);
                case MODULO -> operand == 0 ? OptionalInt.empty() : OptionalInt.of(current % operand);
                case SET_MINIMUM -> OptionalInt.of(Math.max(current, operand));
                case SET_MAXIMUM -> OptionalInt.of(Math.min(current, operand));
                case ABSOLUTE -> current == Integer.MIN_VALUE
                        ? OptionalInt.empty() : OptionalInt.of(Math.abs(current));
                case BITWISE_AND -> OptionalInt.of(current & operand);
                case BITWISE_OR -> OptionalInt.of(current | operand);
                case BITWISE_XOR -> OptionalInt.of(current ^ operand);
                case BITWISE_NOT -> OptionalInt.of(~current);
                // Inclusive upper bound follows the donor runtime's operation 50.
                case RANDOM_UPPER_BOUND -> OptionalInt.of(randomThrough(operand));
                case LEFT_SHIFT -> validBitIndex(operand)
                        ? OptionalInt.of(current << operand) : OptionalInt.empty();
                case RIGHT_SHIFT -> validBitIndex(operand)
                        ? OptionalInt.of(current >> operand) : OptionalInt.empty();
                case BIT_COUNT -> OptionalInt.of(Integer.bitCount(current));
                case NEXT_LOW_BIT -> nextBit(current, operand, false, true);
                case NEXT_HIGH_BIT -> nextBit(current, operand, true, true);
                case PREVIOUS_LOW_BIT -> nextBit(current, operand, false, false);
                case PREVIOUS_HIGH_BIT -> nextBit(current, operand, true, false);
                case GET_BIT -> validBitIndex(operand)
                        ? OptionalInt.of((current >>> operand) & 1) : OptionalInt.empty();
                case SET_BIT -> validBitIndex(operand)
                        ? OptionalInt.of(current | (1 << operand)) : OptionalInt.empty();
                case CLEAR_BIT -> validBitIndex(operand)
                        ? OptionalInt.of(current & ~(1 << operand)) : OptionalInt.empty();
                case TOGGLE_BIT -> validBitIndex(operand)
                        ? OptionalInt.of(current ^ (1 << operand)) : OptionalInt.empty();
                default -> OptionalInt.empty();
            };
        } catch (ArithmeticException ignored) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt powExact(int base, int exponent) {
        if (exponent < 0) {
            return OptionalInt.empty();
        }
        int result = 1;
        int factor = base;
        int remaining = exponent;
        while (remaining != 0) {
            if ((remaining & 1) != 0) {
                result = Math.multiplyExact(result, factor);
            }
            remaining >>>= 1;
            if (remaining != 0) {
                factor = Math.multiplyExact(factor, factor);
            }
        }
        return OptionalInt.of(result);
    }

    private static int randomThrough(int upperBound) {
        if (upperBound <= 0) {
            return 0;
        }
        return upperBound == Integer.MAX_VALUE
                ? ThreadLocalRandom.current().nextInt() & Integer.MAX_VALUE
                : ThreadLocalRandom.current().nextInt(upperBound + 1);
    }

    private static OptionalInt nextBit(int value, int start, boolean high, boolean forwards) {
        if (!validBitIndex(start)) {
            return OptionalInt.empty();
        }
        int candidates = high ? value : ~value;
        if (forwards) {
            candidates &= -1 << start;
            return candidates == 0
                    ? OptionalInt.empty()
                    : OptionalInt.of(Integer.numberOfTrailingZeros(candidates));
        }
        candidates &= -1 >>> (31 - start);
        return candidates == 0
                ? OptionalInt.empty()
                : OptionalInt.of(31 - Integer.numberOfLeadingZeros(candidates));
    }

    private static boolean validBitIndex(int index) {
        return index >= 0 && index < Integer.SIZE;
    }
}
