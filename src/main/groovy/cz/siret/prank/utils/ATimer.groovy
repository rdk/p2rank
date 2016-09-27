package cz.siret.prank.utils

import groovy.time.TimeCategory
import groovy.time.TimeDuration

class ATimer {

    long start

    ATimer() {
        restart()
    }

    static ATimer start() {
        return new ATimer()
    }

    void restart() {
        this.start = System.currentTimeMillis()
    }

    long getTime() {
        return System.currentTimeMillis() - start
    }

    long getTimeSec() {
        return getTime()/1000
    }

    double getMinutes() {
        return time/(1000*60)
    }

    String getFormatted() {
        TimeDuration duration = TimeCategory.minus(new Date(), new Date(start))
        return duration.toString()
    }

    String toString() {
        return getFormatted()
    }

}
