// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.time;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Comparator.*;


/**
 * A date and time. Both date and time come from reading the GC log. In cases where
 * the GC log has no date or time stamp, a DateTimeStamp is synthesized from the information
 * available in the log (event durations, for example).
 * <p>
 * Instance of DateTimeStamp are created by the parser. The constructors match what might be
 * found for dates and time stamps in a GC log file.
 */

public class DateTimeStamp implements Comparable<DateTimeStamp> {
    // Represents the time from Epoch
    // In the case where we have timestamps, the epoch is start of JVM
    // In the case where we only have date stamps, the epoch is 1970:01:01:00:00:00.000::UTC+0
    // All calculations in GCToolKit make use of the double, timeStamp.
    // Calculations are based on an startup Epoch of 0.000 seconds. This isn't always the case and
    // certainly isn't the case when only date stamp is present. In these cases, start time is estimated.
    // This is surprisingly difficult to do thus use of timestamp is highly recommended.

    // Requirements
    // Timestamp can never be less than 0
    //      - use NaN to say it's not set
    private final ZonedDateTime dateTime;
    private final double timeStamp;
    public static final Comparator<DateTimeStamp> comparator = getComparator();

    // For some reason, ISO_DATE_TIME doesn't like that time-zone is -0100. It wants -01:00.
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static ZonedDateTime dateFromString(String iso8601DateTime) {
        if (iso8601DateTime != null) {
            TemporalAccessor temporalAccessor = formatter.parse(iso8601DateTime);
            return ZonedDateTime.from(temporalAccessor);
        }
        return null;
    }

    private static double ageFromString(String doubleFormat) {
        if ( doubleFormat == null) return -1.0d;
        return Double.parseDouble(doubleFormat.replace(",","."));
    }

    // Patterns needed to support conversion of a log line to a DateTimeStamp

    private static final String DECIMAL_POINT = "(?:\\.|,)";
    private static final String INTEGER = "\\d+";
    private static final String DATE = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[\\+|\\-]\\d{4}";
    private static final String TIME = INTEGER + DECIMAL_POINT + "\\d{3}";

    // Unified Tokens
    private static final String DATE_TAG = "\\[" + DATE + "\\]";
    private static final String UPTIME_TAG = "\\[(" + TIME + ")s\\]";

    // Pre-unified tokens
    private static final String TIMESTAMP = "(" + TIME + "): ";
    private static final String DATE_STAMP = "(" + DATE + "): ";
    private static final String DATE_TIMESTAMP = "^(?:" + DATE_STAMP + ")?" + TIMESTAMP;

    //  2017-09-07T09:00:12.795+0200: 0.716:
    private static final Pattern PREUNIFIED_DATE_TIMESTAMP = Pattern.compile(DATE_TIMESTAMP);
    // JEP 158 has ISO-8601 time and uptime in seconds and milliseconds as the first two decorators.
    private static final Pattern UNIFIED_DATE_TIMESTAMP = Pattern.compile("^(" + DATE_TAG + ")?(" + UPTIME_TAG + ")?");
    private static final DateTimeStamp EMPTY_DATE = new DateTimeStamp(-1.0d);

    public static DateTimeStamp fromGCLogLine(String line) {
        Matcher matcher;
        int captureGroup = 2;
        if ( line.startsWith("[")) {
            matcher = UNIFIED_DATE_TIMESTAMP.matcher(line);
            captureGroup = 3;
        } else
            matcher = PREUNIFIED_DATE_TIMESTAMP.matcher(line);

        if ( matcher.find())
            return new DateTimeStamp(dateFromString(matcher.group(1)), ageFromString(matcher.group(captureGroup)));
        else
            return EMPTY_DATE;
    }

    /**
     * Create a DateTimeStamp by parsing an ISO 8601 date/time string.
     * @param iso8601DateTime A String in ISO 8601 format.
     */
    public DateTimeStamp(String iso8601DateTime) {
        this(dateFromString(iso8601DateTime));
    }

    /**
     * Create a DateTimeStamp from a ZonedDateTime.
     * @param dateTime A ZonedDateTime
     */
    public DateTimeStamp(ZonedDateTime dateTime) {
        this(dateTime,Double.NaN);
    }

