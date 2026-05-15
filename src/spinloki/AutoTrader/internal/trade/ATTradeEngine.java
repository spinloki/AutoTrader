package spinloki.AutoTrader.internal.trade;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.apache.log4j.Logger;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.config.ATItemRule;
import spinloki.AutoTrader.internal.registry.ATRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes the configured auto-trade rules against a market's submarkets.
 * All toggles live on {@link ATConfig}.
 */
public final class ATTradeEngine {

    private static final Logger log = Logger.getLogger(ATTradeEngine.class);

    private static final String MODSPEC = "modspec";

    private ATTradeEngine() {}

    /** Run all enabled trade rules against the given market. Returns a human-readable summary or null if nothing happened. */
    public static String run(MarketAPI market) {
        if (market == null) return null;
        ATConfig cfg = ATRegistry.get();
        if (!cfg.enabled) return null;
        CargoAPI playerCargo = Global.getSector().getPlayerFleet().getCargo();
        if (playerCargo == null) return null;

        SubmarketAPI sellTo  = pickSellSubmarket(market, cfg);
        SubmarketAPI openBuy = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        SubmarketAPI blackBuy = market.getSubmarket(Submarkets.SUBMARKET_BLACK);

        Summary s = new Summary();

        // ---- Sell phase ---------------------------------------------------
        if (sellTo != null && sellTo.getCargo() != null) {
            sellWeapons(playerCargo, sellTo, cfg, s);
            sellFighters(playerCargo, sellTo, cfg, s);
        }

        // ---- Buy phase ----------------------------------------------------
        if (openBuy != null && openBuy.getCargo() != null) {
            buyWeapons(playerCargo, openBuy, cfg, s);
            buyFighters(playerCargo, openBuy, cfg, s);
            buyHullmods(playerCargo, openBuy, cfg, s);
        }
        if (cfg.buyHullmodsFromBlack && blackBuy != null && blackBuy.getCargo() != null) {
            buyHullmods(playerCargo, blackBuy, cfg, s);
        }

        return s.isEmpty() ? null : s.format(market);
    }

    private static SubmarketAPI pickSellSubmarket(MarketAPI market, ATConfig cfg) {
        if (cfg.sellThroughBlack) {
            SubmarketAPI b = market.getSubmarket(Submarkets.SUBMARKET_BLACK);
            if (b != null) return b;
        }
        return market.getSubmarket(Submarkets.SUBMARKET_OPEN);
    }

    // ====================================================================
    // Weapons
    // ====================================================================

    private static void sellWeapons(CargoAPI player, SubmarketAPI sm, ATConfig cfg, Summary s) {
        for (Map.Entry<String, ATItemRule> e : cfg.weapons.entrySet()) {
            String id = e.getKey();
            ATItemRule r = e.getValue();
            if (r.sellAbove < 0) continue;
            int have = player.getNumWeapons(id);
            if (have <= r.sellAbove) continue;
            int qty = have - r.sellAbove;
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            if (spec == null) continue;
            int credits = sellPrice(spec.getBaseValue(), sm, qty);
            player.removeWeapons(id, qty);
            sm.getCargo().addWeapons(id, qty);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
            s.addSell(spec.getWeaponName(), qty, credits);
        }
    }

    private static void buyWeapons(CargoAPI player, SubmarketAPI sm, ATConfig cfg, Summary s) {
        CargoAPI smc = sm.getCargo();
        for (Map.Entry<String, ATItemRule> e : cfg.weapons.entrySet()) {
            String id = e.getKey();
            ATItemRule r = e.getValue();
            if (r.buyBelow < 0) continue;
            int have = player.getNumWeapons(id);
            if (have >= r.buyBelow) continue;
            int avail = smc.getNumWeapons(id);
            if (avail <= 0) continue;
            int want = r.buyBelow - have;
            int qty = Math.min(want, avail);
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            if (spec == null) continue;
            qty = capByCredits(spec.getBaseValue(), sm, qty);
            if (qty <= 0) continue;
            int cost = buyPrice(spec.getBaseValue(), sm, qty);
            smc.removeWeapons(id, qty);
            player.addWeapons(id, qty);
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            s.addBuy(spec.getWeaponName(), qty, cost);
        }
    }

    // ====================================================================
    // Fighter LPCs
    // ====================================================================

