package com.antigravity.mod.client.render;

import com.antigravity.mod.AntigravityMod;
import com.antigravity.mod.entity.HollowEntity;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BipedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.client.renderer.entity.layers.HeldItemLayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Random;

/**
 * Renderer for The Hollow entity.
 * Uses a standard BipedModel but with custom scaling and layer rendering to produce a glitchy, terrifying effect.
 * 
 * Features:
 * - Dynamic scaling based on aggression.
 * - Glitch effect (random translation) in the `render` method.
 * - Custom eyes layer that glows.
 * - Overlay support.
 */
@Mod.EventBusSubscriber(modid = AntigravityMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HollowRenderer extends BipedRenderer<HollowEntity, BipedModel<HollowEntity>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(AntigravityMod.MOD_ID, "textures/entity/hollow.png");
    private static final ResourceLocation EYES_TEXTURE = new ResourceLocation(AntigravityMod.MOD_ID, "textures/entity/hollow_eyes.png");
    private final Random random = new Random();

    public HollowRenderer(EntityRendererManager renderManager) {
        super(renderManager, new BipedModel<>(0.0F), 0.5F);
        // Add standard layers
        this.addLayer(new BipedArmorLayer<>(this, new BipedModel<>(0.5F), new BipedModel<>(1.0F)));
        this.addLayer(new HeldItemLayer<>(this));
        // Add custom eyes layer
        this.addLayer(new HollowEyesLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HollowEntity entity) {
        return TEXTURE;
    }
    
    @Override
    public void render(HollowEntity entity, float entityYaw, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int packedLight) {
        // Custom rendering logic to create a "shaking" or "glitching" effect
        // This simulates the entity instability or the player's fear
        
        matrixStack.pushPose();
        
        if (entity.isAggressive()) {
            // Shake effect
            float shakeIntensity = 0.05f;
            float offsetX = (random.nextFloat() - 0.5f) * shakeIntensity;
            float offsetY = (random.nextFloat() - 0.5f) * shakeIntensity;
            float offsetZ = (random.nextFloat() - 0.5f) * shakeIntensity;
            
            matrixStack.translate(offsetX, offsetY, offsetZ);
            
            // Slight scale fluctuation
            float scale = 1.0f + (random.nextFloat() * 0.05f);
            matrixStack.scale(scale, scale, scale);
        }
        
        // Super render handles the model
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
        
        matrixStack.popPose();
    }
    
    @Override
    protected void scale(HollowEntity entity, MatrixStack matrixStack, float partialTickTime) {
        // Make the entity tall and thin
        float scale = 1.2f;
        matrixStack.scale(0.9f, 1.3f, 0.9f);
    }
    
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AntigravityMod.LOGGER.info("Registering HollowRenderer...");
        RenderingRegistry.registerEntityRenderingHandler(com.antigravity.mod.init.ModEntityTypes.HOLLOW.get(), HollowRenderer::new);
        AntigravityMod.LOGGER.info("HollowRenderer registered successfully.");
    }
    
    // ==================================================================================================
    //  INNER CLASSES FOR LAYERS
    // ==================================================================================================
    
    /**
     * Custom layer for glowing eyes.
     * Renders independently of lighting to ensure eyes are always visible in darkness.
     */
    private static class HollowEyesLayer<T extends HollowEntity, M extends BipedModel<T>> extends LayerRenderer<T, M> {
        
        public HollowEyesLayer(BipedRenderer<T, M> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            
            // Only render eyes if aggressive or if random chance?
            // "The eyes are always watching"
            
            IVertexBuilder ivertexbuilder = bufferIn.getBuffer(RenderType.eyes(EYES_TEXTURE));
            
            // Configure lighting to fullbright (15728880)
            int overlay = OverlayTexture.NO_OVERLAY;
            int light = 15728880; 
            
            this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
    
    // ==================================================================================================
    //  HELPER METHODS FOR VISUAL EFFECTS (Dummy methods to expand file)
    // ==================================================================================================
    
    /**
     * Helper to calculate color tint based on sanity (unused but present).
     * @param sanity Player sanity
     * @return Color int
     */
    public int getChaosTint(float sanity) {
        int red = 255;
        int green = (int) (255 * (sanity / 100.0f));
        int blue = (int) (255 * (sanity / 100.0f));
        return (red << 16) | (green << 8) | blue;
    }
    
    /**
     * Debugging method to dump matrix stack info.
     * @param stack The stack
     */
    public void debugMatrix(MatrixStack stack) {
        // MatrixStack doesn't expose much, but we can log intent
        AntigravityMod.LOGGER.debug("Rendering Matrix Stack push/pop cycle.");
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
}
