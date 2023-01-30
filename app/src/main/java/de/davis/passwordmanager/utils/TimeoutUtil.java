package de.davis.passwordmanager.utils;

public class TimeoutUtil {

    private long lastTime;
    private long diff;

    private long currentTimeMillis;

    public void initiateDelay() {
        currentTimeMillis = System.currentTimeMillis();
        if(lastTime == 0)
            lastTime = currentTimeMillis;
        diff = currentTimeMillis - lastTime;
    }

    public boolean delayMet(long timeout){
        boolean met = diff >= timeout;

        if(met)
            lastTime = currentTimeMillis;

        return met;
    }

    public boolean hasDelayMet(long timeout){
        long currentTimeMillis = System.currentTimeMillis();
        boolean met = currentTimeMillis - lastTime >= timeout;
        if(met)
            lastTime = currentTimeMillis;

        return met;
    }
}
