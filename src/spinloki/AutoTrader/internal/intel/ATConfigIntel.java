package spinloki.AutoTrader.internal.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.registry.ATRegistry;
import spinloki.AutoTrader.internal.ui.ATConfigDialogPlugin;

import java.awt.Color;
import java.util.Set;

/**
 * Intel item that hosts the AutoTrader configuration screen. The right pane shows
 * a brief summary plus a Configure button which opens the full tabbed dialog.
 *
 * Named static class — fields stored in this object are serialized with the save.
 */
public class ATConfigIntel extends BaseIntelPlugin {

    private static final Object BTN_CONFIGURE = new Object();

    public ATConfigIntel() {
        setImportant(false);
    }

    @Override
    public String getIcon() {
        return Global.getSector().getPlayerFaction().getCrest();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new java.util.LinkedHashSet<>();
        tags.add(Tags.INTEL_TRADE);
        return tags;
    }

    @Override
    protected String getName() {
        return "AutoTrader";
    }

    @Override
    public String getSortString() {
        return "AutoTrader";
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return null;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getTitleColor(mode);
        info.addPara(getName(), tc, 0f);

        ATConfig cfg = ATRegistry.get();
        int rules = cfg.weapons.size() + cfg.fighters.size();
        Color hl = Misc.getHighlightColor();
        info.addPara("%s rule(s), enabled: %s", 3f, hl,
                Integer.toString(rules), cfg.enabled ? "yes" : "no");
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        ATConfig cfg = ATRegistry.get();
        Color hl = Misc.getHighlightColor();

        TooltipMakerAPI info = panel.createUIElement(width, height, true);
        info.addSectionHeading("AutoTrader", Alignment.MID, 0f);
        info.addSpacer(8f);

        info.addPara("Automated buying and selling of weapons, fighter LPCs, and hullmods at any market you dock at.",
                10f);

        info.addSpacer(12f);
        info.addPara("Master switch: %s", 0f, hl, cfg.enabled ? "ON" : "OFF");
        info.addPara("Sells routed through: %s", 0f, hl, cfg.sellThroughBlack ? "black market" : "open market");
        info.addPara("Hullmod auto-buy: %s%s", 0f, hl,
                cfg.buyUnknownHullmods ? "ON" : "OFF",
                cfg.buyUnknownHullmods && cfg.learnHullmodsOnBuy ? " (auto-learn)" : "");
        info.addPara("Credit floor: %s", 0f, hl, Misc.getDGSCredits(cfg.creditFloor));
        info.addSpacer(6f);
        info.addPara("Configured rules: %s weapons, %s fighters, %s blacklisted hullmods",
                0f, hl,
                Integer.toString(cfg.weapons.size()),
                Integer.toString(cfg.fighters.size()),
                Integer.toString(cfg.hullmodBlacklist.size()));

        info.addSpacer(16f);
        info.addButton("Configure...", BTN_CONFIGURE, 160f, 28f, 0f);

        panel.addUIElement(info).inTL(0f, 0f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BTN_CONFIGURE) {
            ui.showDialog(Global.getSector().getPlayerFleet(), new ATConfigDialogPlugin());
        }
    }
}
