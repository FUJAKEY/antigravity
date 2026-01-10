package com.antigravity.mod.client.render;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.entity.HollowEntity;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HollowRenderer extends BipedRenderer<HollowEntity, BipedModel<HollowEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AntigravityMod.MOD_ID, "textures/entity/hollow.png");

    public HollowRenderer(EntityRendererManager renderManager) {
        super(renderManager, new BipedModel<>(0.0F), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(HollowEntity entity) {
        return TEXTURE;
    }
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(com.antigravity.mod.init.ModEntityTypes.HOLLOW.get(), HollowRenderer::new);
    }
}
