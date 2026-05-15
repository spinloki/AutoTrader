package spinloki.AutoTrader.data.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.config.ATItemRule;
import spinloki.AutoTrader.internal.registry.ATRegistry;

/**
 * Usage: AT_Fighter &lt;wingId&gt; &lt;sellAbove&gt; &lt;buyBelow&gt;
 */
public class AT_Fighter implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isCampaignAccessible()) return CommandResult.WRONG_CONTEXT;
        String[] parts = args.trim().split("\\s+");
        if (parts.length != 3) return CommandResult.BAD_SYNTAX;
        if (!CommandUtils.isInteger(parts[1]) || !CommandUtils.isInteger(parts[2])) return CommandResult.BAD_SYNTAX;

        String id = parts[0];
        int sellAbove = Integer.parseInt(parts[1]);
        int buyBelow  = Integer.parseInt(parts[2]);

        FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(id);
        if (spec == null) {
            Console.showMessage("Unknown fighter wing id: " + id);
            return CommandResult.ERROR;
        }

        ATConfig cfg = ATRegistry.get();
        if (sellAbove < 0 && buyBelow < 0) {
            cfg.fighters.remove(id);
            Console.showMessage("Removed rule for " + spec.getWingName() + " (" + id + ").");
        } else {
            cfg.fighters.put(id, new ATItemRule(sellAbove, buyBelow));
            Console.showMessage("Set " + spec.getWingName() + " (" + id + "): "
                    + "sellAbove=" + (sellAbove < 0 ? "-" : sellAbove)
                    + " buyBelow=" + (buyBelow < 0 ? "-" : buyBelow));
        }
        return CommandResult.SUCCESS;
    }
}
