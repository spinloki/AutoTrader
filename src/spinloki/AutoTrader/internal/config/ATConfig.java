package spinloki.AutoTrader.internal.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Root persistent config object. Stored in sector persistent data under
 * {@link spinloki.AutoTrader.internal.registry.ATRegistry#KEY}.
 *
 * MUST be a named static class — never replace with an inner/anonymous class
 * (XStream will null it on save load).
 */
public class ATConfig {

    /** Per-weapon rules, keyed by weapon spec id. */
    public Map<String, ATItemRule> weapons = new LinkedHashMap<>();

    /** Per-fighter-LPC rules, keyed by wing id. */
    public Map<String, ATItemRule> fighters = new LinkedHashMap<>();

    /** If true, auto-buy any hullmod the player faction hasn't learned yet. */
    public boolean buyUnknownHullmods = false;

    /** If true, after buying a hullmod also add it to the player faction's known list. */
    public boolean learnHullmodsOnBuy = false;

    /** Hullmod ids to never auto-buy (override for buyUnknownHullmods). */
    public Set<String> hullmodBlacklist = new LinkedHashSet<>();

    // ---- Global toggles (previously in LunaSettings) ---------------------

    /** Master switch. When false, no automatic trades happen. */
    public boolean enabled = true;

    /** Route sells through the black market instead of the open market. */
    public boolean sellThroughBlack = false;

    /** Additionally scan the black market for unknown hullmods to buy. */
    public boolean buyHullmodsFromBlack = false;

    /** Never spend credits below this floor. */
    public int creditFloor = 0;
}
