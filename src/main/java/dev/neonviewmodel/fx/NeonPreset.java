package dev.neonviewmodel.fx;

import dev.neonviewmodel.NeonConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

/** Presets de color inspirados en el vídeo: rosa/magenta neón, azul eléctrico/cian y esmeralda. */
public enum NeonPreset {
    PLASMA_PINK(0xFF3FD0, 0xA63BFF),    // magenta neón + violeta
    ELECTRIC_BLUE(0x27D6FF, 0x3A5BFF),  // cian + azul eléctrico
    EMERALD(0x1CE58A, 0x0A7A55);        // esmeralda + verde oscuro

    public final int primary;
    public final int secondary;

    NeonPreset(int primary, int secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    /** Devuelve el preset para el ítem en mano, o null si no debe haber efecto. */
    public static NeonPreset resolve(ItemStack stack, NeonConfig cfg) {
        if (stack.isEmpty()) return null;

        boolean weaponOrTool = stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES)
                || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)
                || stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW)
                || stack.isOf(Items.TRIDENT) || stack.isOf(Items.MACE);
        if (!weaponOrTool && !cfg.affectAllItems) return null;

        // Preset forzado por el usuario
        if (!"AUTO".equalsIgnoreCase(cfg.preset)) {
            for (NeonPreset p : values()) if (p.name().equalsIgnoreCase(cfg.preset)) return p;
        }

        // AUTO: espadas -> rosa; hachas/maza -> esmeralda; resto -> azul
        if (stack.isIn(ItemTags.SWORDS)) return PLASMA_PINK;
        if (stack.isIn(ItemTags.AXES) || stack.isOf(Items.MACE)) return EMERALD;
        if (weaponOrTool) return ELECTRIC_BLUE;

        // Cualquier otro ítem (solo si affectAllItems): color estable según su id
        Item item = stack.getItem();
        return values()[Math.floorMod(Registries.ITEM.getId(item).hashCode(), values().length)];
    }
}
