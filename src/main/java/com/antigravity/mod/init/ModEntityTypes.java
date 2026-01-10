package com.antigravity.mod.init;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.entity.HollowEntity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Registers all entities for the Antigravity mod.
 */
public class ModEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITIES, AntigravityMod.MOD_ID);

    // Register The Hollow entity
    public static final RegistryObject<EntityType<HollowEntity>> HOLLOW = ENTITY_TYPES.register("hollow",
            () -> EntityType.Builder.of(HollowEntity::new, EntityClassification.MONSTER)
                    .sized(0.6f, 1.95f) // Player size
                    .clientTrackingRange(80)
                    .build(new ResourceLocation(AntigravityMod.MOD_ID, "hollow").toString()));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
