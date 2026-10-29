package de.coinshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/**
 * Ausdauer für Tiefenmine, Expeditionslevel und Verteidigung. Jede Aktivität kostet beim Start Ausdauer; ohne genug
 * Ausdauer kann man nicht starten. Sie füllt sich mit der Zeit auf (auch offline) und hat keine Obergrenze
 * (stamina.max = 0). Die Auffüllrate lässt sich mit Coins in Stufen steigern. Gespeichert wird in der Datenbank:
 * stam_val_ (Tausendstel Punkte), stam_ts_ (Zeitpunkt der Messung) und stam_lvl_ (Stufe).
 */
public final class Stamina {

    /** Die drei Arten, die Ausdauer kosten: Schlüssel in der config.yml und Standardkosten. */
    public enum Mode {
        MINE("mine", 50, "Tiefenmine"),
        EXPEDITION("expedition", 25, "Expeditionslevel"),
        DEFENSE("defense", 5, "Verteidigungswelle");

        final String key;
        final double def;
        public final String label;

        Mode(String key, double def, String label) {
            this.key = key;
            this.def = def;
            this.label = label;
        }
    }

    private final CoinShopPlugin plugin;

    public Stamina(CoinShopPlugin plugin) {
        this.plugin = plugin;
    }

    private double cfgD(String path, double def) {
        return plugin.getConfig().getDouble("stamina." + path, def);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("stamina.enabled", true);
    }

    public double cost(Mode mode) {
        return Math.max(0, cfgD("cost." + mode.key, mode.def));
    }

    /** Höchste Stufe (0 in der config = keine Grenze). */
    public int maxLevel() {
        int m = (int) cfgD("max-level", 0);
        return m <= 0 ? Integer.MAX_VALUE : m;
    }

    /** Stufe des Spielers (1 bis maxLevel). Wer früher Mine-Läufe gekauft hat, startet mit entsprechend vielen Stufen. */
    public int level(UUID uuid) {
        Database db = plugin.database();
        long lvl = db.getMetaLong("stam_lvl_" + uuid, 0);
        if (lvl <= 0) {
            long slots = Math.max(0, db.getMetaLong("mine_slots_" + uuid, 0));
            lvl = 1 + slots;
            db.setMetaLong("stam_lvl_" + uuid, Math.min(maxLevel(), lvl));
        }
        return (int) Math.max(1, Math.min(maxLevel(), lvl));
    }

    /** Ausdauer, die pro Stunde dazukommt. */
    public double perHour(UUID uuid) {
        return Math.max(0, cfgD("per-hour", 10)) + Math.max(0, cfgD("per-hour-per-level", 0.5)) * (level(uuid) - 1);
    }

    /** Höchstvorrat: wächst mit der Stufe (ohne Ende). Auffüllen hört hier auf; ausgeben kann man alles. */
    public double max(UUID uuid) {
        return Math.max(1, cfgD("max-base", 100)) + Math.max(0, cfgD("max-per-level", 10)) * (level(uuid) - 1);
    }

    private static final long HOUR = 3_600_000L;

    /**
     * Gespeicherter Stand, um die vollen Stunden seit der letzten Messung weitergerechnet: {Vorrat in Tausendstel, Zeitpunkt}.
     * Die Auffüllung kommt nur in ganzen Stunden (nach 1 Stunde auf einen Schlag die ganze Rate); angefangene Stunden bleiben
     * erhalten. Ist der Vorrat voll, läuft keine Uhr.
     */
    private long[] state(UUID uuid) {
        Database db = plugin.database();
        long ts = db.getMetaLong("stam_ts_" + uuid, 0);
        long now = System.currentTimeMillis();
        if (ts <= 0) {
            long start = Math.round(max(uuid) * 1000); // neue Spieler beginnen mit vollem Vorrat
            db.setMetaLong("stam_val_" + uuid, start);
            db.setMetaLong("stam_ts_" + uuid, now);
            return new long[]{start, now};
        }
        long val = db.getMetaLong("stam_val_" + uuid, 0);
        long maxMilli = Math.round(max(uuid) * 1000);
        if (val >= maxMilli) {
            return new long[]{val, now}; // voll: die Uhr startet erst beim nächsten Verbrauch
        }
        long hours = Math.max(0, (now - ts) / HOUR);
        if (hours > 0) {
            long add = Math.round(hours * perHour(uuid) * 1000);
            if (val + add >= maxMilli) {
                return new long[]{maxMilli, now};
            }
            return new long[]{val + add, ts + hours * HOUR};
        }
        return new long[]{val, ts};
    }

    /** Aktueller Vorrat (nur ganze Stunden Auffüllung sind enthalten). */
    public double get(UUID uuid) {
        return state(uuid)[0] / 1000.0;
    }

    private void store(UUID uuid, double value) {
        long[] st = state(uuid);
        Database db = plugin.database();
        db.setMetaLong("stam_val_" + uuid, Math.round(Math.max(0, value) * 1000));
        db.setMetaLong("stam_ts_" + uuid, st[1]); // angefangene Stunde bleibt erhalten
    }

    /** Sekunden bis zur nächsten vollen Stunde der Auffüllung (0 = voll). */
    public long secondsToNextTick(UUID uuid) {
        long[] st = state(uuid);
        if (st[0] >= Math.round(max(uuid) * 1000)) {
            return 0;
        }
        return Math.max(1, (st[1] + HOUR - System.currentTimeMillis() + 999) / 1000);
    }

