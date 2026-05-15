package spinloki.AutoTrader.internal.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import org.apache.log4j.Logger;
import spinloki.AutoTrader.internal.trade.ATTradeEngine;

/**
 * Listens for market-open events and runs the trade engine once per visit.
 *
 * Registered via {@code Global.getSector().getListenerManager().addListener(...)}
 * on game load.
 *
 * <p>Named static class — required for XStream safety even though we never store
 * this in saved memory directly; the listener manager <em>does</em> persist us.
 */
public class ATMarketListener implements ColonyInteractionListener {

    private static final Logger log = Logger.getLogger(ATMarketListener.class);

    // Transient — reset each market visit.
    private transient String lastHandledMarketId = null;

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        // Fires when the planet/station interaction dialog first opens, before the
        // player has visited any submarket. Run the trade engine here so the user
        // sees the post-trade state when they click into the market.
        runOnce(market);
    }

    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        // Fallback: if reportPlayerOpenedMarket didn't fire for some reason
        // (e.g. cargo opened via raw rules), still try here. runOnce() dedupes.
        runOnce(market);
    }

    private void runOnce(MarketAPI market) {
        if (market == null) return;
        if (market.getId() != null && market.getId().equals(lastHandledMarketId)) return;
        lastHandledMarketId = market.getId();

        // Force-populate submarket cargo so the trade engine can read realistic stocks.
        // Vanilla normally only calls this when the player actually opens that submarket.
        primeSubmarket(market, Submarkets.SUBMARKET_OPEN);
        primeSubmarket(market, Submarkets.SUBMARKET_BLACK);

        try {
            String result = ATTradeEngine.run(market);
            if (result != null) {
                Global.getSector().getCampaignUI().addMessage(result);
            }
        } catch (Exception e) {
            log.error("AutoTrader trade engine failed at " + market.getId(), e);
        }
    }

    private static void primeSubmarket(MarketAPI market, String submarketId) {
        SubmarketAPI sm = market.getSubmarket(submarketId);
        if (sm == null || sm.getPlugin() == null) return;
        try {
            sm.getPlugin().updateCargoPrePlayerInteraction();
        } catch (Exception e) {
            log.warn("AutoTrader: updateCargoPrePlayerInteraction failed for "
                    + market.getId() + "/" + submarketId, e);
        }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        lastHandledMarketId = null;
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // No-op.
    }
}
