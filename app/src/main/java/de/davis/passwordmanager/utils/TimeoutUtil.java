package de.davis.passwordmanager.utils;

public class TimeoutUtil {

    private long lastTime;

    public boolean hasDelayMet(int timeout){
        long currentTimeMillis = System.currentTimeMillis();
        boolean met = currentTimeMillis - lastTime >= timeout;
        if(met)
            lastTime = currentTimeMillis;

        return met;
    }
}
