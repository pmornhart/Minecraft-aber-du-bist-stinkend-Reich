package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Automenü (Philip, 07.11.: „mich sneake und dann Rechtsklick mache, dann öffnet sich ein Menü, wo ich meinen Kofferraum
 * öffnen kann … und nach Hause bringen“). Eine Zeile mit Knöpfen als Gegenstände, wie ein Truhenfenster vom Server –
 * so geht es in jeder Version und auch für Spieler ohne Mod. Ein Klick nimmt nichts heraus, sondern löst die Aktion aus.
 *
 * <p>Knöpfe: Kofferraum (27 Plätze), Abschließen/Aufschließen, Schlüssel nehmen, Nach Hause bringen (James holt den
 * Wagen mit dem Hubschrauber ab und stellt ihn in die Garage), Einpacken (Wagen wird wieder zum Gegenstand).</p>
 */
public final class AutoMenue extends ChestMenu {
    private static final int KOFFERRAUM = 0, SCHLOSS = 2, SCHLUESSEL = 4, HEIM = 6, EINPACKEN = 8;

    private final AutoEntity auto;

    private AutoMenue(int id, Inventory inv, SimpleContainer knoepfe, AutoEntity auto) {
        super(MenuType.GENERIC_9x1, id, inv, knoepfe, 1);
        this.auto = auto;
    }

    /** Öffnet das Menü für diesen Wagen. */
    public static void oeffnen(ServerPlayer p, AutoEntity auto) {
        SimpleContainer knoepfe = new SimpleContainer(9);
        boolean darf = auto.darfBedienen(p);
        knoepfe.setItem(KOFFERRAUM, knopf(new ItemStack(Items.CHEST), "kofferraum", darf));
        knoepfe.setItem(SCHLOSS, knopf(new ItemStack(auto.verschlossen() ? Items.IRON_DOOR : Items.TRIPWIRE_HOOK),
                auto.verschlossen() ? "aufschliessen" : "abschliessen", darf));
        knoepfe.setItem(SCHLUESSEL, knopf(new ItemStack(ModItems.AUTOSCHLUESSEL), "schluessel", auto.besitzer() == null || auto.istBesitzer(p)));
        if (!auto.art.fliegt()) {
            knoepfe.setItem(HEIM, knopf(new ItemStack(ModItems.HUBSCHRAUBER), "heim", darf));
        }
        knoepfe.setItem(EINPACKEN, knopf(new ItemStack(ModItems.auto(auto.art)), "einpacken", darf));
        Component titel = Component.translatable("entity.stinkendreich." + auto.art.name)
                .append(auto.kennzeichen().isEmpty() ? Component.empty() : Component.literal(" · " + auto.kennzeichen()));
        p.openMenu(new SimpleMenuProvider((id, inv, sp) -> new AutoMenue(id, inv, knoepfe, auto), titel));
    }

    private static ItemStack knopf(ItemStack s, String name, boolean erlaubt) {
        s.set(DataComponents.CUSTOM_NAME, Component.translatable("stinkendreich.automenue." + name)
                .withStyle(st -> st.withItalic(false).withColor(erlaubt ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
        return s;
    }

    @Override
    //? if >=26.1 {
    public void clicked(int platz, int taste, net.minecraft.world.inventory.ContainerInput art, Player spieler) {
    //?} else
    /*public void clicked(int platz, int taste, net.minecraft.world.inventory.ClickType art, Player spieler) {*/
        // Nichts verschieben: Knöpfe bleiben, wo sie sind; das eigene Inventar unten ist gesperrt
        if (!(spieler instanceof ServerPlayer sp) || platz < 0 || platz >= 9 || !auto.isAlive()) {
            return;
        }
        switch (platz) {
            case KOFFERRAUM -> {
                sp.closeContainer();
                auto.kofferraumOeffnen(sp);
            }
            case SCHLOSS -> {
                if (pruefen(sp)) {
                    sp.closeContainer();
                    auto.verschliessen(!auto.verschlossen(), sp);
                }
            }
            case SCHLUESSEL -> {
                sp.closeContainer();
                auto.schluesselGeben(sp);
            }
            case HEIM -> {
                if (pruefen(sp) && !auto.art.fliegt()) {
                    sp.closeContainer();
                    auto.heimBringen(sp);
                }
            }
            case EINPACKEN -> {
                if (pruefen(sp)) {
                    sp.closeContainer();
                    auto.einpacken();
                }
            }
            default -> {
            }
        }
    }

    private boolean pruefen(ServerPlayer sp) {
        if (auto.darfBedienen(sp)) {
            return true;
        }
        Kompat.nachricht(sp, Component.translatable("stinkendreich.schluessel.fehlt").withStyle(ChatFormatting.RED), true);
        sendAllDataToRemote();       // der Client hatte den Knopf schon „aufgenommen“
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player spieler, int platz) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player spieler) {
        return auto.isAlive() && spieler.distanceTo(auto) < 8;
    }
}
