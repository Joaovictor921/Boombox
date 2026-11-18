package com.boombox.client.render;

import com.boombox.ModBlocks;
import com.boombox.SpeakerSettings;
import com.boombox.client.ClientBoomboxManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.boombox.SpeakerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** Desenha o item da boombox / caixa de festa (inventário, primeira pessoa, no chão) com o mesmo modelo 3D animado. */
public class BoomboxItemRenderer implements SpecialModelRenderer<BoomboxItemRenderer.Args> {
    /** O que o desenho do item precisa: grave/agudo e as configurações (luz, cor). */
    public record Args(float bass, float treble, SpeakerSettings settings) {}


    /** No JSON do item: {"type": "boombox:boombox", "speaker": "party_speaker_large"} (padrão: boombox). */
    public record Unbaked(String speaker) implements SpecialModelRenderer.Unbaked<Args> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("speaker", "boombox").forGetter(Unbaked::speaker)
        ).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Args> bake(SpecialModelRenderer.BakingContext ctx) {
            return new BoomboxItemRenderer(SpeakerType.byId(speaker));
        }

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<Args>> type() {
            return MAP_CODEC;
        }
    }

    private final SpeakerType type;

    public BoomboxItemRenderer(SpeakerType type) {
        this.type = type;
    }

    @Override
    public Args extractArgument(ItemStack stack) {
        float[] levels = new float[2];
        ClientBoomboxManager.levelsForSong(stack.get(ModBlocks.SONG), levels);
        return new Args(levels[0], levels[1], stack.getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT));
    }

    @Override
    public void submit(Args args, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                       boolean hasFoil, int outlineColor) {
        float bass = args == null ? 0 : args.bass();
        float treble = args == null ? 0 : args.treble();
        SpeakerRendering.submit(type, poseStack, collector, light, overlay, bass, treble,
                args == null ? SpeakerSettings.DEFAULT : args.settings());
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float[][] ext = SpeakerRendering.extents(type);
        float[] a = ext[0], b = ext[1];
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f((i & 1) == 0 ? a[0] : b[0], (i & 2) == 0 ? a[1] : b[1], (i & 4) == 0 ? a[2] : b[2]));
        }
    }
}