    private static void sellFighters(CargoAPI player, SubmarketAPI sm, ATConfig cfg, Summary s) {
        for (Map.Entry<String, ATItemRule> e : cfg.fighters.entrySet()) {
            String id = e.getKey();
            ATItemRule r = e.getValue();
            if (r.sellAbove < 0) continue;
            int have = player.getNumFighters(id);
            if (have <= r.sellAbove) continue;
            int qty = have - r.sellAbove;
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec == null) continue;
            int credits = sellPrice(spec.getBaseValue(), sm, qty);
            player.removeFighters(id, qty);
            sm.getCargo().addFighters(id, qty);
            Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
            s.addSell(spec.getWingName() + " LPC", qty, credits);
        }
    }

    private static void buyFighters(CargoAPI player, SubmarketAPI sm, ATConfig cfg, Summary s) {
        CargoAPI smc = sm.getCargo();
        for (Map.Entry<String, ATItemRule> e : cfg.fighters.entrySet()) {
            String id = e.getKey();
            ATItemRule r = e.getValue();
            if (r.buyBelow < 0) continue;
            int have = player.getNumFighters(id);
            if (have >= r.buyBelow) continue;
            int avail = smc.getNumFighters(id);
            if (avail <= 0) continue;
            int want = r.buyBelow - have;
            int qty = Math.min(want, avail);
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
            if (spec == null) continue;
            qty = capByCredits(spec.getBaseValue(), sm, qty);
            if (qty <= 0) continue;
            int cost = buyPrice(spec.getBaseValue(), sm, qty);
            smc.removeFighters(id, qty);
            player.addFighters(id, qty);
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            s.addBuy(spec.getWingName() + " LPC", qty, cost);
        }
    }

    // ====================================================================
    // Hullmods (buy only — selling hullmods is rarely useful)
    // ====================================================================

    private static void buyHullmods(CargoAPI player, SubmarketAPI sm, ATConfig cfg, Summary s) {
        if (!cfg.buyUnknownHullmods) return;
        // Snapshot stack list — we mutate the submarket cargo as we go.
        List<CargoStackAPI> stacks = new ArrayList<>(sm.getCargo().getStacksCopy());
        for (CargoStackAPI stack : stacks) {
            if (!stack.isSpecialStack()) continue;
            if (stack.getSpecialDataIfSpecial() == null) continue;
            if (!MODSPEC.equals(stack.getSpecialDataIfSpecial().getId())) continue;
            HullModSpecAPI hm = stack.getHullModSpecIfHullMod();
            if (hm == null) continue;
            String hmId = hm.getId();
            if (Global.getSector().getCharacterData().knowsHullMod(hmId)) continue;
            if (cfg.hullmodBlacklist.contains(hmId)) continue;

            int avail = (int) stack.getSize();
            if (avail <= 0) continue;
            // Only need one copy to learn / stash.
            int qty = 1;
            qty = capByCredits(hm.getBaseValue(), sm, qty);
            if (qty <= 0) continue;
            int cost = buyPrice(hm.getBaseValue(), sm, qty);
            stack.subtract(qty);
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            s.addBuy(hm.getDisplayName(), qty, cost);

            if (cfg.learnHullmodsOnBuy) {
                // Consume the modspec exactly like a manual right-click would:
                // play the acquire sound, add the mod to the player's character data,
                // and surface the same "Acquired hull mod" notification. The modspec
                // is consumed (not added to cargo).
                Global.getSoundPlayer().playUISound("ui_acquired_hullmod", 1f, 1f);
                Global.getSector().getCharacterData().addHullMod(hmId);
                // getMessageDisplay() is suppressed while a campaign interaction
                // dialog is open (which is when we run now). Use the campaign
                // message log instead — it queues and surfaces reliably.
                Global.getSector().getCampaignUI().addMessage(
                        "Acquired hull mod: " + hm.getDisplayName());
                s.addLearned(hm.getDisplayName());
            } else {
                player.addHullmods(hmId, qty);
            }
        }
        sm.getCargo().removeEmptyStacks();
    }

    // ====================================================================
    // Pricing helpers
    // ====================================================================

    private static int sellPrice(float baseValue, SubmarketAPI sm, int qty) {
        return Math.max(0, (int) (baseValue * qty * (1f - sm.getTariff())));
    }

    private static int buyPrice(float baseValue, SubmarketAPI sm, int qty) {
        return Math.max(0, (int) (baseValue * qty * (1f + sm.getTariff())));
    }

    /** Clamp qty so the buy cost stays at or below (credits - floor). Returns 0 if even one unit can't be afforded. */
    private static int capByCredits(float baseValue, SubmarketAPI sm, int qty) {
        float perUnit = baseValue * (1f + sm.getTariff());
        if (perUnit <= 0) return qty;
        int floor = ATRegistry.get().creditFloor;
        int budget = (int) (Global.getSector().getPlayerFleet().getCargo().getCredits().get() - floor);
        if (budget <= 0) return 0;
        int affordable = (int) (budget / perUnit);
        return Math.min(qty, affordable);
    }

    // ====================================================================
    // Summary
    // ====================================================================

    private static final class Summary {
        final List<String> sold = new ArrayList<>();
        final List<String> bought = new ArrayList<>();
        final List<String> learned = new ArrayList<>();
        int totalSoldQty = 0;
        int totalBoughtQty = 0;
        int totalEarned = 0;
        int totalSpent  = 0;

        void addSell(String name, int qty, int credits) {
            String line = "Sold " + qty + "x " + name + " (+" + credits + " cr)";
            sold.add(line);
            totalSoldQty += qty;
            totalEarned += credits;
            Global.getSector().getCampaignUI().addMessage(line);
        }

        void addBuy(String name, int qty, int cost) {
            String line = "Bought " + qty + "x " + name + " (-" + cost + " cr)";
            bought.add(line);
            totalBoughtQty += qty;
            totalSpent += cost;
            Global.getSector().getCampaignUI().addMessage(line);
        }

        void addLearned(String name) {
            learned.add(name);
        }

        boolean isEmpty() {
            return sold.isEmpty() && bought.isEmpty() && learned.isEmpty();
        }

        String format(MarketAPI market) {
            StringBuilder sb = new StringBuilder();
            sb.append("AutoTrader @ ").append(market.getName()).append(":");
            if (!sold.isEmpty())    sb.append(" sold ").append(totalSoldQty).append(" (+").append(totalEarned).append(" cr)");
            if (!bought.isEmpty())  sb.append(" bought ").append(totalBoughtQty).append(" (-").append(totalSpent).append(" cr)");
            if (!learned.isEmpty()) sb.append(" learned ").append(learned.size()).append(" hullmod(s)");
            return sb.toString();
        }
    }
}