    /** Sekunden bis zum vollen Vorrat (0 = voll). */
    public long secondsUntilFull(UUID uuid) {
        return secondsFor(uuid, max(uuid) - get(uuid));
    }

    public boolean has(UUID uuid, Mode mode) {
        return !enabled() || get(uuid) + 1e-9 >= cost(mode);
    }

    /** Zieht die Kosten ab (true, wenn es gereicht hat; ist die Ausdauer ausgeschaltet, kostet nichts etwas). */
    public boolean spend(UUID uuid, Mode mode) {
        if (!enabled()) {
            return true;
        }
        double have = get(uuid);
        double cost = cost(mode);
        if (have + 1e-9 < cost) {
            return false;
        }
        store(uuid, have - cost);
        return true;
    }

    /** Schenkt Ausdauer (z. B. als Belohnung). */
    public void add(UUID uuid, double amount) {
        store(uuid, get(uuid) + Math.max(0, amount));
    }

    /** Sekunden, bis die Kosten bezahlbar sind (0 = jetzt). */
    public long secondsUntil(UUID uuid, Mode mode) {
        double missing = cost(mode) - get(uuid);
        if (missing <= 0) {
            return 0;
        }
        if (cost(mode) > max(uuid) + 1e-9) {
            return Long.MAX_VALUE; // passt nicht in den Vorrat: erst die Stufe steigern
        }
        return secondsFor(uuid, missing);
    }

    /** Sekunden, bis so viel Ausdauer dazugekommen ist (die Auffüllung kommt in ganzen Stunden). */
    private long secondsFor(UUID uuid, double missing) {
        if (missing <= 1e-9) {
            return 0;
        }
        double rate = perHour(uuid);
        if (rate <= 0) {
            return Long.MAX_VALUE;
        }
        long ticks = (long) Math.ceil(missing / rate - 1e-9);
        return secondsToNextTick(uuid) + Math.max(0, ticks - 1) * 3600L;
    }

    public static String format(double value) {
        return String.format(Locale.GERMANY, value == Math.rint(value) ? "%.0f" : "%.1f", value);
    }

    public static String duration(long seconds) {
        if (seconds == Long.MAX_VALUE) {
            return "unendlich langer Zeit (erst den Höchstvorrat steigern)";
        }
        if (seconds >= 3600) {
            return (seconds / 3600) + " Std. " + ((seconds % 3600) / 60) + " Min.";
        }
        return Math.max(1, (seconds + 59) / 60) + " Min.";
    }

    /** Meldung an den Spieler: zu wenig Ausdauer. */
    public void sendMissing(Player player, Mode mode) {
        UUID uuid = player.getUniqueId();
        player.sendMessage(Component.text("Zu wenig Ausdauer für " + mode.label + ": Du hast " + format(get(uuid)) + ", es kostet "
                + format(cost(mode)) + ". In " + duration(secondsUntil(uuid, mode)) + " reicht sie (+" + format(perHour(uuid))
                + " pro Stunde, Höchstvorrat " + format(max(uuid)) + "). Mit /ausdauer steigerst du beides.", NamedTextColor.RED));
    }

    /** Preis der nächsten Stufe (-1 = Höchststufe). */
    public long upgradePrice(UUID uuid) {
        int lvl = level(uuid);
        if (lvl >= maxLevel()) {
            return -1;
        }
        double price = Math.max(1, cfgD("upgrade-price", 1500)) * Math.pow(Math.max(1.0, cfgD("upgrade-growth", 1.2)), lvl - 1);
        return (long) Math.min(price, 1.0e15); // praktisch unbegrenzt, aber ohne Überlauf
    }

    /** Schenkt eine Stufe (z. B. aus einer Mystery Box); gibt die neue Stufe zurück. */
    public int grantLevel(UUID uuid) {
        int lvl = level(uuid);
        if (lvl >= maxLevel()) {
            return lvl;
        }
        plugin.database().setMetaLong("stam_lvl_" + uuid, lvl + 1);
        return lvl + 1;
    }

    /** Kauft die nächste Stufe mit Coins. */
    public void upgrade(Player player) {
        Database db = plugin.database();
        UUID uuid = player.getUniqueId();
        if (plugin.expeditions().isIn(uuid)) {
            return;
        }
        long price = upgradePrice(uuid);
        if (price < 0) {
            player.sendMessage(Component.text("Deine Ausdauer hat schon die Höchststufe.", NamedTextColor.GREEN));
            return;
        }
        int lvl = level(uuid);
        db.begin();
        try {
            if (!db.takeCoins(uuid, price)) {
                db.rollback();
                plugin.messages().send(player, "ex-no-money", "price", price);
                return;
            }
            db.addShopAccount(price);
            db.setMetaLong("stam_lvl_" + uuid, lvl + 1);
            db.commit();
        } catch (RuntimeException e) {
            db.rollback();
            plugin.getLogger().severe("Ausdauer: Stufe kaufen fehlgeschlagen: " + e.getMessage());
            plugin.messages().send(player, "error");
            return;
        }
        db.logTx(uuid, player.getName(), "ausdauer", -price, "Ausdauer-Stufe " + (lvl + 1));
        player.sendMessage(Component.text("Ausdauer-Stufe " + (lvl + 1) + ": Jetzt +" + format(perHour(uuid)) + " pro Stunde.", NamedTextColor.GREEN));
    }
}
