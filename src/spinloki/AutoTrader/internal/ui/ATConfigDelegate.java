package spinloki.AutoTrader.internal.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.util.Misc;
import spinloki.AutoTrader.internal.config.ATConfig;
import spinloki.AutoTrader.internal.config.ATItemRule;
import spinloki.AutoTrader.internal.registry.ATRegistry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tabbed in-game settings UI for AutoTrader.
 *
 * Hosted inside a vanilla custom dialog via {@link ATConfigDialogPlugin}.
 * All edits write straight to {@link ATConfig} in sector persistent data —
 * there is no Save/Cancel; closing the dialog just dismisses it.
 */
public class ATConfigDelegate extends BaseCustomDialogDelegate {

    private enum Tab { GENERAL, WEAPONS, FIGHTERS, HULLMODS }

    private static final float W = 1100f;
    private static final float H = 700f;
    private static final float TAB_BAR_H = 36f;
    private static final float TAB_W = 130f;
    private static final float SEARCH_W = 220f;
    private static final float ROW_FIELD_W = 110f;
    private static final float FILTER_BAR_H = 32f;

    // Intuitive coloring for the per-row threshold fields.
    private static final Color SELL_COLOR = new Color(220,  80,  80);   // red — selling off excess
    private static final Color BUY_COLOR  = new Color( 80, 200,  80);   // green — buying up to fill

    private static final Object TG_ENABLED = new Object();
    private static final Object TG_SELL_BLACK = new Object();
    private static final Object TG_BUY_UNKNOWN_HM = new Object();
    private static final Object TG_LEARN_HM = new Object();
    private static final Object TG_BUY_HM_BLACK = new Object();
    private static final Object TG_HELD_ONLY = new Object();
    private static final Object TG_DEFAULTS = new Object();
    private static final Object TG_CLEAR_RULES = new Object();
    private static final Object TG_SELECT_ALL = new Object();
    private static final Object TG_APPLY_TEMPLATE = new Object();
    private static final Object TG_CLEAR_SELECTION = new Object();

    private final InteractionDialogAPI dialog;
    private CustomPanelAPI root;
    private CustomPanelAPI bodyHost;
    private CustomPanelAPI actionBar;
    private float actionBarY;
    private UIComponentAPI bodyEl;

    private Tab currentTab = Tab.GENERAL;
    private TextFieldAPI searchField;
    private String lastSearch = "";

    // Weapon-tab filters (empty set = no filter on that axis).
    private final EnumSet<WeaponSize> sizeFilter = EnumSet.noneOf(WeaponSize.class);
    private final EnumSet<WeaponType> typeFilter = EnumSet.noneOf(WeaponType.class);
    private boolean heldOnly = false;

    private final Map<Tab, ButtonAPI> tabButtons = new EnumMap<>(Tab.class);

    private TextFieldAPI creditFloorField;
    private String lastCreditFloorText = "";

    // key = "W:<id>" or "F:<id>"
    private final Map<String, TextFieldAPI> sellFields = new LinkedHashMap<>();
    private final Map<String, TextFieldAPI> buyFields = new LinkedHashMap<>();
    private final Map<String, String> lastSell = new LinkedHashMap<>();
    private final Map<String, String> lastBuy = new LinkedHashMap<>();

    // Multi-select / template state. Selection persists across rebuilds and tab switches;
    // visibleKeys is the ordered list of rows the current tab actually rendered.
    private final Set<String> selectedKeys = new HashSet<>();
    private final List<String> visibleKeys = new ArrayList<>();
    private TextFieldAPI templateSellField;
    private TextFieldAPI templateBuyField;
    private String templateSellText = "";
    private String templateBuyText = "";

    private final Plugin plugin = new Plugin();

    public ATConfigDelegate(InteractionDialogAPI dialog) {
        this.dialog = dialog;
    }

    // -- CustomDialogDelegate ----------------------------------------------

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        this.root = panel;

        // --- Tab bar -----------------------------------------------------
        CustomPanelAPI tabBar = panel.createCustomPanel(W, TAB_BAR_H, plugin);
        float x = 0f;
        for (Tab t : Tab.values()) {
            TooltipMakerAPI el = tabBar.createUIElement(TAB_W, TAB_BAR_H, false);
            ButtonAPI b = el.addAreaCheckbox(label(t), t,
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                    TAB_W, TAB_BAR_H - 8f, 0f);
            b.setChecked(t == currentTab);
            tabButtons.put(t, b);
            tabBar.addUIElement(el).inTL(x, 0f);
            x += TAB_W + 4f;
        }

