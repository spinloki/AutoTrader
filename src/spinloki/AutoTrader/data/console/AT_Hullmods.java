package spinloki.AutoTrader.data.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.registry.ATRegistry;

/**
 * Usage: AT_Hullmods &lt;buyUnknown&gt; &lt;learnOnBuy&gt;
 * Each arg is "on"/"off" (or true/false). Toggles the global hullmod auto-buy behaviour.
 */
public class AT_Hullmods implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isCampaignAccessible()) return CommandResult.WRONG_CONTEXT;
        String[] parts = args.trim().split("\\s+");
        if (parts.length != 2) return CommandResult.BAD_SYNTAX;
        Boolean buy   = parseBool(parts[0]);
        Boolean learn = parseBool(parts[1]);
        if (buy == null || learn == null) return CommandResult.BAD_SYNTAX;

        ATConfig cfg = ATRegistry.get();
        cfg.buyUnknownHullmods = buy;
        cfg.learnHullmodsOnBuy = learn;
        Console.showMessage("Hullmods: buyUnknown=" + buy + " learnOnBuy=" + learn);
        return CommandResult.SUCCESS;
    }

    private static Boolean parseBool(String s) {
        s = s.toLowerCase();
        if (s.equals("on") || s.equals("true") || s.equals("1") || s.equals("yes")) return true;
        if (s.equals("off") || s.equals("false") || s.equals("0") || s.equals("no")) return false;
        return null;
    }
}
