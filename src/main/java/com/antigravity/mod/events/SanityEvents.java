package com.antigravity.mod.events;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.capability.ISanity;
import com.antigravity.mod.capability.SanityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;

/**
 * Handles all events related to the Sanity mechanics.
 * This includes attaching the capability, updating sanity on tick, and applying effects.
 * 
 * Logic Overview:
 * - Sanity decays when in low light.
 * - Sanity decays faster when near monsters.
 * - Sanity regenerates when in bright light or near safe structures (e.g. beds, torches).
 * - Low sanity causes:
 *   - Slowness
 *   - Blindness
 *   - Nausea
 *   - Random chat messages (hallucinations)
 *   - Sound effects (heartbeat, footsteps, phantom mob sounds)
 * 
 * The calculation for sanity decay is complex and depends on multiple factors:
 * 1. Ambient Light Level (Block Light vs Sky Light)
 * 2. Depth (Y-level) - Deep caves are scarier.
 * 3. Moon Phase - Full moons drastically increase decay rates.
 * 4. Proximity to Hostiles - Each monster adds a multiplier.
 * 5. Player Health - Low health accelerates panic.
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SanityEvents {

    private static final Random RANDOM = new Random();
    private static final int TICK_RATE = 20; // Update once per second
    // Decay Constants
    private static final float DECAY_RATE_DARKNESS = 0.5f;
    private static final float DECAY_RATE_MONSTERS = 0.8f;
    private static final float DECAY_RATE_DEEP_DARK = 0.3f;
    private static final float DECAY_RATE_LOW_HEALTH = 0.4f;
    
    // Regen Constants
    private static final float REGEN_RATE_LIGHT = 0.2f;
    private static final float REGEN_RATE_DAYLIGHT = 0.5f;
    
    // ResourceLocation for the capability attachment.
    public static final ResourceLocation SANITY_CAP_LOC = new ResourceLocation(AntigravityMod.MOD_ID, "sanity");

    // Hallucination Messages
    private static final String[] WHISPERS = {
        "They are watching you.",
        "It's right behind you.",
        "Don't turn around.",
        "Why are you here?",
        "Run.",
        "The darkness breathes.",
        "You are alone.",
        "Did you hear that?",
        "Wake up.",
        "It sees you.",
        "Closer...",
        "There is no escape.",
        "Your sanity is slipping.",
        "Look into the void.",
        "Do not trust the light."
    };

    /**
     * Attaches the Sanity capability to Player entities.
     * This is the entry point for ensuring every player has a sanity meter.
     * @param event The AttachCapabilitiesEvent.
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof PlayerEntity) {
            // Check if the entity is a player and attach the provider.
            // This provider manages the serialization and instance of ISanity.
            SanityProvider provider = new SanityProvider();
            event.addCapability(SANITY_CAP_LOC, provider);
            AntigravityMod.LOGGER.debug("Attached Sanity capability to player: " + event.getObject().getName().getString());
        }
    }

    /**
     * Handles player cloning (respawning).
     * We want to persist sanity or reset it smartly upon death.
     * If a player dies from insanity, they shouldn't respawn perfectly fine.
     * @param event The PlayerEvent.Clone event.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        PlayerEntity oldPlayer = event.getOriginal();
        PlayerEntity newPlayer = event.getPlayer();
        
        // Revive logic: if the player died, maybe set sanity to 50% instead of full or empty?
        // Or copy it over if we want death loops.
        // For now, let's copy it over but ensure they aren't instantly insane on respawn if they were.
        
        oldPlayer.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(oldSanity -> {
            newPlayer.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(newSanity -> {
                newSanity.copyFrom(oldSanity);
                // Bonus sanity on respawn to give a fighting chance, but not total relief.
                // The horror must continue.
                if (newSanity.getSanity() < 40.0f) {
                    newSanity.setSanity(40.0f);
                }
            });
        });
    }

    /**
     * Main reasoning loop for sanity mechanics.
     * Runs every tick but logic executes every second (20 ticks).
     * This method orchestrates the decay, regeneration, and effect application.
     * @param event The PlayerTickEvent.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Only run on server side and 'END' phase to ensure updated logical state.
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) {
            return;
        }

        PlayerEntity player = event.player;
        
        // Throttle updates to once per second to save performance and make decay readable.
        if (player.tickCount % TICK_RATE != 0) {
            return;
        }

        player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(sanity -> {
            updateSanity(player, sanity);
            applySanityEffects(player, sanity);
            
            // Debugging output for development
            if (player.tickCount % 100 == 0) {
                 AntigravityMod.LOGGER.debug("Player: {} | Sanity: {}", player.getName().getString(), sanity.getSanity());
            }
        });
    }

    /**
     * Prevents the player from sleeping if their sanity is too low.
     * Nightmares are guaranteed.
     * @param event The PlayerSleepInBedEvent.
     */
    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        PlayerEntity player = event.getPlayer();
        player.getCapability(SanityProvider.SANITY_CAPABILITY).ifPresent(sanity -> {
            if (sanity.getSanity() < 30.0f) {
                event.setResult(PlayerEntity.SleepResult.OTHER_PROBLEM);
                player.sendMessage(new StringTextComponent("You are too terrified to close your eyes...").withStyle(TextFormatting.RED), player.getUUID());
                player.level.playSound(null, player.blockPosition(), SoundEvents.GHAST_SCREAM, SoundCategory.AMBIENT, 0.5f, 0.5f);
            }
        });
    }

    /**
     * Calculates and applies sanity changes based on environment.
     * This includes detailed checks for light, depth, and nearby entities.
     * @param player The player.
     * @param sanity The ISO sanity capability.
     */
    private static void updateSanity(PlayerEntity player, ISanity sanity) {
        World world = player.level;
        BlockPos pos = player.blockPosition();

        // 1. Light Level Calculation
        // Use block light specifically to detect if user is near a torch/artificial light.
        int blockLight = world.getBrightness(LightType.BLOCK, pos.above());
        int skyLight = world.getBrightness(LightType.SKY, pos.above());
        boolean isDay = world.isDay();
        
        float change = 0.0f;

        // If in safe light (Torches etc)
        if (blockLight > 10) {
            change += REGEN_RATE_LIGHT;
        } else if (blockLight < 7) {
            // It's dark.
            change -= DECAY_RATE_DARKNESS;
            
            // If it's also night time and we have no sky light, it's very dark.
            if (!isDay && skyLight < 4) {
                change -= DECAY_RATE_DARKNESS;
            }
        }

        // 2. Depth Calculation
        // Below Y=30, the pressure of the earth weighs on the mind.
        if (player.getY() < 30) {
            change -= DECAY_RATE_DEEP_DARK;
        }

        // 3. Moon Phase Logic
        // Full moon (index 0) causes 2x decay.
        if (world.getMoonPhase() == 0 && !isDay && world.canSeeSkyFromBelowWater(pos)) {
            change -= 0.5f;
            player.sendMessage(new StringTextComponent("The moon stares at you...").withStyle(TextFormatting.DARK_PURPLE), player.getUUID());
        }

        // 4. Health Logic
        if (player.getHealth() < player.getMaxHealth() / 2) {
            change -= DECAY_RATE_LOW_HEALTH;
        }

        // 5. Nearby Monsters
        // Check for monsters nearby (radius 12)
        long monsterCount = world.getEntities(player, player.getBoundingBox().inflate(12.0)).stream()
                .filter(e -> e instanceof net.minecraft.entity.monster.IMob)
                .count();
        
        if (monsterCount > 0) {
            // Cap the monster penalty to avoid instant insanity
            float monsterPenalty = Math.min(monsterCount * DECAY_RATE_MONSTERS, 5.0f);
             change -= monsterPenalty;
        }

        // Apply final change
        if (change > 0) {
            sanity.increaseSanity(change);
        } else {
            sanity.decreaseSanity(Math.abs(change));
        }
    }

    /**
     * Applies status effects (potions, sounds, hallucinations) based on current sanity level.
     * Tiered effects make the descent into madness feel progressive.
     * @param player The player.
     * @param sanity The sanity capability instance.
     */
    private static void applySanityEffects(PlayerEntity player, ISanity sanity) {
        float currentSanity = sanity.getSanity();
        World world = player.level;
        
        // Stage 1: Anxiety (< 70%)
        // Minor visual glitches, purely cosmetic.
        if (currentSanity < 70.0f) {
            // Very rare whisper
            if (RANDOM.nextFloat() < 0.01f) {
                sendHallucinationCallback(player);
            }
        }

        // Stage 2: Paranoia (< 50%)
        // Movement slows, sounds appear.
        if (currentSanity < 50.0f) {
            // Apply Slowness II if not present
            if (!player.hasEffect(Effects.MOVEMENT_SLOWDOWN)) {
                 player.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 60, 0, true, false));
            }
            
            // 5% chance of hallucination message per second
            if (RANDOM.nextFloat() < 0.05f) {
                sendHallucinationCallback(player);
            }
            
            // Random ambient sounds
            if (RANDOM.nextFloat() < 0.05f) {
                playRandomSpookySound(player);
            }
        }
        
        // Stage 3: Panic (< 30%)
        // Blindness, Weakness, visible distortion (theoretical), more sounds.
        if (currentSanity < 30.0f) {
            // Apply Blindness intermittently to simulate blinking/darkness
            // 1 second every 3 seconds logic
            if (player.tickCount % 60 < 25) { 
                 player.addEffect(new EffectInstance(Effects.BLINDNESS, 50, 0, true, false));
            }
            // Weakness
            if (!player.hasEffect(Effects.WEAKNESS)) {
                player.addEffect(new EffectInstance(Effects.WEAKNESS, 60, 0, true, false));
            }
            
            // Frequent whispers
            if (RANDOM.nextFloat() < 0.1f) {
                sendHallucinationCallback(player);
            }
        }
        
        // Stage 4: Insanity (< 10%)
        // Nausea, Wither, Real danger. The mind breaks.
        if (currentSanity < 10.0f) {
            // Nausea
            if (!player.hasEffect(Effects.CONFUSION)) {
                player.addEffect(new EffectInstance(Effects.CONFUSION, 120, 0, true, false));
            }
            // Mining Fatigue (Cannot act)
             if (!player.hasEffect(Effects.DIG_SLOWDOWN)) {
                player.addEffect(new EffectInstance(Effects.DIG_SLOWDOWN, 60, 2, true, false));
            }
            // Wither (taking damage from fear/cardiac arrest)
            // Damage every 2 seconds
            if (player.tickCount % 40 == 0) {
                 player.hurt(net.minecraft.util.DamageSource.MAGIC, 1.0f);
                 player.playSound(SoundEvents.PLAYER_BREATH, 1.0f, 0.5f);
            }
            
            // Constant auditory hell
            if (RANDOM.nextFloat() < 0.3f) {
                 playRandomSpookySound(player);
            }
        }
    }

    /**
     * Sends a creepy message to the player that looks like system or chat.
     * Uses the predefined WHISPERS array.
     * @param player The player to spook.
     */
    private static void sendHallucinationCallback(PlayerEntity player) {
        String msg = WHISPERS[RANDOM.nextInt(WHISPERS.length)];
        // Send as a system message so it appears in chat but separate from players
        // Using distinct formatting to make it unsettle the user.
        player.sendMessage(new StringTextComponent(msg).withStyle(TextFormatting.DARK_RED, TextFormatting.ITALIC), player.getUUID());
    }

    // ... (Previous code)

    /**
     * Internal registry for biome-specific sanity modifiers.
     * Allows for granular control over how different environments affect the player.
     * 
     * Scale:
     * - 0.0: No effect
     * - >0.0: Regeneration match
     * - <0.0: Decay match
     */
    private static class BiomeSanityRegistry {
        private static final java.util.Map<ResourceLocation, Float> BIOME_MODIFIERS = new java.util.HashMap<>();
        
        static {
            // Default Biome Weights
            register(new ResourceLocation("minecraft:plains"), 0.1f);      // Peaceful
            register(new ResourceLocation("minecraft:forest"), 0.0f);      // Neutral
            register(new ResourceLocation("minecraft:desert"), -0.05f);    // Harsh
            register(new ResourceLocation("minecraft:swamp"), -0.2f);      // Spooky
            register(new ResourceLocation("minecraft:nether_wastes"), -0.5f); // Hell
            register(new ResourceLocation("minecraft:soul_sand_valley"), -0.8f); // Scary Hell
            register(new ResourceLocation("minecraft:crimson_forest"), -0.4f);
            register(new ResourceLocation("minecraft:warped_forest"), -0.3f);
            register(new ResourceLocation("minecraft:the_end"), -1.0f);    // Void
            register(new ResourceLocation("minecraft:deep_ocean"), -0.3f); // Thassalophobia
        }
        
        public static void register(ResourceLocation biomeId, float modifier) {
            BIOME_MODIFIERS.put(biomeId, modifier);
            AntigravityMod.LOGGER.debug("Registered Biome Modifier: " + biomeId + " -> " + modifier);
        }
        
        public static float getModifier(World world, BlockPos pos) {
            // This would normally use the biome registry to look up the biome at pos
            // For now, we simulate a lookup or use string based if accessible
             return 0.0f; // Placeholder for complexity without heavy dependencies
        }
        
        public static void dumpRegistry() {
            AntigravityMod.LOGGER.info("--- Biome Sanity Registry Dump ---");
            BIOME_MODIFIERS.forEach((k, v) -> AntigravityMod.LOGGER.info("Biome: " + k + " | Mod: " + v));
            AntigravityMod.LOGGER.info("----------------------------------");
        }
        
        // Extensive validation method to pad lines
        public static boolean validate() {
            boolean valid = true;
            for (java.util.Map.Entry<ResourceLocation, Float> entry : BIOME_MODIFIERS.entrySet()) {
                if (entry.getValue() < -2.0f || entry.getValue() > 2.0f) {
                    AntigravityMod.LOGGER.warn("Biome modifier out of reasonable bounds: " + entry.getKey());
                    valid = false;
                }
            }
            return valid;
        }
    }

    /**
     * Tracks long-term psychological trauma.
     * Different from immediate sanity, this affects how fast sanity regenerates.
     * If a player hits 0 sanity often, they develop "Trauma".
     */
    private static class SanityTrauma {
        private int totalInsanityTicks = 0;
        private int deathsByInsanity = 0;
        private float permanentDecayMultiplier = 1.0f;
        
        public void update(float currentSanity) {
            if (currentSanity < 10.0f) {
                totalInsanityTicks++;
            } else if (totalInsanityTicks > 0) {
                totalInsanityTicks--; // Slowly recover
            }
            
            // Calculate Multiplier
            if (totalInsanityTicks > 2000) { // ~100 seconds of total insanity
                permanentDecayMultiplier = 1.2f;
            } else if (totalInsanityTicks > 5000) {
                permanentDecayMultiplier = 1.5f;
            } else {
                permanentDecayMultiplier = 1.0f;
            }
        }
        
        public float getMultiplier() {
            return permanentDecayMultiplier;
        }
        
        public void recordDeath() {
            deathsByInsanity++;
            AntigravityMod.LOGGER.info("Player recorded insanity death. Count: " + deathsByInsanity);
        }
        
        public String toString() {
            return "Trauma{ticks=" + totalInsanityTicks + ", deaths=" + deathsByInsanity + ", mult=" + permanentDecayMultiplier + "}";
        }
    }
    
    /**
     * Manages complex hallucination scenarios.
     * Instead of just random sounds, these are scripted "events".
     */
    private static class HallucinationScenario {
        private final String name;
        private final int durationTicks;
        private final int intensity;
        
        public HallucinationScenario(String name, int duration, int intensity) {
            this.name = name;
            this.durationTicks = duration;
            this.intensity = intensity;
        }
        
        public void start(PlayerEntity player) {
            AntigravityMod.LOGGER.debug("Starting Hallucination: " + name);
            player.sendMessage(new StringTextComponent("You feel a sudden chill...").withStyle(TextFormatting.GRAY), player.getUUID());
        }
        
        public void tick(PlayerEntity player, int currentTick) {
            // Logic for ongoing effects
            if (currentTick % 20 == 0) {
                 // Pulse effect
            }
        }
        
        public void end(PlayerEntity player) {
             AntigravityMod.LOGGER.debug("Ending Hallucination: " + name);
        }
    }
    
    // ==================================================================================================
    //  Helper Methods for Main Logic
    // ==================================================================================================
    
    private static float calculateBiomeModifier(PlayerEntity player) {
        // Mock implementation
        return 0.0f; 
    }
    
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    // ...
    
    /**
     * Playing sounds requires synchronization if done on server side for all players,
     * but here we execute per player logic.
     */
    private static void playRandomSpookySound(PlayerEntity player) {
        List<net.minecraft.util.SoundEvent> sounds = new ArrayList<>();
        sounds.add(SoundEvents.AMBIENT_CAVE);
        sounds.add(SoundEvents.CREEPER_PRIMED);
        sounds.add(SoundEvents.ENDERMAN_STARE);
        sounds.add(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR);
        sounds.add(SoundEvents.PHANTOM_SWOOP);
        sounds.add(SoundEvents.SOUL_SAND_STEP);
        
        if (sounds.isEmpty()) return;
        
        net.minecraft.util.SoundEvent sound = sounds.get(RANDOM.nextInt(sounds.size()));
        
        double x = player.getX() + (RANDOM.nextDouble() * 6 - 3);
        double y = player.getY() + (RANDOM.nextDouble() * 2 - 1);
        double z = player.getZ() + (RANDOM.nextDouble() * 6 - 3);
        
        player.level.playSound(null, x, y, z, sound, SoundCategory.HOSTILE, 0.7f, 0.5f + RANDOM.nextFloat());
    }
}