    /**
     * Create a DateTimeStamp from an IOS 8601 date/time string and
     * a time stamp. The time stamp represents decimal seconds.
     * @param iso8601DateTime A String in ISO 8601 format.
     * @param timeStamp A time stamp in decimal seconds.
     */
    public DateTimeStamp(String iso8601DateTime, double timeStamp) {
        this(dateFromString(iso8601DateTime), timeStamp);
    }

    /**
     * Create a DateTimeStamp from a time stamp.
     * @param timeStamp A time stamp in decimal seconds.
     */
    public DateTimeStamp(double timeStamp) {
        this((ZonedDateTime) null,timeStamp);
    }

    /**
     * Create a DateTimeStamp from a ZonedDateTime and a timestamp.
     * All other constructors end up here. If timeStamp is
     * {@code NaN} or less than zero, then the time stamp is extracted
     * from the ZonedDateTime.
     * @param dateTime A ZonedDateTime, which may be {@code null}.
     * @param timeStamp A time stamp in decimal seconds,
     *                  which should be greater than or equal to zero.
     */
    public DateTimeStamp(ZonedDateTime dateTime, double timeStamp) {
        this.dateTime = dateTime;
        //NaN is our agreed upon not set but less than 0 makes no sense either.
        if ( dateTime != null && (Double.isNaN(timeStamp) || timeStamp < 0.0d)) {
            //Converts the long nano value to a decimal nano value. The timeStamp value is in decimal seconds - <seconds>.<millis>
            this.timeStamp = dateTime.toEpochSecond() + dateTime.getNano() / 1_000_000_000d;
        }
        else
            this.timeStamp = getTimestampValue(timeStamp);
    }

    /**
     * Return the time stamp value. Allows a consistent time stamp be available to all calculations.
     * @return The time stamp value, in decimal seconds.
     */
    public double getTimeStamp() {
        return timeStamp;
    }

    /**
     * Return the date stamp.
     * @return The date stamp, which may be {@code null}
     */
    public ZonedDateTime getDateTime() {
        return dateTime;
    }

    /**
     * Return {@code true} if the date stamp is not {@code null}.
     * @return {@code true} if the the date stamp is not {@code null}.
     */
    public boolean hasDateStamp() {
        return getDateTime() != null;
    }

    @Override
    public boolean equals(Object obj) {
        if ( obj instanceof  DateTimeStamp) {
            DateTimeStamp other = (DateTimeStamp)obj;
            boolean eq = getDateTime() == null ? other.getDateTime() == null : getDateTime().equals(other.getDateTime());
            return eq && getTimeStamp() == other.getTimeStamp();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDateTime(), getTimeStamp());
    }

    @Override
    public String toString() {
        if (dateTime == null)
            return "@" + String.format(Locale.US, "%.3f", timeStamp);
        else
            return dateTime + "@" + String.format(Locale.US, "%.3f", timeStamp);
    }

    /**
     * Return {@code true} if this time stamp is less than the other.
     * @param other The other time stamp, in decimal seconds.
     * @return {@code true} if this time stamp is less than the other.
     */
    public boolean before(double other) {
        return getTimeStamp() < getTimestampValue(other);
    }

    /**
     * Return {@code true} if this time stamp is greater than the other.
     * @param other The other time stamp, in decimal seconds.
     * @return {@code true} if this time stamp is greater than the other.
     */
    public boolean after(double other) {
        return getTimeStamp() > getTimestampValue(other);
    }

    /**
     * Return {@code true} if this DateTimeStamp comes before the other.
     * @param other The other DateTimeStamp.
     * @return {@code true} if this time stamp is less than the other.
     */
    public boolean before(DateTimeStamp other) {
        return !after(other);
    }

    /**
     * Return {@code true} if this DateTimeStamp comes after the other.
     * @param other The other DateTimeStamp.
     * @return {@code true} if this time stamp is less than the other.
     */
    public boolean after(DateTimeStamp other) {
        if ((other.hasDateStamp()) && (this.hasDateStamp())) {
            int comparison = other.compare(getDateTime());
            if (comparison < 0)
                return true;
            else if (comparison > 0)
                return false;
        }
        return after(other.getTimeStamp());
    }

