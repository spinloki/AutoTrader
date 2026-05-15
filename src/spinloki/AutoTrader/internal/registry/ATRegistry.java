package spinloki.AutoTrader.internal.registry;

import com.fs.starfarer.api.Global;
import spinloki.AutoTrader.internal.config.ATConfig;

import java.util.Map;

/**
 * Accessor for the per-save {@link ATConfig} stored in sector persistent data.
 * Auto-creates the config on first access.
 */
public final class ATRegistry {

    public static final String KEY = "spinloki_autotrader.config";

    private ATRegistry() {}

    /** Returns the config, creating it if missing. Safe to call any time after sector init. */
    public static ATConfig get() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object raw = data.get(KEY);
        if (raw instanceof ATConfig) {
            return (ATConfig) raw;
        }
        ATConfig cfg = new ATConfig();
        data.put(KEY, cfg);
        return cfg;
    }
}
