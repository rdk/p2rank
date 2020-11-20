package cz.siret.prank.utils;

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

    /**
     * @return elapsed time in milliseconds
     */
    public long getTime() {
        return (System.nanoTime() - start) / 1000000;
    }

    public Duration getDuration() {
        return new Duration(getTime());
    }

    public long getTimeSec() {
        return getTime() / 1000;
    }

    public double getMinutes() {
        return (double)getTimeSec() / 60;
    }

    public String getFormatted() {
        return getDuration().toString();
    }

    public String toString() {
        return getFormatted();
    }

//===============================================================================================//

    public static String formatTime(long ms) {
        return new Duration(ms).toString();
    }

    public static class Duration {
        private long millis;
        private long seconds;
        private long minutes;
        private long hours;

        public Duration(long ms) {
            millis = ms;
            seconds = millis / 1000;
            minutes = seconds / 60;
            hours = minutes / 60;

            minutes = minutes % 60;
            seconds = seconds % 60;
            millis = millis % 1000;
        }

        public long getMillis() {
            return millis;
        }

        public long getSeconds() {
            return seconds;
        }

        public long getMinutes() {
            return minutes;
        }

        public long getHours() {
            return hours;
        }

        @Override
        public String toString() {
            return String.format("%d hours %d minutes %d.%03d seconds", hours, minutes, seconds, millis); 
        }
    }

}
