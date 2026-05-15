package spinloki.AutoTrader.internal.config;

/**
 * Count-based threshold rule for a single weapon / fighter LPC.
 *
 * <p>Semantics:
 * <ul>
 *   <li>{@code sellAbove >= 0}: if cargo count &gt; {@code sellAbove}, sell down to {@code sellAbove}.</li>
 *   <li>{@code buyBelow >= 0}: if cargo count &lt; {@code buyBelow}, buy up to {@code buyBelow} (subject to availability and credits).</li>
 *   <li>A negative value means "unset / do nothing for this direction".</li>
 * </ul>
 *
 * If both are set and buyBelow &gt; sellAbove, sell runs first, then buy — which can produce
 * a final count anywhere in {@code [sellAbove, buyBelow]}. Generally users should set
 * buyBelow &lt;= sellAbove or leave one unset.
 */
public class ATItemRule {
    public int sellAbove = -1;
    public int buyBelow = -1;

    public ATItemRule() {}

    public ATItemRule(int sellAbove, int buyBelow) {
        this.sellAbove = sellAbove;
        this.buyBelow = buyBelow;
    }

    public boolean isEmpty() {
        return sellAbove < 0 && buyBelow < 0;
    }
}
