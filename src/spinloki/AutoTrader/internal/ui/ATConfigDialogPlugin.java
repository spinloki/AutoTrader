package spinloki.AutoTrader.internal.ui;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

import java.util.Map;

/**
 * Bare-bones InteractionDialogPlugin that immediately opens the AutoTrader
 * config screen as a CustomDialog. Used by {@link spinloki.AutoTrader.internal.intel.ATConfigIntel}.
 */
public class ATConfigDialogPlugin implements InteractionDialogPlugin {

    @Override
    public void init(InteractionDialogAPI dialog) {
        dialog.setPromptText("AutoTrader");
        dialog.showCustomDialog(1100f, 700f, new ATConfigDelegate(dialog));
    }

    @Override public void optionSelected(String optionText, Object optionData) {}
    @Override public void optionMousedOver(String optionText, Object optionData) {}
    @Override public void advance(float amount) {}
    @Override public void backFromEngagement(EngagementResultAPI battleResult) {}
    @Override public Object getContext() { return null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