        // Search field on the right
        TooltipMakerAPI searchEl = tabBar.createUIElement(SEARCH_W, TAB_BAR_H, false);
        searchField = searchEl.addTextField(SEARCH_W, 0f);
        searchField.setText(lastSearch);
        tabBar.addUIElement(searchEl).inTL(W - SEARCH_W - 8f, 4f);

        panel.addComponent(tabBar).inTL(0f, 0f);

        // Body host is (re)created on every rebuild; see rebuildBody().
        rebuildBody();
    }

    @Override public boolean hasCancelButton() { return false; }
    @Override public String getConfirmText() { return "Close"; }
    @Override public String getCancelText() { return null; }
    @Override public void customDialogConfirm() {
        if (dialog != null) dialog.dismiss();
    }
    @Override public void customDialogCancel() {
        // Called on Escape as well as when a (non-existent) cancel button is clicked.
        if (dialog != null) dialog.dismiss();
    }
    @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return plugin; }

    // -- Layout helpers ----------------------------------------------------

    private String label(Tab t) {
        switch (t) {
            case GENERAL: return "General";
            case WEAPONS: return "Weapons";
            case FIGHTERS: return "Fighters";
            case HULLMODS: return "Hullmods";
        }
        return t.name();
    }

    private void switchTab(Tab t) {
        if (t == currentTab) {
            // Keep the button checked even if user clicked the active tab again.
            ButtonAPI b = tabButtons.get(t);
            if (b != null) b.setChecked(true);
            return;
        }
        currentTab = t;
        for (Map.Entry<Tab, ButtonAPI> e : tabButtons.entrySet()) {
            e.getValue().setChecked(e.getKey() == currentTab);
        }
        rebuildBody();
    }

    private void rebuildBody() {
        // Fully recreate the body host. Removing only the inner TooltipMakerAPI
        // does not reliably detach sub-panels (rows) or addCustom'd children, so
        // we drop the whole host and build a fresh one each time.
        if (bodyHost != null) {
            root.removeComponent(bodyHost);
            bodyHost = null;
        }
        bodyEl = null;
        actionBar = null;
        sellFields.clear();
        buyFields.clear();
        lastSell.clear();
        lastBuy.clear();
        visibleKeys.clear();
        templateSellField = null;
        templateBuyField = null;
        creditFloorField = null;

        float bodyH = H - TAB_BAR_H - 8f;
        bodyHost = root.createCustomPanel(W, bodyH, plugin);

        ATConfig cfg = ATRegistry.get();
        String q = currentSearchLower();

        float listY = 0f;
        if (currentTab == Tab.WEAPONS) {
            CustomPanelAPI filterRow = buildWeaponFilterBar();
            bodyHost.addComponent(filterRow).inTL(0f, 0f);
            actionBar = buildActionBar();
            actionBarY = FILTER_BAR_H + 2f;
            bodyHost.addComponent(actionBar).inTL(0f, actionBarY);
            listY = (FILTER_BAR_H * 2f) + 6f;
        } else if (currentTab == Tab.FIGHTERS) {
            CustomPanelAPI filterRow = buildFighterFilterBar();
            bodyHost.addComponent(filterRow).inTL(0f, 0f);
            actionBar = buildActionBar();
            actionBarY = FILTER_BAR_H + 2f;
            bodyHost.addComponent(actionBar).inTL(0f, actionBarY);
            listY = (FILTER_BAR_H * 2f) + 6f;
        } else {
            actionBar = null;
        }

        TooltipMakerAPI t = bodyHost.createUIElement(W, bodyH - listY, true);

        switch (currentTab) {
            case GENERAL:  buildGeneral(t, cfg); break;
            case WEAPONS:  buildWeapons(t, cfg, q); break;
            case FIGHTERS: buildFighters(t, cfg, q); break;
            case HULLMODS: buildHullmods(t, cfg, q); break;
        }

        bodyHost.addUIElement(t).inTL(0f, listY);
        bodyEl = t;
        root.addComponent(bodyHost).inTL(0f, TAB_BAR_H + 4f);
    }

    /** Swap just the action bar (selection count + select-all visual) without touching the scrollable list. */
    private void rebuildActionBar() {
        if (bodyHost == null || actionBar == null) return;
        bodyHost.removeComponent(actionBar);
        actionBar = buildActionBar();
        bodyHost.addComponent(actionBar).inTL(0f, actionBarY);
    }

    private String currentSearchLower() {
        if (searchField == null) return "";
        String s = searchField.getText();
        return s == null ? "" : s.trim().toLowerCase();
    }

    private boolean matches(String q, String name, String id) {
        if (q.isEmpty()) return true;
        if (name != null && name.toLowerCase().contains(q)) return true;
        if (id != null && id.toLowerCase().contains(q)) return true;
        return false;
    }

    private void addToggle(TooltipMakerAPI t, String label, Object data, boolean checked) {
        ButtonAPI b = t.addAreaCheckbox(label, data,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                520f, 24f, 4f);
        b.setChecked(checked);
    }

    // -- Tab: General ------------------------------------------------------

    private void buildGeneral(TooltipMakerAPI t, ATConfig cfg) {
        t.addSectionHeading("Global", Alignment.MID, 0f);
        t.addSpacer(8f);
        addToggle(t, "AutoTrader enabled", TG_ENABLED, cfg.enabled);
        addToggle(t, "Sell through black market (instead of open)", TG_SELL_BLACK, cfg.sellThroughBlack);

        t.addSpacer(14f);
        t.addPara("Credit floor (do not go below this):", 0f);
        creditFloorField = t.addTextField(180f, 6f);
        creditFloorField.setText(Integer.toString(cfg.creditFloor));
        lastCreditFloorText = creditFloorField.getText();

        t.addSpacer(20f);
        t.addSectionHeading("Bulk actions", Alignment.MID, 0f);
        t.addSpacer(8f);
        t.addPara("Set Reasonable Defaults overwrites all sell thresholds:\n"
                + "  \u2022 Large weapons: sell above 5\n"
                + "  \u2022 Medium weapons: sell above 10\n"
                + "  \u2022 Small weapons: sell above 25\n"
                + "  \u2022 Fighter LPCs: sell above 10\n"
                + "Existing buy thresholds are left untouched.",
                Misc.getGrayColor(), 0f);
        t.addSpacer(6f);
        t.addButton("Set Reasonable Defaults", TG_DEFAULTS,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                240f, 28f, 0f);
        t.addSpacer(6f);
        t.addButton("Clear All Rules", TG_CLEAR_RULES,
                Misc.getNegativeHighlightColor(), Misc.getDarkPlayerColor(),
                240f, 28f, 0f);
        t.addPara("Removes every per-weapon and per-fighter sell/buy threshold.",
                Misc.getGrayColor(), 4f);
    }

    // -- Tab: Weapons / Fighters -------------------------------------------

    private static final Comparator<WeaponSpecAPI> WEAPON_BY_NAME = new Comparator<WeaponSpecAPI>() {
        @Override public int compare(WeaponSpecAPI a, WeaponSpecAPI b) {
            return safeStr(a.getWeaponName()).compareToIgnoreCase(safeStr(b.getWeaponName()));
        }
    };

    private static final Comparator<FighterWingSpecAPI> FIGHTER_BY_NAME = new Comparator<FighterWingSpecAPI>() {
        @Override public int compare(FighterWingSpecAPI a, FighterWingSpecAPI b) {
            return safeStr(a.getWingName()).compareToIgnoreCase(safeStr(b.getWingName()));
        }
    };

    private static final Comparator<HullModSpecAPI> HULLMOD_BY_NAME = new Comparator<HullModSpecAPI>() {
        @Override public int compare(HullModSpecAPI a, HullModSpecAPI b) {
            return safeStr(a.getDisplayName()).compareToIgnoreCase(safeStr(b.getDisplayName()));
        }
    };

    private static String safeStr(String s) { return s == null ? "" : s; }

    private CargoAPI playerCargo() {
        if (Global.getSector() == null) return null;
        if (Global.getSector().getPlayerFleet() == null) return null;
        return Global.getSector().getPlayerFleet().getCargo();
    }

    private CustomPanelAPI buildWeaponFilterBar() {
        CustomPanelAPI bar = bodyHost.createCustomPanel(W, FILTER_BAR_H, plugin);
        float x = 0f;
        x = addFilterLabel(bar, "Size:", x, 50f);
        x = addFilterToggle(bar, "Small",  WeaponSize.SMALL,  sizeFilter.contains(WeaponSize.SMALL),  x, 70f);
        x = addFilterToggle(bar, "Medium", WeaponSize.MEDIUM, sizeFilter.contains(WeaponSize.MEDIUM), x, 70f);
        x = addFilterToggle(bar, "Large",  WeaponSize.LARGE,  sizeFilter.contains(WeaponSize.LARGE),  x, 70f);
        x += 16f;
        x = addFilterLabel(bar, "Type:", x, 50f);
        x = addFilterToggle(bar, "Energy",    WeaponType.ENERGY,    typeFilter.contains(WeaponType.ENERGY),    x, 70f);
        x = addFilterToggle(bar, "Ballistic", WeaponType.BALLISTIC, typeFilter.contains(WeaponType.BALLISTIC), x, 80f);
        x = addFilterToggle(bar, "Missile",   WeaponType.MISSILE,   typeFilter.contains(WeaponType.MISSILE),   x, 70f);
        x += 16f;
        x = addFilterToggle(bar, "Held only", TG_HELD_ONLY, heldOnly, x, 100f);
        return bar;
    }

    private CustomPanelAPI buildFighterFilterBar() {
        CustomPanelAPI bar = bodyHost.createCustomPanel(W, FILTER_BAR_H, plugin);
        addFilterToggle(bar, "Held only", TG_HELD_ONLY, heldOnly, 0f, 100f);
        return bar;
    }

    private float addFilterLabel(CustomPanelAPI bar, String text, float x, float width) {
        TooltipMakerAPI el = bar.createUIElement(width, FILTER_BAR_H, false);
        el.addPara(text, 0f);
        bar.addUIElement(el).inTL(x, 8f);
        return x + width;
    }

    private float addFilterToggle(CustomPanelAPI bar, String label, Object data, boolean checked, float x, float width) {
        TooltipMakerAPI el = bar.createUIElement(width, FILTER_BAR_H, false);
        ButtonAPI b = el.addAreaCheckbox(label, data,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                width - 4f, FILTER_BAR_H - 8f, 0f);
        b.setChecked(checked);
        bar.addUIElement(el).inTL(x, 0f);
        return x + width;
    }

    private void buildWeapons(TooltipMakerAPI t, ATConfig cfg, String q) {
        t.addSectionHeading("Weapons",
                Alignment.MID, 0f);
        t.addSpacer(4f);
        addColumnHeaders(t);

        List<WeaponSpecAPI> all = new ArrayList<>(Global.getSettings().getAllWeaponSpecs());
        Collections.sort(all, WEAPON_BY_NAME);

        CargoAPI cargo = heldOnly ? playerCargo() : null;

        int shown = 0;
        for (WeaponSpecAPI w : all) {
            String id = w.getWeaponId();
            String name = w.getWeaponName();
            if (!matches(q, name, id)) continue;
            if (!sizeFilter.isEmpty() && !sizeFilter.contains(w.getSize())) continue;
            if (!typeFilter.isEmpty() && !typeFilter.contains(w.getType())) continue;
            if (heldOnly && (cargo == null || cargo.getNumWeapons(id) <= 0)) continue;
            ATItemRule r = cfg.weapons.get(id);
            addItemRow(t, id, name, r, true);
            shown++;
        }
        t.addSpacer(8f);
        t.addPara("Showing " + shown + " of " + all.size() + " weapons.",
                Misc.getGrayColor(), 0f);
    }

    private void buildFighters(TooltipMakerAPI t, ATConfig cfg, String q) {
        t.addSectionHeading("Fighter LPCs",
                Alignment.MID, 0f);
        t.addSpacer(4f);
        addColumnHeaders(t);

        List<FighterWingSpecAPI> all = new ArrayList<>(Global.getSettings().getAllFighterWingSpecs());
        Collections.sort(all, FIGHTER_BY_NAME);

        CargoAPI cargo = heldOnly ? playerCargo() : null;

        int shown = 0;
        for (FighterWingSpecAPI f : all) {
            String id = f.getId();
            String name = f.getWingName();
            if (!matches(q, name, id)) continue;
            if (heldOnly && (cargo == null || cargo.getNumFighters(id) <= 0)) continue;
            ATItemRule r = cfg.fighters.get(id);
            addItemRow(t, id, name, r, false);
            shown++;
        }
        t.addSpacer(8f);
        t.addPara("Showing " + shown + " of " + all.size() + " fighters.",
                Misc.getGrayColor(), 0f);
    }

    private void addItemRow(TooltipMakerAPI t, String id, String name, ATItemRule rule, boolean isWeapon) {
        float rowW = W - 30f;
        float rowH = 30f;
        float checkW = 26f;
        CustomPanelAPI row = bodyHost.createCustomPanel(rowW, rowH, plugin);

        float fieldsW = (ROW_FIELD_W * 2f) + 12f;
        String key = (isWeapon ? "W:" : "F:") + id;

        TooltipMakerAPI cbEl = row.createUIElement(checkW, rowH, false);
        ButtonAPI cb = cbEl.addAreaCheckbox("", "SEL:" + key,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                checkW - 4f, rowH - 8f, 0f);
        cb.setChecked(selectedKeys.contains(key));
        row.addUIElement(cbEl).inTL(0f, 2f);

        TooltipMakerAPI nameEl = row.createUIElement(rowW - fieldsW - 12f - checkW, rowH, false);
        nameEl.addPara(name + "  [" + id + "]", 4f);
        row.addUIElement(nameEl).inTL(checkW + 4f, 4f);

        TooltipMakerAPI sellEl = row.createUIElement(ROW_FIELD_W, rowH, false);
        TextFieldAPI sell = sellEl.addTextField(ROW_FIELD_W, 0f);
        sell.setText(rule != null && rule.sellAbove >= 0 ? Integer.toString(rule.sellAbove) : "");
        tintField(sell, SELL_COLOR);
        row.addUIElement(sellEl).inTL(rowW - fieldsW, 2f);

        TooltipMakerAPI buyEl = row.createUIElement(ROW_FIELD_W, rowH, false);
        TextFieldAPI buy = buyEl.addTextField(ROW_FIELD_W, 0f);
        buy.setText(rule != null && rule.buyBelow >= 0 ? Integer.toString(rule.buyBelow) : "");
        tintField(buy, BUY_COLOR);
        row.addUIElement(buyEl).inTL(rowW - ROW_FIELD_W, 2f);

        sellFields.put(key, sell);
        buyFields.put(key, buy);
        lastSell.put(key, sell.getText() == null ? "" : sell.getText());
        lastBuy.put(key, buy.getText() == null ? "" : buy.getText());
        visibleKeys.add(key);

        t.addCustom(row, 2f);
    }

    private static void tintField(TextFieldAPI f, Color c) {
        if (f == null) return;
        f.setBorderColor(c);
        // Darker translucent fill so the text stays readable.
        f.setBgColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 40));
    }

    private CustomPanelAPI buildActionBar() {
        CustomPanelAPI bar = bodyHost.createCustomPanel(W, FILTER_BAR_H, plugin);
        float x = 0f;

        // Select-all toggle. Shows checked when every currently visible row is in the selection.
        boolean allSelected = !visibleKeys.isEmpty() && selectedKeys.containsAll(visibleKeys);
        TooltipMakerAPI saEl = bar.createUIElement(140f, FILTER_BAR_H, false);
        ButtonAPI sa = saEl.addAreaCheckbox("Select all visible", TG_SELECT_ALL,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                136f, FILTER_BAR_H - 8f, 0f);
        sa.setChecked(allSelected);
        bar.addUIElement(saEl).inTL(x, 0f);
        x += 144f;

        TooltipMakerAPI lbl = bar.createUIElement(80f, FILTER_BAR_H, false);
        lbl.addPara("Template:", Misc.getGrayColor(), 0f);
        bar.addUIElement(lbl).inTL(x, 10f);
        x += 80f;

        TooltipMakerAPI tsEl = bar.createUIElement(ROW_FIELD_W, FILTER_BAR_H, false);
        templateSellField = tsEl.addTextField(ROW_FIELD_W, 0f);
        templateSellField.setText(templateSellText);
        tintField(templateSellField, SELL_COLOR);
        bar.addUIElement(tsEl).inTL(x, 4f);
        x += ROW_FIELD_W + 6f;

        TooltipMakerAPI tbEl = bar.createUIElement(ROW_FIELD_W, FILTER_BAR_H, false);
        templateBuyField = tbEl.addTextField(ROW_FIELD_W, 0f);
        templateBuyField.setText(templateBuyText);
        tintField(templateBuyField, BUY_COLOR);
        bar.addUIElement(tbEl).inTL(x, 4f);
        x += ROW_FIELD_W + 10f;

        TooltipMakerAPI applyEl = bar.createUIElement(160f, FILTER_BAR_H, false);
        applyEl.addButton("Apply to selected (" + selectedKeys.size() + ")", TG_APPLY_TEMPLATE,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                160f, FILTER_BAR_H - 8f, 0f);
        bar.addUIElement(applyEl).inTL(x, 0f);
        x += 168f;

        TooltipMakerAPI clrEl = bar.createUIElement(130f, FILTER_BAR_H, false);
        clrEl.addButton("Clear selection", TG_CLEAR_SELECTION,
                Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                130f, FILTER_BAR_H - 8f, 0f);
        bar.addUIElement(clrEl).inTL(x, 0f);

        return bar;
    }

    private void applyTemplateToSelected() {
        // Snapshot text from the live fields (the polling cache may be one frame stale).
        String sellText = templateSellField != null ? templateSellField.getText() : templateSellText;
        String buyText  = templateBuyField  != null ? templateBuyField.getText()  : templateBuyText;

        boolean haveSell = sellText != null && !sellText.trim().isEmpty();
        boolean haveBuy  = buyText  != null && !buyText.trim().isEmpty();
        int sellVal = haveSell ? parseRowField(sellText) : -1;
        int buyVal  = haveBuy  ? parseRowField(buyText)  : -1;
        // Treat unparseable / negative as "skip this axis".
        if (haveSell && sellVal < 0) haveSell = false;
        if (haveBuy && buyVal < 0) haveBuy = false;
        if (!haveSell && !haveBuy) return;
        if (selectedKeys.isEmpty()) return;

        ATConfig cfg = ATRegistry.get();
        for (String key : selectedKeys) {
            boolean isWeapon = key.startsWith("W:");
            String id = key.substring(2);
            Map<String, ATItemRule> map = isWeapon ? cfg.weapons : cfg.fighters;
            ATItemRule r = map.get(id);
            if (r == null) {
                r = new ATItemRule();
                map.put(id, r);
            }
            if (haveSell) r.sellAbove = sellVal;
            if (haveBuy)  r.buyBelow  = buyVal;
            if (r.isEmpty()) map.remove(id);
        }
    }

    private void applyReasonableDefaults() {
        ATConfig cfg = ATRegistry.get();
        for (WeaponSpecAPI w : Global.getSettings().getAllWeaponSpecs()) {
            int v;
            WeaponSize s = w.getSize();
            if (s == WeaponSize.LARGE) v = 5;
            else if (s == WeaponSize.MEDIUM) v = 10;
            else v = 25; // SMALL or null
            String id = w.getWeaponId();
            ATItemRule r = cfg.weapons.get(id);
            if (r == null) { r = new ATItemRule(); cfg.weapons.put(id, r); }
            r.sellAbove = v;
        }
        for (FighterWingSpecAPI f : Global.getSettings().getAllFighterWingSpecs()) {
            String id = f.getId();
            ATItemRule r = cfg.fighters.get(id);
            if (r == null) { r = new ATItemRule(); cfg.fighters.put(id, r); }
            r.sellAbove = 10;
        }
    }

    private void addColumnHeaders(TooltipMakerAPI t) {
        float rowW = W - 30f;
        float fieldsW = (ROW_FIELD_W * 2f) + 12f;
        float checkW = 26f;
        CustomPanelAPI hdr = bodyHost.createCustomPanel(rowW, 18f, null);

        TooltipMakerAPI nameEl = hdr.createUIElement(rowW - fieldsW - 12f - checkW, 18f, false);
        nameEl.addPara("Item  (blank = no rule)", Misc.getGrayColor(), 0f);
        hdr.addUIElement(nameEl).inTL(checkW + 4f, 0f);

        TooltipMakerAPI sellEl = hdr.createUIElement(ROW_FIELD_W, 18f, false);
        sellEl.addPara("Sell above", SELL_COLOR, 0f);
        hdr.addUIElement(sellEl).inTL(rowW - fieldsW, 0f);

        TooltipMakerAPI buyEl = hdr.createUIElement(ROW_FIELD_W, 18f, false);
        buyEl.addPara("Buy below", BUY_COLOR, 0f);
        hdr.addUIElement(buyEl).inTL(rowW - ROW_FIELD_W, 0f);

        t.addCustom(hdr, 0f);
        t.addSpacer(2f);
    }

    // -- Tab: Hullmods -----------------------------------------------------

    private void buildHullmods(TooltipMakerAPI t, ATConfig cfg, String q) {
        t.addSectionHeading("Hullmods", Alignment.MID, 0f);
        t.addSpacer(6f);
        addToggle(t, "Buy unknown hullmods", TG_BUY_UNKNOWN_HM, cfg.buyUnknownHullmods);
        addToggle(t, "Also scan black markets for unknown hullmods", TG_BUY_HM_BLACK, cfg.buyHullmodsFromBlack);
        addToggle(t, "Auto-use hullmod specs on buy", TG_LEARN_HM, cfg.learnHullmodsOnBuy);
        t.addSpacer(10f);
        t.addSectionHeading("Blacklist  —  checked = never auto-buy",
                Alignment.MID, 0f);
        t.addSpacer(4f);

        List<HullModSpecAPI> all = new ArrayList<>(Global.getSettings().getAllHullModSpecs());
        Collections.sort(all, HULLMOD_BY_NAME);
        int shown = 0;
        int total = 0;
        for (HullModSpecAPI hm : all) {
            if (hm.isHidden() || hm.isHiddenEverywhere()) continue;
            total++;
            String id = hm.getId();
            String name = hm.getDisplayName();
            if (!matches(q, name, id)) continue;
            ButtonAPI b = t.addAreaCheckbox(name + "    [" + id + "]", "HM:" + id,
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Misc.getBrightPlayerColor(),
                    W - 60f, 22f, 2f);
            b.setChecked(cfg.hullmodBlacklist.contains(id));
            shown++;
        }
        t.addSpacer(8f);
        t.addPara("Showing " + shown + " of " + total + " hullmods.",
                Misc.getGrayColor(), 0f);
    }

    // -- Panel plugin ------------------------------------------------------

    private class Plugin implements CustomUIPanelPlugin {

        @Override public void positionChanged(PositionAPI position) {}
        @Override public void renderBelow(float alphaMult) {}
        @Override public void render(float alphaMult) {}
        @Override public void processInput(List<InputEventAPI> events) {}

        @Override
        public void advance(float amount) {
            // Live-filter: rebuild list tabs when search text changes.
            String s = currentSearchLower();
            if (!s.equals(lastSearch)) {
                lastSearch = s;
                if (currentTab != Tab.GENERAL) {
                    rebuildBody();
                    return; // field refs reset; skip polling this frame
                }
            }

            ATConfig cfg = ATRegistry.get();

            if (creditFloorField != null) {
                String txt = creditFloorField.getText();
                if (txt == null) txt = "";
                if (!txt.equals(lastCreditFloorText)) {
                    lastCreditFloorText = txt;
                    cfg.creditFloor = parseIntOr(txt, cfg.creditFloor);
                }
            }

            if (templateSellField != null) {
                String txt = templateSellField.getText();
                if (txt == null) txt = "";
                templateSellText = txt;
            }
            if (templateBuyField != null) {
                String txt = templateBuyField.getText();
                if (txt == null) txt = "";
                templateBuyText = txt;
            }

            for (Map.Entry<String, TextFieldAPI> e : sellFields.entrySet()) {
                String key = e.getKey();
                String txt = e.getValue().getText();
                if (txt == null) txt = "";
                String prev = lastSell.get(key);
                if (!txt.equals(prev)) {
                    lastSell.put(key, txt);
                    applyRowChange(cfg, key, txt, true);
                }
            }
            for (Map.Entry<String, TextFieldAPI> e : buyFields.entrySet()) {
                String key = e.getKey();
                String txt = e.getValue().getText();
                if (txt == null) txt = "";
                String prev = lastBuy.get(key);
                if (!txt.equals(prev)) {
                    lastBuy.put(key, txt);
                    applyRowChange(cfg, key, txt, false);
                }
            }
        }

        @Override
        public void buttonPressed(Object data) {
            if (data instanceof Tab) {
                switchTab((Tab) data);
                return;
            }
            if (data instanceof WeaponSize) {
                WeaponSize s = (WeaponSize) data;
                if (!sizeFilter.remove(s)) sizeFilter.add(s);
                rebuildBody();
                return;
            }
            if (data instanceof WeaponType) {
                WeaponType ty = (WeaponType) data;
                if (!typeFilter.remove(ty)) typeFilter.add(ty);
                rebuildBody();
                return;
            }
            if (data == TG_HELD_ONLY) {
                heldOnly = !heldOnly;
                rebuildBody();
                return;
            }
            if (data == TG_DEFAULTS) {
                applyReasonableDefaults();
                rebuildBody();
                return;
            }
            if (data == TG_CLEAR_RULES) {
                ATConfig c = ATRegistry.get();
                c.weapons.clear();
                c.fighters.clear();
                rebuildBody();
                return;
            }
            if (data == TG_SELECT_ALL) {
                if (visibleKeys.isEmpty()) return;
                if (selectedKeys.containsAll(visibleKeys)) {
                    selectedKeys.removeAll(visibleKeys);
                } else {
                    selectedKeys.addAll(visibleKeys);
                }
                rebuildBody();
                return;
            }
            if (data == TG_APPLY_TEMPLATE) {
                applyTemplateToSelected();
                rebuildBody();
                return;
            }
            if (data == TG_CLEAR_SELECTION) {
                selectedKeys.clear();
                rebuildBody();
                return;
            }
            if (data instanceof String && ((String) data).startsWith("SEL:")) {
                String key = ((String) data).substring(4);
                if (!selectedKeys.remove(key)) selectedKeys.add(key);
                // Only refresh the action bar (count + select-all visual) so the scrollable
                // list keeps its scroll position. The row's own checkbox visual was already
                // toggled by the click itself.
                rebuildActionBar();
                return;
            }
            ATConfig cfg = ATRegistry.get();
            if (data == TG_ENABLED) cfg.enabled = !cfg.enabled;
            else if (data == TG_SELL_BLACK) cfg.sellThroughBlack = !cfg.sellThroughBlack;
            else if (data == TG_BUY_UNKNOWN_HM) cfg.buyUnknownHullmods = !cfg.buyUnknownHullmods;
            else if (data == TG_LEARN_HM) cfg.learnHullmodsOnBuy = !cfg.learnHullmodsOnBuy;
            else if (data == TG_BUY_HM_BLACK) cfg.buyHullmodsFromBlack = !cfg.buyHullmodsFromBlack;
            else if (data instanceof String && ((String) data).startsWith("HM:")) {
                String id = ((String) data).substring(3);
                if (!cfg.hullmodBlacklist.remove(id)) {
                    cfg.hullmodBlacklist.add(id);
                }
            }
        }
    }

    // -- Util --------------------------------------------------------------

    private static int parseIntOr(String s, int fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void applyRowChange(ATConfig cfg, String key, String txt, boolean isSell) {
        boolean isWeapon = key.startsWith("W:");
        String id = key.substring(2);
        Map<String, ATItemRule> map = isWeapon ? cfg.weapons : cfg.fighters;
        ATItemRule r = map.get(id);
        int parsed = parseRowField(txt);
        if (r == null) {
            if (parsed < 0) return;
            r = new ATItemRule();
            map.put(id, r);
        }
        if (isSell) r.sellAbove = parsed;
        else r.buyBelow = parsed;
        if (r.isEmpty()) map.remove(id);
    }

    /** Returns -1 for blank, parsed value for valid non-negative int, else -1. */
    private static int parseRowField(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.isEmpty()) return -1;
        try {
            int v = Integer.parseInt(t);
            return v < 0 ? -1 : v;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
