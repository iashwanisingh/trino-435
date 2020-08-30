/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.operator.scalar.timestamp;

import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.LiteralParameters;
import io.prestosql.spi.function.ScalarOperator;
import io.prestosql.spi.function.SqlType;
import io.prestosql.spi.type.LongTimestamp;
import io.prestosql.spi.type.StandardTypes;
import io.prestosql.type.Constraint;
import org.joda.time.DateTimeField;
import org.joda.time.chrono.ISOChronology;

import static io.prestosql.spi.function.OperatorType.ADD;
import static io.prestosql.spi.function.OperatorType.GREATER_THAN;
import static io.prestosql.spi.function.OperatorType.GREATER_THAN_OR_EQUAL;
import static io.prestosql.spi.function.OperatorType.LESS_THAN;
import static io.prestosql.spi.function.OperatorType.LESS_THAN_OR_EQUAL;
import static io.prestosql.spi.function.OperatorType.SUBTRACT;
import static io.prestosql.spi.type.TimestampType.MAX_SHORT_PRECISION;
import static io.prestosql.type.DateTimes.MICROSECONDS_PER_MILLISECOND;
import static io.prestosql.type.DateTimes.getMicrosOfMilli;
import static io.prestosql.type.DateTimes.rescale;
import static io.prestosql.type.DateTimes.round;
import static io.prestosql.type.DateTimes.scaleEpochMicrosToMillis;
import static io.prestosql.type.DateTimes.scaleEpochMillisToMicros;
import static java.lang.Math.multiplyExact;

