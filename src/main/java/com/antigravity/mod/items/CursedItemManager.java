package com.antigravity.mod.items;

import com.antigravity.mod.AntigravityMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.*;

/**
 * Cursed Item Manager
 * Manages a system of cursed items that provide benefits at a cost.
 * Items can accumulate curses, and curses can spread between items.
 */
public class CursedItemManager {
    
    private static final Map<UUID, PlayerCurseData> playerCurseData = new HashMap<>();
    private static final Random random = new Random();
    
    // Global curse registry
    private static final List<CurseType> registeredCurses = new ArrayList<>();
    
    static {
        // Register all curse types
        registeredCurses.add(new CurseType.VampirismCurse());
        registeredCurses.add(new CurseType.FragilityCurse());
        registeredCurses.add(new CurseType.HungerCurse());
        registeredCurses.add(new CurseType.ParanoiaCurse());
        registeredCurses.add(new CurseType.WeightCurse());
        registeredCurses.add(new CurseType.UnluckCurse());
    }
    
    /**
     * Gets curse data for a player.
     */
    public static PlayerCurseData getPlayerData(PlayerEntity player) {
        return playerCurseData.computeIfAbsent(player.getUUID(), 
            uuid -> new PlayerCurseData(uuid));
    }
    
    /**
     * Applies curses from held items to the player.
     */
    public static void tickPlayerCurses(PlayerEntity player) {
        PlayerCurseData data = getPlayerData(player);
        
        // Scan all equipment for curses
        List<CurseInstance> activeCurses = new ArrayList<>();
        
        for (ItemStack stack : player.getAllSlots()) {
            if (!stack.isEmpty()) {
                List<CurseInstance> itemCurses = getCurses(stack);
                activeCurses.addAll(itemCurses);
            }
        }
        
        // Apply active curse effects
        for (CurseInstance curse : activeCurses) {
            curse.getType().tick(player, curse.getLevel());
        }
        
        // Track curse exposure
        data.tick(activeCurses);
    }
    
    /**
     * Attempts to curse an item.
     */
    public static boolean curseItem(ItemStack stack, CurseType type, int level) {
        if (stack.isEmpty()) return false;
        
        CompoundNBT nbt = stack.getOrCreateTagElement("Curses");
        ListNBT curseList = nbt.getList("CurseList", 10);
        
        // Check if already has this curse
        for (int i = 0; i < curseList.size(); i++) {
            CompoundNBT curseNbt = curseList.getCompound(i);
            if (curseNbt.getString("Type").equals(type.getId())) {
                // Increase level
                int currentLevel = curseNbt.getInt("Level");
                curseNbt.putInt("Level", Math.min(5, currentLevel + level));
                return true;
            }
        }
        
        // Add new curse
        CompoundNBT curseNbt = new CompoundNBT();
        curseNbt.putString("Type", type.getId());
        curseNbt.putInt("Level", level);
        curseNbt.putLong("AppliedTime", System.currentTimeMillis());
        curseList.add(curseNbt);
        
        nbt.put("CurseList", curseList);
        
        return true;
    }
    
    /**
     * Gets all curses on an item.
     */
    public static List<CurseInstance> getCurses(ItemStack stack) {
        List<CurseInstance> curses = new ArrayList<>();
        
        if (!stack.hasTag()) return curses;
        
        CompoundNBT nbt = stack.getTagElement("Curses");
        if (nbt == null) return curses;
        
        ListNBT curseList = nbt.getList("CurseList", 10);
        for (int i = 0; i < curseList.size(); i++) {
            CompoundNBT curseNbt = curseList.getCompound(i);
            String typeId = curseNbt.getString("Type");
            int level = curseNbt.getInt("Level");
            
            for (CurseType type : registeredCurses) {
                if (type.getId().equals(typeId)) {
                    curses.add(new CurseInstance(type, level));
                    break;
                }
            }
        }
        
        return curses;
    }
    
    /**
     * Checks if item is cursed.
     */
    public static boolean isCursed(ItemStack stack) {
        return !getCurses(stack).isEmpty();
    }
    
    /**
     * Gets total curse level on an item.
     */
    public static int getTotalCurseLevel(ItemStack stack) {
        return getCurses(stack).stream().mapToInt(CurseInstance::getLevel).sum();
    }
    
