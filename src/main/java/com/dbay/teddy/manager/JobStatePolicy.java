package com.dbay.teddy.manager;

import com.dbay.teddy.entity.App;
import com.dbay.teddy.entity.Job;

import java.util.Locale;

/**
 * Keeps YARN lifecycle semantics in one place so transient states, successful
 * completion and manual kills cannot accidentally trigger failure handling.
 */
public final class JobStatePolicy {

    private JobStatePolicy() {
    }

    public static String persistedState(App snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("YARN snapshot must not be null");
        }

        String state = normalize(snapshot.getState());
        if (state == null) {
            throw new IllegalArgumentException("YARN snapshot state must not be blank");
        }

        if ("FINISHED".equals(state)) {
            String finalStatus = normalize(snapshot.getFinalStatus());
            if ("FAILED".equals(finalStatus) || "KILLED".equals(finalStatus)) {
                return finalStatus;
            }
        }
        return state;
    }

    public static boolean shouldAutoRestart(Job job) {
        return job != null
                && "FAILED".equals(normalize(job.getState()))
                && Integer.valueOf(1).equals(job.getRestart())
                && job.getRetries() != null
                && job.getRetries() > 0;
    }

    public static boolean shouldAlert(Job job) {
        return job != null
                && "FAILED".equals(normalize(job.getState()))
                && Integer.valueOf(1).equals(job.getSend());
    }

    /** 包内共享，避免各处再写一份状态归一化逻辑。 */
    static String normalize(String state) {
        if (state == null || state.trim().isEmpty()) {
            return null;
        }
        return state.trim().toUpperCase(Locale.ROOT);
    }
}