@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public final class TimestampOperators
{
    private TimestampOperators() {}

    @ScalarOperator(LESS_THAN)
    public static final class LessThan
    {
        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean lessThan(@SqlType("timestamp(p)") long left, @SqlType("timestamp(p)") long right)
        {
            return left < right;
        }

        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean lessThan(@SqlType("timestamp(p)") LongTimestamp left, @SqlType("timestamp(p)") LongTimestamp right)
        {
            return (left.getEpochMicros() < right.getEpochMicros()) ||
                    ((left.getEpochMicros() == right.getEpochMicros()) && (left.getPicosOfMicro() < right.getPicosOfMicro()));
        }
    }

    @ScalarOperator(LESS_THAN_OR_EQUAL)
    public static final class LessThanOrEqual
    {
        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean lessThanOrEqual(@SqlType("timestamp(p)") long left, @SqlType("timestamp(p)") long right)
        {
            return left <= right;
        }

        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean lessThanOrEqual(@SqlType("timestamp(p)") LongTimestamp left, @SqlType("timestamp(p)") LongTimestamp right)
        {
            return left.getEpochMicros() < right.getEpochMicros() ||
                    left.getEpochMicros() == right.getEpochMicros() && left.getPicosOfMicro() <= right.getPicosOfMicro();
        }
    }

    @ScalarOperator(GREATER_THAN)
    public static final class GreaterThan
    {
        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean greaterThan(@SqlType("timestamp(p)") long left, @SqlType("timestamp(p)") long right)
        {
            return left > right;
        }

        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean greaterThan(@SqlType("timestamp(p)") LongTimestamp left, @SqlType("timestamp(p)") LongTimestamp right)
        {
            return !LessThanOrEqual.lessThanOrEqual(left, right);
        }
    }

    @ScalarOperator(GREATER_THAN_OR_EQUAL)
    public static final class GreaterThanOrEqual
    {
        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean greaterThanOrEqual(@SqlType("timestamp(p)") long left, @SqlType("timestamp(p)") long right)
        {
            return left >= right;
        }

        @LiteralParameters("p")
        @SqlType(StandardTypes.BOOLEAN)
        public static boolean greaterThanOrEqual(@SqlType("timestamp(p)") LongTimestamp left, @SqlType("timestamp(p)") LongTimestamp right)
        {
            return !LessThan.lessThan(left, right);
        }
    }

    @ScalarOperator(ADD)
    public static final class TimestampPlusIntervalDayToSecond
    {
        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static long add(
                @SqlType("timestamp(p)") long timestamp,
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval)
        {
            // scale to micros
            interval = multiplyExact(interval, MICROSECONDS_PER_MILLISECOND);

            return timestamp + interval;
        }

        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static LongTimestamp add(
                @SqlType("timestamp(p)") LongTimestamp timestamp,
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval)
        {
            return new LongTimestamp(timestamp.getEpochMicros() + multiplyExact(interval, MICROSECONDS_PER_MILLISECOND), timestamp.getPicosOfMicro());
        }
    }

    @ScalarOperator(ADD)
    public static final class IntervalDayToSecondPlusTimestamp
    {
        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static long add(
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval,
                @SqlType("timestamp(p)") long timestamp)
        {
            return TimestampPlusIntervalDayToSecond.add(timestamp, interval);
        }

        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static LongTimestamp add(
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval,
                @SqlType("timestamp(p)") LongTimestamp timestamp)
        {
            return TimestampPlusIntervalDayToSecond.add(timestamp, interval);
        }
    }

    @ScalarOperator(ADD)
    public static final class TimestampPlusIntervalYearToMonth
    {
        private static final DateTimeField MONTH_OF_YEAR_UTC = ISOChronology.getInstanceUTC().monthOfYear();

        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static long add(
                @SqlType("timestamp(p)") long timestamp,
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval)
        {
            long fractionMicros = getMicrosOfMilli(timestamp);
            long result = MONTH_OF_YEAR_UTC.add(scaleEpochMicrosToMillis(timestamp), interval);
            return scaleEpochMillisToMicros(result) + fractionMicros;
        }

        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static LongTimestamp add(
                @SqlType("timestamp(p)") LongTimestamp timestamp,
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval)
        {
            return new LongTimestamp(
                    add(timestamp.getEpochMicros(), interval),
                    timestamp.getPicosOfMicro());
        }
    }

    @ScalarOperator(ADD)
    public static final class IntervalYearToMonthPlusTimestamp
    {
        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static long add(
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval,
                @SqlType("timestamp(p)") long timestamp)
        {
            return TimestampPlusIntervalYearToMonth.add(timestamp, interval);
        }

        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static LongTimestamp add(
                ConnectorSession session,
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval,
                @SqlType("timestamp(p)") LongTimestamp timestamp)
        {
            return TimestampPlusIntervalYearToMonth.add(timestamp, interval);
        }
    }

    @ScalarOperator(SUBTRACT)
    public static final class TimestampMinusIntervalYearToMonth
    {
        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static long subtract(
                @SqlType("timestamp(p)") long timestamp,
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval)
        {
            return TimestampPlusIntervalYearToMonth.add(timestamp, -interval);
        }

        @LiteralParameters("p")
        @SqlType("timestamp(p)")
        public static LongTimestamp subtract(
                ConnectorSession session,
                @SqlType("timestamp(p)") LongTimestamp timestamp,
                @SqlType(StandardTypes.INTERVAL_YEAR_TO_MONTH) long interval)
        {
            return TimestampPlusIntervalYearToMonth.add(timestamp, -interval);
        }
    }

    @ScalarOperator(SUBTRACT)
    public static final class TimestampMinusIntervalDayToSecond
    {
        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static long subtract(
                @SqlType("timestamp(p)") long timestamp,
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval)
        {
            return TimestampPlusIntervalDayToSecond.add(timestamp, -interval);
        }

        @LiteralParameters({"p", "u"})
        @SqlType("timestamp(u)")
        @Constraint(variable = "u", expression = "max(3, p)") // Interval is currently p = 3, so the minimum result precision is 3.
        public static LongTimestamp subtract(
                @SqlType("timestamp(p)") LongTimestamp timestamp,
                @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND) long interval)
        {
            return TimestampPlusIntervalDayToSecond.add(timestamp, -interval);
        }
    }

    @ScalarOperator(SUBTRACT)
    public static final class TimestampMinusTimestamp
    {
        @LiteralParameters("p")
        @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND)
        public static long subtract(
                @SqlType("timestamp(p)") long left,
                @SqlType("timestamp(p)") long right)
        {
            long interval = left - right;

            interval = round(interval, 3);
            interval = rescale(interval, MAX_SHORT_PRECISION, 3);

            return interval;
        }

        @LiteralParameters("p")
        @SqlType(StandardTypes.INTERVAL_DAY_TO_SECOND)
        public static long subtract(
                @SqlType("timestamp(p)") LongTimestamp left,
                @SqlType("timestamp(p)") LongTimestamp right)
        {
            return subtract(left.getEpochMicros(), right.getEpochMicros());
        }
    }
}