    /**
     * Attempts curse spread when items are stored together.
     */
    public static void attemptCurseSpread(ItemStack source, ItemStack target) {
        if (!isCursed(source) || isCursed(target)) return;
        
        List<CurseInstance> sourceCurses = getCurses(source);
        for (CurseInstance curse : sourceCurses) {
            if (random.nextDouble() < curse.getType().getSpreadChance() * curse.getLevel()) {
                curseItem(target, curse.getType(), 1);
            }
        }
    }
    
    /**
     * Get a random curse type.
     */
    public static CurseType getRandomCurse() {
        return registeredCurses.get(random.nextInt(registeredCurses.size()));
    }
    
    /**
     * Tracks curse-related data for a player.
     */
    public static class PlayerCurseData {
        private final UUID playerId;
        private int totalCurseExposure = 0;
        private int curseMitigationResistance = 0;
        private final Map<String, Integer> curseExposure = new HashMap<>();
        private long lastPurificationAttempt = 0;
        
        public PlayerCurseData(UUID id) {
            this.playerId = id;
        }
        
        public void tick(List<CurseInstance> activeCurses) {
            // Accumulate curse exposure
            for (CurseInstance curse : activeCurses) {
                String typeId = curse.getType().getId();
                int current = curseExposure.getOrDefault(typeId, 0);
                curseExposure.put(typeId, current + curse.getLevel());
                totalCurseExposure += curse.getLevel();
            }
            
            // Long-term exposure effects
            if (totalCurseExposure > 10000) {
                // Player becomes susceptible to more curse effects
                curseMitigationResistance = Math.max(0, curseMitigationResistance - 1);
            }
        }
        
        public int getTotalExposure() { return totalCurseExposure; }
        public int getMitigationResistance() { return curseMitigationResistance; }
        
        public void addMitigationResistance(int amount) {
            curseMitigationResistance = Math.min(100, curseMitigationResistance + amount);
        }
    }
    
    /**
     * Represents an instance of a curse on an item.
     */
    public static class CurseInstance {
        private final CurseType type;
        private final int level;
        
        public CurseInstance(CurseType type, int level) {
            this.type = type;
            this.level = level;
        }
        
        public CurseType getType() { return type; }
        public int getLevel() { return level; }
    }
    
    /**
     * Base class for curse types.
     */
    public static abstract class CurseType {
        protected final String id;
        protected final String name;
        protected final double spreadChance;
        
        public CurseType(String id, String name, double spreadChance) {
            this.id = id;
            this.name = name;
            this.spreadChance = spreadChance;
        }
        
        public abstract void tick(PlayerEntity player, int level);
        public abstract String[] getLoreLines(int level);
        
        public String getId() { return id; }
        public String getName() { return name; }
        public double getSpreadChance() { return spreadChance; }
        
        /**
         * Vampirism Curse - heals from damage but burns in sunlight.
         */
        public static class VampirismCurse extends CurseType {
            public VampirismCurse() {
                super("vampirism", "Curse of Vampirism", 0.1);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                // Sunlight damage
                if (player.level.isDay() && player.level.canSeeSky(player.blockPosition())) {
                    player.setSecondsOnFire(1);
                    if (player.tickCount % 20 == 0) {
                        player.hurt(DamageSource.ON_FIRE, level);
                    }
                }
                
                // Regeneration in darkness
                int light = player.level.getMaxLocalRawBrightness(player.blockPosition());
                if (light < 5 && player.tickCount % 40 == 0) {
                    player.heal(level * 0.5f);
                }
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "Burns in sunlight",
                    "Heals in darkness"
                };
            }
        }
        
