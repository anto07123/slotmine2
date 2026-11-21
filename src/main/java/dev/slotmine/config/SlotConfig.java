package dev.slotmine.config;

import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** config/slotmine/config.json — editable a mano. Las claves de bloques aceptan "minecraft:diamond_ore" o solo "diamond_ore". */
public final class SlotConfig {
    public static final class OpBlock {
        public double scoreMult = 10;   // multiplicador de puntos
        public double luckMult = 3;     // multiplica TODAS las probabilidades de evento (crit, chain, jackpot...)
        public OpBlock() {}
        public OpBlock(double s, double l) { scoreMult = s; luckMult = l; }
    }

    public int configVersion = 1;

    // HUD
    public boolean hudEnabled = true;
    public String hudSide = "RIGHT";   // LEFT | RIGHT
    public int hudMarginX = 4;
    public int hudOffsetY = 0;         // desde el centro vertical; negativo = más arriba

    // FX
    public boolean effectsEnabled = true;
    public boolean soundEnabled = true;
    public float soundVolume = 0.8f;
    public int particleBudgetPerTick = 36;
    public int liteModeBreaksPerSecond = 5; // a partir de aquí (instamine) se usan efectos mínimos

    // Probabilidades base por bloque
    public double critChance = 0.08;
    public double critMultiplier = 5.0;
    public double chainChance = 0.05;
    public double shockwaveChance = 0.025;
    public double feverChance = 0.012;
    public double jackpotChance = 0.0012;
    public double nearMissFactor = 6.0;     // ventana de "casi" = jackpot * factor
    public int pityMax = 900;               // bloques sin jackpot => jackpot garantizado
    public double feverSeconds = 8;
    public double feverMultiplier = 2.0;
    public double jackpotBaseMult = 500;    // x puntos del bloque
    public double jackpotPointsShare = 0.03; // + % de los puntos actuales

    public double defaultBlockValue = 1.0;
    public Map<String, Double> blockValues = new LinkedHashMap<>();
    public Map<String, OpBlock> opBlocks = new LinkedHashMap<>();

    public SlotConfig() {
        v("coal_ore", 4); v("deepslate_coal_ore", 5);
        v("iron_ore", 8); v("deepslate_iron_ore", 10);
        v("copper_ore", 6); v("deepslate_copper_ore", 7);
        v("gold_ore", 20); v("deepslate_gold_ore", 24); v("nether_gold_ore", 14);
        v("redstone_ore", 10); v("deepslate_redstone_ore", 12);
        v("lapis_ore", 14); v("deepslate_lapis_ore", 16);
        v("nether_quartz_ore", 8);
        v("diamond_ore", 60); v("deepslate_diamond_ore", 70);
        v("emerald_ore", 80); v("deepslate_emerald_ore", 90);
        v("ancient_debris", 400);
        op("diamond_ore", 15, 3); op("deepslate_diamond_ore", 18, 3);
        op("emerald_ore", 20, 3.5); op("deepslate_emerald_ore", 24, 3.5);
        op("ancient_debris", 60, 6);
    }

    private void v(String path, double val) { blockValues.put("minecraft:" + path, val); }
    private void op(String path, double s, double l) { opBlocks.put("minecraft:" + path, new OpBlock(s, l)); }

    public double valueOf(Identifier id) {
        Double v = blockValues.get(id.toString());
        if (v == null) v = blockValues.get(id.getPath());
        return v != null ? v : defaultBlockValue;
    }

    public OpBlock opOf(Identifier id) {
        OpBlock o = opBlocks.get(id.toString());
        return o != null ? o : opBlocks.get(id.getPath());
    }

    public boolean isValuable(Identifier id) {
        return blockValues.containsKey(id.toString()) || blockValues.containsKey(id.getPath()) || opOf(id) != null;
    }
}
