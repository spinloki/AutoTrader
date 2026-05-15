package spinloki.AutoTrader;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import org.apache.log4j.Logger;
import spinloki.AutoTrader.internal.intel.ATConfigIntel;
import spinloki.AutoTrader.internal.listener.ATMarketListener;

public class AutoTrader extends BaseModPlugin {

    private static final Logger log = Logger.getLogger(AutoTrader.class);

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);
        // Re-attach our listener — remove first so reloads don't duplicate it.
        Global.getSector().getListenerManager().removeListenerOfClass(ATMarketListener.class);
        Global.getSector().getListenerManager().addListener(new ATMarketListener());

        // Ensure the config intel exists (singleton).
        IntelManagerAPI im = Global.getSector().getIntelManager();
        if (im.getFirstIntel(ATConfigIntel.class) == null) {
            im.addIntel(new ATConfigIntel(), /*hidden=*/true);
        }
        log.info("AutoTrader: market listener attached, intel ensured.");
    }
}


