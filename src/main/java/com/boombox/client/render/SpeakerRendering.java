package com.boombox.client.render;

import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Escolhe o modelo certo para cada tipo de caixa. */
public final class SpeakerRendering {
    private SpeakerRendering() {}

    public static void submit(SpeakerType type, PoseStack poseStack, SubmitNodeCollector collector,
                              int light, int overlay, float bass, float treble) {
        submit(type, poseStack, collector, light, overlay, bass, treble, 0f, SpeakerSettings.DEFAULT);
    }

    public static void submit(SpeakerType type, PoseStack poseStack, SubmitNodeCollector collector,
                              int light, int overlay, float bass, float treble, SpeakerSettings settings) {
        submit(type, poseStack, collector, light, overlay, bass, treble, 0f, settings);
    }

    public static void submit(SpeakerType type, PoseStack poseStack, SubmitNodeCollector collector,
                              int light, int overlay, float bass, float treble, float wheelAngle,
                              SpeakerSettings settings) {
        SpeakerSettings st = settings == null ? SpeakerSettings.DEFAULT : settings;
        if (type.isParty()) {
            collector.submitCustomGeometry(poseStack, PartyGeometry.RENDER_TYPE,
                    (pose, vc) -> PartyGeometry.draw(type, pose, vc, light, overlay, bass, treble, wheelAngle, st));
        } else {
            collector.submitCustomGeometry(poseStack, BoomboxGeometry.RENDER_TYPE,
                    (pose, vc) -> BoomboxGeometry.draw(pose, vc, light, overlay, bass, treble));
        }
    }

    public static float[][] extents(SpeakerType type) {
        return type.isParty() ? PartyGeometry.extents(type) : BoomboxGeometry.EXTENTS;
    }
}
