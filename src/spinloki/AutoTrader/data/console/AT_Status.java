package spinloki.AutoTrader.data.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.config.ATItemRule;
import spinloki.AutoTrader.internal.registry.ATRegistry;

import java.util.Map;

public class AT_Status implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isCampaignAccessible()) {
            Console.showMessage("AutoTrader: campaign-only.");
            return CommandResult.WRONG_CONTEXT;
        }
        ATConfig cfg = ATRegistry.get();
        Console.showMessage("=== AutoTrader status ===");
        Console.showMessage("enabled=" + cfg.enabled
                + " sellThroughBlack=" + cfg.sellThroughBlack
                + " buyHullmodsFromBlack=" + cfg.buyHullmodsFromBlack
                + " creditFloor=" + cfg.creditFloor);
        Console.showMessage("Hullmods: buyUnknown=" + cfg.buyUnknownHullmods
                + " learnOnBuy=" + cfg.learnHullmodsOnBuy
                + " blacklist=" + cfg.hullmodBlacklist.size());
        printRules("Weapons", cfg.weapons);
        printRules("Fighters", cfg.fighters);
        return CommandResult.SUCCESS;
    }

    private static void printRules(String label, Map<String, ATItemRule> rules) {
        if (rules.isEmpty()) {
            Console.showMessage(label + ": (none)");
            return;
        }
        Console.showMessage(label + ": " + rules.size() + " rule(s)");
        for (Map.Entry<String, ATItemRule> e : rules.entrySet()) {
            ATItemRule r = e.getValue();
            Console.showMessage("  " + e.getKey()
                    + " sellAbove=" + (r.sellAbove < 0 ? "-" : r.sellAbove)
                    + " buyBelow=" + (r.buyBelow < 0 ? "-" : r.buyBelow));
        }
    }
}
