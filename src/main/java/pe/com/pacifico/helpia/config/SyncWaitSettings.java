package pe.com.pacifico.helpia.config;

import pe.com.pacifico.helpia.common.Env;

/** Espera corta del starter: "polling ≈3 intentos x 2s" del diagrama. */
public record SyncWaitSettings(int polls, long intervalMillis) {

    public static SyncWaitSettings load(Env env) {
        return new SyncWaitSettings(
                Math.max(0, env.getInt("SYNC_WAIT_POLLS", 3)),
                Math.max(100, env.getInt("SYNC_WAIT_INTERVAL_MS", 2000)));
    }
}
