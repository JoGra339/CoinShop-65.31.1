package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Das Hauptmenü des Großhandels (nur mit der Großhandellizenz). */
public final class WholesaleHubMenu extends Menu {

    private final CoinShopPlugin pl;

    @Override
    public void open(Player player) {
        if (!pl.wholesale().allowed(player)) {
            return; // ohne Grosshandellizenz kommt man nicht hinein
        }
        super.open(player);
    }

    public WholesaleHubMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 4, plugin.messages().get("gui.whhub.title"));
        this.pl = plugin;
        Messages msg = plugin.messages();
        java.util.List<net.kyori.adventure.text.Component> none = java.util.List.of();
        set(11, ItemUtil.icon(Material.BARREL, msg.get("gui.whhub.orders"), none),
                (p, c) -> new WholesaleMenu(plugin, p).open(p));
        set(13, ItemUtil.icon(Material.WRITABLE_BOOK, msg.get("gui.whhub.treaties"), none),
                (p, c) -> new TreatyMenu(plugin, p).open(p));
        set(15, ItemUtil.icon(Material.GOLDEN_HELMET, net.kyori.adventure.text.Component.text("Ranglisten und Firmen",
                        net.kyori.adventure.text.format.NamedTextColor.YELLOW, net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                java.util.List.of(ExpeditionMenu.line("Rangliste, Firmenrang und alle Firmen", net.kyori.adventure.text.format.NamedTextColor.GRAY))),
                (p, c) -> new WholesaleRanksMenu(plugin, p).open(p));
        set(20, ItemUtil.icon(Material.OAK_SIGN, msg.get("gui.whhub.company"), none),
                (p, c) -> {
                    if (plugin.companies().employerOf(p.getUniqueId()) != null) {
                        new StaffHomeMenu(plugin, p).open(p);
                    } else {
                        new CompanyMenu(plugin, p).open(p);
                    }
                });
        if (plugin.expeditions().enabled()) {
            set(29, ItemUtil.icon(Material.LEAD, msg.get("gui.whhub.companions"), none),
                    (p, c) -> ExpeditionCopyMenu.forWholesale(plugin, p, q -> new WholesaleHubMenu(plugin, q).open(q)).open(p));
        }
        set(27, backButton(), (p, c) -> new MainMenu(plugin).open(p));
        set(31, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
