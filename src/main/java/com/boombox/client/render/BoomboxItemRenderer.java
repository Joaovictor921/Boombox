package com.boombox.client.render;

import com.boombox.ModBlocks;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** Desenha o item da boombox (inventário, primeira pessoa, no chão) com o mesmo modelo 3D animado. */
public class BoomboxItemRenderer implements SpecialModelRenderer<float[]> {

    public record Unbaked() implements SpecialModelRenderer.Unbaked<float[]> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<float[]> bake(SpecialModelRenderer.BakingContext ctx) {
            return new BoomboxItemRenderer();
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<float[]>> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public float[] extractArgument(ItemStack stack) {
        float[] levels = new float[2];
        ClientBoomboxManager.levelsForSong(stack.get(ModBlocks.SONG), levels);
        return levels;
    }

    @Override
    public void submit(float[] levels, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean hasFoil, int outlineColor) {
        float bass = levels == null ? 0 : levels[0];
        float treble = levels == null ? 0 : levels[1];
        collector.submitCustomGeometry(poseStack, BoomboxGeometry.RENDER_TYPE,
                (pose, vc) -> BoomboxGeometry.draw(pose, vc, light, overlay, bass, treble));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float[] a = BoomboxGeometry.EXTENTS[0], b = BoomboxGeometry.EXTENTS[1];
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f((i & 1) == 0 ? a[0] : b[0], (i & 2) == 0 ? a[1] : b[1], (i & 4) == 0 ? a[2] : b[2]));
        }
    }
}
