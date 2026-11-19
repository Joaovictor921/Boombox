package com.boombox.client.render;

import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import com.boombox.client.ClientBoomboxManager;
import com.boombox.entity.MountedSpeaker;
import com.boombox.net.Payloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Paredão no barco/carrinho: o mesmo modelo animado das caixas, virado para a frente do veículo. */
public class MountedSpeakerRenderer extends EntityRenderer<MountedSpeaker, MountedSpeakerRenderer.State> {
    public static class State extends EntityRenderState {
        public SpeakerType type = SpeakerType.PARTY_LARGE;
        public float yaw, bass, treble;
        public SpeakerSettings settings = SpeakerSettings.DEFAULT;
    }

    private final float[] levels = new float[2];

    public MountedSpeakerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MountedSpeaker e, State s, float partialTick) {
        super.extractRenderState(e, s, partialTick);
        s.type = e.speakerType();
        s.yaw = e.getYRot(partialTick);
        String key = Payloads.entityKey(e.getUUID());
        ClientBoomboxManager.levelsForKey(key, levels);
        s.bass = levels[0];
        s.treble = levels[1];
        s.settings = ClientBoomboxManager.settingsForKey(key);
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        ps.pushPose();
        ps.rotateDegrees(Axis.YP, 180f - s.yaw);
        float scale = s.type == SpeakerType.PARTY_LARGE ? 0.8f : 1f;
        ps.scale(scale, scale, scale);
        ps.translate(-0.5f, 0f, -0.5f);
        SpeakerRendering.submit(s.type, ps, collector, s.lightCoords, OverlayTexture.NO_OVERLAY, s.bass, s.treble, s.settings);
        ps.popPose();
        super.submit(s, ps, collector, camera);
    }
}
