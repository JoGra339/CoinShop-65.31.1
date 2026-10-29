package de.coinshop.gui;

import de.coinshop.CoinShopPlugin;
import de.coinshop.ItemUtil;
import de.coinshop.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Großhandel: Rangliste, Firmenrang und alle Firmen unter einem Knopf. */
public final class WholesaleRanksMenu extends Menu {

    public WholesaleRanksMenu(CoinShopPlugin plugin, Player viewer) {
        super(plugin, 3, Component.text("Ranglisten und Firmen"));
        Messages msg = plugin.messages();
        List<Component> none = List.of();
        set(11, ItemUtil.icon(Material.GOLDEN_HELMET, msg.get("gui.whhub.rank"), none),
                (p, c) -> new WholesaleRankMenu(plugin, p, -1).open(p));
        set(13, ItemUtil.icon(Material.NETHER_STAR, msg.get("gui.whhub.company-rank"), none),
                (p, c) -> new CompanyRankMenu(plugin, p).open(p));
        set(15, ItemUtil.icon(Material.BOOKSHELF, msg.get("gui.whhub.companies"), none),
                (p, c) -> new CompanyListMenu(plugin, p, 0).open(p));
        set(18, backButton(), (p, c) -> new WholesaleHubMenu(plugin, p).open(p));
        set(22, balanceIcon(viewer), incomeClick());
        fillEmpty();
    }
}