        /**
         * Fragility Curse - take more damage.
         */
        public static class FragilityCurse extends CurseType {
            public FragilityCurse() {
                super("fragility", "Curse of Fragility", 0.05);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                // Effect is applied on damage, not on tick
                // Would use a damage event handler
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "You take " + (level * 10) + "% more damage"
                };
            }
        }
        
        /**
         * Hunger Curse - constantly hungry.
         */
        public static class HungerCurse extends CurseType {
            public HungerCurse() {
                super("hunger", "Curse of Starvation", 0.08);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                if (player.tickCount % 100 == 0) {
                    player.getFoodData().addExhaustion(level * 0.5f);
                }
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "Your hunger drains faster"
                };
            }
        }
        
        /**
         * Paranoia Curse - random fear effects.
         */
        public static class ParanoiaCurse extends CurseType {
            public ParanoiaCurse() {
                super("paranoia", "Curse of Paranoia", 0.15);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                if (random.nextInt(2000 / level) == 0) {
                    // Random scare effects
                    int effect = random.nextInt(3);
                    switch (effect) {
                        case 0:
                            player.displayClientMessage(
                                new StringTextComponent("Something is behind you...")
                                    .withStyle(TextFormatting.RED),
                                true
                            );
                            break;
                        case 1:
                            player.addEffect(new EffectInstance(Effects.BLINDNESS, 20, 0));
                            break;
                        case 2:
                            // Play heartbeat sound (would need sound registry)
                            break;
                    }
                }
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "You hear and see things that aren't there"
                };
            }
        }
        
        /**
         * Weight Curse - slowed movement.
         */
        public static class WeightCurse extends CurseType {
            public WeightCurse() {
                super("weight", "Curse of Burden", 0.05);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                if (player.tickCount % 40 == 0) {
                    player.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 60, level - 1));
                }
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "Your movements are slowed"
                };
            }
        }
        
        /**
         * Unluck Curse - bad things happen more often.
         */
        public static class UnluckCurse extends CurseType {
            public UnluckCurse() {
                super("unluck", "Curse of Misfortune", 0.2);
            }
            
            @Override
            public void tick(PlayerEntity player, int level) {
                if (player.tickCount % 60 == 0) {
                    player.addEffect(new EffectInstance(Effects.UNLUCK, 80, level - 1));
                }
            }
            
            @Override
            public String[] getLoreLines(int level) {
                return new String[]{
                    "Fortune does not favor you"
                };
            }
        }
    }
    
    /**
     * Altar for purifying cursed items.
     */
    public static class PurificationAltar {
        private final List<ItemStack> sacrificeItems = new ArrayList<>();
        private int purificationProgress = 0;
        private ItemStack targetItem = ItemStack.EMPTY;
        
        public boolean addSacrifice(ItemStack stack) {
            if (sacrificeItems.size() >= 4) return false;
            
            // Only non-cursed items can be sacrificed
            if (isCursed(stack)) return false;
            
            sacrificeItems.add(stack.copy());
            return true;
        }
        
        public void setTargetItem(ItemStack stack) {
            if (!isCursed(stack)) return;
            this.targetItem = stack;
        }
        
        public boolean attemptPurification() {
            if (targetItem.isEmpty() || sacrificeItems.isEmpty()) return false;
            
            List<CurseInstance> curses = getCurses(targetItem);
            if (curses.isEmpty()) return false;
            
            // Calculate purification power
            int power = 0;
            for (ItemStack sacrifice : sacrificeItems) {
                power += getSacrificeValue(sacrifice);
            }
            
            // Compare to curse strength
            int curseStrength = getTotalCurseLevel(targetItem);
            
            if (power >= curseStrength * 10) {
                // Success - remove all curses
                targetItem.removeTagKey("Curses");
                consumeSacrifices();
                return true;
            } else if (power >= curseStrength * 5) {
                // Partial - reduce curse levels
                reduceCurseLevels(targetItem);
                consumeSacrifices();
                return true;
            }
            
            // Failed - curses might spread or intensify
            return false;
        }
        
        private int getSacrificeValue(ItemStack stack) {
            // Different items have different purification values
            // This would check item rarity, enchantments, etc.
            return 10;
        }
        
        private void reduceCurseLevels(ItemStack stack) {
            CompoundNBT nbt = stack.getTagElement("Curses");
            if (nbt == null) return;
            
            ListNBT curseList = nbt.getList("CurseList", 10);
            ListNBT newList = new ListNBT();
            
            for (int i = 0; i < curseList.size(); i++) {
                CompoundNBT curseNbt = curseList.getCompound(i);
                int level = curseNbt.getInt("Level") - 1;
                if (level > 0) {
                    curseNbt.putInt("Level", level);
                    newList.add(curseNbt);
                }
            }
            
            if (newList.isEmpty()) {
                stack.removeTagKey("Curses");
            } else {
                nbt.put("CurseList", newList);
            }
        }
        
        private void consumeSacrifices() {
            sacrificeItems.clear();
        }
        
        public void reset() {
            sacrificeItems.clear();
            targetItem = ItemStack.EMPTY;
            purificationProgress = 0;
        }
    }
}
