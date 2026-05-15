package spinloki.AutoTrader.data.console;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.Global;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import spinloki.AutoTrader.internal.trade.ATTradeEngine;

/**
 * Usage: AT_Run — force a trade pass against the market the player is currently docked at.
 */
public class AT_Run implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isCampaignAccessible()) return CommandResult.WRONG_CONTEXT;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        MarketAPI market = null;
        if (fleet != null && fleet.getInteractionTarget() != null) {
            market = fleet.getInteractionTarget().getMarket();
        }
        if (market == null) {
            Console.showMessage("AutoTrader: not docked at a market.");
            return CommandResult.WRONG_CONTEXT;
        }
        String result = ATTradeEngine.run(market);
        Console.showMessage(result == null ? "AutoTrader: nothing to do." : result);
        return CommandResult.SUCCESS;
    }
}