    /**
     * Return {@code 1} if this date is after than the other,
     * {@code -1} if this date is before the other,
     * or {@code 0} if this date is the same as the other.
     * @param otherDate The other date.
     * @return {@code 1}, {@code 0}, {@code -1} if this date is
     * after, the same as, or before the other.
     */
    public int compare(ZonedDateTime otherDate) {
        if (hasDateStamp() && otherDate != null) {
            if (getDateTime().isAfter(otherDate)) return 1;
            if (getDateTime().isBefore(otherDate)) return -1;
            return 0;
        } else if (hasDateStamp()) {
            return 1;
        } else {
            return -1;
        }
    }

    /**
     * Return a new {@code DateTimeStamp} resulting from adding the
     * decimal second offset to this.
     * @param offsetInDecimalSeconds An offset, in decimal seconds.
     * @return A new {@code DateTimeStamp}, {@code offsetInDecimalSeconds} from this.
     */
    public DateTimeStamp add(double offsetInDecimalSeconds) {
        DateTimeStamp now;
        // if passed value is NAN then consider offset seconds as 0.0d
        offsetInDecimalSeconds = getTimestampValue(offsetInDecimalSeconds);
        if (dateTime != null) {
            int seconds = (int) offsetInDecimalSeconds;
            long nanos = (long) ((offsetInDecimalSeconds % 1) * 1_000_000_000L);
            now = new DateTimeStamp(dateTime.plusSeconds(seconds).plusNanos(nanos), timeStamp + offsetInDecimalSeconds);
        } else
            now = new DateTimeStamp(getTimeStamp() + offsetInDecimalSeconds);

        return now;
    }

    private double getTimestampValue(double timestamp) {
        return Double.isNaN(timestamp)? 0.0d: timestamp;
    }

    /**
     * Return a new {@code DateTimeStamp} resulting from subtracting the
     * decimal second offset from this.
     * @param offsetInDecimalSeconds An offset, in decimal seconds.
     * @return A new {@code DateTimeStamp}, {@code offsetInDecimalSeconds} from this.
     */
    public DateTimeStamp minus(double offsetInDecimalSeconds) {
        return add(-offsetInDecimalSeconds);
    }

    /**
     * Return the difference between this time stamp, and the time stamp of
     * the other DateTimeStamp.
     * @param dateTimeStamp The other {@code DateTimeStamp}
     * @return The difference between this time stamp, and the time stamp
     * of the other DateTimeStamp.
     */
    public double minus(DateTimeStamp dateTimeStamp) {
        return timeStamp - dateTimeStamp.timeStamp;
    }

    /**
     * Return the difference between time stamps, converted to minutes.
     * This is a convenience method for {@code this.minus(dateTimeStamp) / 60.0}.
     * @param dateTimeStamp The other {@code DateTimeStamp}
     * @return The difference between time stamps, converted to minutes.
     */
    public double timeSpanInMinutes(DateTimeStamp dateTimeStamp) {
        return this.minus(dateTimeStamp) / 60.0d;
    }

    /**
     * It will compare date time first, if both are equals then compare timestamp value,
     * For Null date time  considered to be last entry.
     * @param dateTimeStamp - other object to compared
     * @return  a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object
     */
    @Override
    public int compareTo(DateTimeStamp dateTimeStamp) {

        return  comparator.compare(this,dateTimeStamp);
    }

    private static  Comparator<DateTimeStamp> getComparator(){
        // compare with dateTime field, if null then it will go to last
        return nullsLast((o1, o2) -> {
            Comparator<DateTimeStamp> dateTimeStampComparator = compareDateTimeStamp(o1, o2);
            return dateTimeStampComparator.compare(o1,o2);
        });
    }

    private static Comparator<DateTimeStamp> compareDateTimeStamp(DateTimeStamp o1, DateTimeStamp o2) {
        return comparingDouble(DateTimeStamp::getTimeStamp)
                .thenComparing(DateTimeStamp::getDateTime,
                        (dtsA, dtsB) -> {
                            if (dtsA == null || dtsB == null) return 0;
                            else return dtsA.compareTo(dtsB);
                        });
    }

    public double toEpochInMillis() {
        if ( dateTime != null) {
            return (double)(dateTime.toEpochSecond() * 1000) + (((double)dateTime.getNano()) / 1000000.0d);
        } // todo: a reasonable response here might be????
        return -1.0d;
    }

    public boolean hasTimeStamp() {
        return ! (timeStamp == -1.0d || Double.isNaN(timeStamp));
    }

    public boolean hasDateTime() {
        return dateTime != null;
    }
}
