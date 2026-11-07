package de.moinmornhart.stinkendreich.auto;

import de.moinmornhart.stinkendreich.Kompat;
import de.moinmornhart.stinkendreich.register.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Autoschlüssel (Philip, 07.11.: „dass man wirklich Autos auch schließen kann“). Jeder Schlüssel gehört zu genau einem
 * Wagen (dessen Kennung steht in den Daten des Gegenstands, der Name sagt, welcher Wagen es ist). Rechtsklick mit dem
 * Schlüssel in der Hand schließt den Wagen ab oder auf, wenn er höchstens {@link #FUNK} Blöcke entfernt ist – mit Piepen
 * und Blinken. Einen abgeschlossenen Wagen öffnet nur, wer den passenden Schlüssel dabeihat.
 *
 * <p>Den Schlüssel bekommt man im Automenü (Schleichen + Rechtsklick auf den Wagen): Der Erste, der ihn nimmt, wird
 * Besitzer des Wagens; danach bekommt nur der Besitzer weitere Schlüssel.</p>
 */
public class AutoschluesselItem extends Item {
    /** Reichweite der Funkfernbedienung (Blöcke). */
    public static final double FUNK = 48;
    private static final String DATEN = "stinkendreich_auto";

    public AutoschluesselItem(Properties eigenschaften) {
        super(eigenschaften);
    }

    /** Ein neuer Schlüssel für diesen Wagen. */
    public static ItemStack fuer(AutoEntity auto) {
        ItemStack s = new ItemStack(ModItems.AUTOSCHLUESSEL);
        CompoundTag tag = new CompoundTag();
        tag.putString(DATEN, auto.getUUID().toString());
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        String schild = auto.kennzeichen().isEmpty() ? "" : " · " + auto.kennzeichen();
        s.set(DataComponents.CUSTOM_NAME, Component.translatable("stinkendreich.schluessel.name",
                        Component.translatable("entity.stinkendreich." + auto.art.name), schild)
                .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
        return s;
    }

    /** Zu welchem Wagen gehört der Schlüssel (null bei einem leeren Rohling)? */
    public static UUID wagen(ItemStack s) {
        if (!s.is(ModItems.AUTOSCHLUESSEL)) {
            return null;
        }
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) {
            return null;
        }
        CompoundTag tag = d.copyTag();
        //? if >=1.21.5 {
        String text = tag.getStringOr(DATEN, "");
        //?} else
        /*String text = tag.getString(DATEN);*/
        try {
            return text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Hat der Spieler einen Schlüssel für diesen Wagen dabei (Inventar oder Hand)? */
    public static boolean hatSchluessel(Player p, AutoEntity auto) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (auto.getUUID().equals(wagen(inv.getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    /** Funk: den eigenen Wagen in der Nähe ab- bzw. aufschließen. */
    private static void funken(Level welt, Player spieler, ItemStack schluessel) {
        if (!(welt instanceof ServerLevel sw)) {
            return;
        }
        UUID id = wagen(schluessel);
        AutoEntity auto = id == null ? null : sw.getEntity(id) instanceof AutoEntity a ? a : null;
        if (auto == null || auto.distanceTo(spieler) > FUNK) {
            Kompat.nachricht(spieler, Component.translatable("stinkendreich.schluessel.zu_weit").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        auto.verschliessen(!auto.verschlossen(), spieler);
    }

    //? if >=1.21.2 {
    @Override
    public InteractionResult use(Level welt, Player spieler, InteractionHand hand) {
        funken(welt, spieler, spieler.getItemInHand(hand));
        return InteractionResult.SUCCESS;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level welt, Player spieler, InteractionHand hand) {
        funken(welt, spieler, spieler.getItemInHand(hand));
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(spieler.getItemInHand(hand), welt.isClientSide());
    }
    *///?}
}
