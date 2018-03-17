package cz.siret.prank.utils;

import groovy.time.TimeCategory;
import groovy.time.TimeDuration;

import java.util.Date;

public class ATimer {

    private long start;

    public ATimer() {
        restart();
    }

    public static ATimer startTimer() {
        return new ATimer();
    }

    public void restart() {
        this.start = System.nanoTime();
    }

    public long getTime() {
        return (System.nanoTime() - start) / 1000000;
    }

    public long getTimeSec() {
        return getTime() / 1000;
    }

    public double getMinutes() {
        return getTimeSec() / 60;
    }

    public String getFormatted() {
        TimeDuration duration = TimeCategory.minus(new Date(), new Date(start/1000000));
        return duration.toString();
    }

    public String toString() {
        return getFormatted();
    }

}
