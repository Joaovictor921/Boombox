package com.boombox.client;

import com.boombox.bar.SinucaBlock;
import com.boombox.bar.SinucaBlockEntity;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Modo sinuca no cliente, como num jogo de sinuca 3D: clicou na mesa, a câmera vai para trás da branca e fica
 * presa na mesa. O mouse gira a mira (e sobe/desce a câmera); segure o botão direito para puxar o taco e solte para
 * tacar. Enquanto as bolas andam (ou é a vez do outro) a câmera sobe e mostra a mesa de cima. Agachar sai.
 */
public final class PoolClient {
    private static BlockPos table;
    private static boolean charging, wasUseDown;
    private static int chargeTicks;
    private static float power;
    /** Câmera: posição e giro do tick anterior e do atual (para interpolar). */
    private static Vec3 camPrev, camNow;
    private static float yawPrev, yawNow, pitchPrev, pitchNow;

    private PoolClient() {}

    public static boolean active() {
        return table != null;
    }

    /** Clique na mesa (fora do modo sinuca): pega o taco. */
    public static void click(BlockPos main) {
        if (table != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level.getBlockEntity(main) instanceof SinucaBlockEntity)) return;
        table = main.immutable();
        charging = false;
        wasUseDown = true; // o próprio clique que abriu não conta como tacada
        power = 0;
        camNow = null;
        mc.player.sendOverlayMessage(Component.literal("§aMouse: mirar  •  segure o botão direito: puxar o taco  •  solte: tacar  •  agachar: sair"));
    }

    public static void stop() {
        table = null;
        charging = false;
        camNow = null;
    }

    public static float aimPower() {
        return charging ? power : 0;
    }

    private static SinucaBlockEntity be(Minecraft mc) {
        return table != null && mc.level != null && mc.level.getBlockEntity(table) instanceof SinucaBlockEntity b ? b : null;
    }

    private static Direction facing(Minecraft mc) {
        var st = mc.level.getBlockState(table);
        return st.hasProperty(SinucaBlock.FACING) ? st.getValue(SinucaBlock.FACING) : Direction.NORTH;
    }

    /** É a minha vez de tacar nesta mesa? */
    public static boolean myTurn(Minecraft mc, SinucaBlockEntity be) {
        if (be.moving || be.winner >= 0 || be.in[0]) return false;
        String t = be.turnName();
        return t.isEmpty() || t.equals(mc.player.getName().getString());
    }

    /** Ângulo da mira nas coordenadas da mesa (segue para onde o jogador olha); NaN se não estou mirando nesta mesa. */
    public static double aimAngle(BlockPos main) {
        Minecraft mc = Minecraft.getInstance();
        if (table == null || !table.equals(main) || mc.player == null) return Double.NaN;
        SinucaBlockEntity be = be(mc);
        if (be == null || !myTurn(mc, be)) return Double.NaN;
        return localAngle(mc.player.getYRot(), facing(mc));
    }

    /** Direção do mundo (yaw) → ângulo na mesa. */
    private static double localAngle(float yaw, Direction f) {
        double r = Math.toRadians(yaw), wx = -Math.sin(r), wz = Math.cos(r);
        Direction X = SinucaBlock.xDir(f), Z = SinucaBlock.zDir(f);
        return Math.atan2(wx * Z.getStepX() + wz * Z.getStepZ(), wx * X.getStepX() + wz * X.getStepZ());
    }

    /** Ponto da mesa (em blocos) → mundo. */
    private static Vec3 world(Direction f, double lx, double ly, double lz) {
        Direction X = SinucaBlock.xDir(f), Z = SinucaBlock.zDir(f);
        double dx = lx - 0.5, dz = lz - 0.5;
        return new Vec3(table.getX() + 0.5 + dx * X.getStepX() + dz * Z.getStepX(), table.getY() + ly,
                table.getZ() + 0.5 + dx * X.getStepZ() + dz * Z.getStepZ());
    }

    /** Começo do tick: tacadas pelo botão direito (o jogo não usa o botão enquanto você está na mesa). */
    public static void startTick(Minecraft mc) {
        if (table == null) return;
        boolean down = mc.options.keyUse.isDown();
        while (mc.options.keyUse.consumeClick()) { }
        while (mc.options.keyAttack.consumeClick()) { }
        SinucaBlockEntity be = be(mc);
        if (be != null && myTurn(mc, be)) {
            if (down && !wasUseDown) {
                charging = true;
                chargeTicks = 0;
            }
            if (charging && down) {
                chargeTicks++;
                power = Math.min(1f, chargeTicks / 30f);
            }
            if (charging && !down) {
                ClientPlayNetworking.send(new Payloads.SinucaShot(table, (float) localAngle(mc.player.getYRot(), facing(mc)), Math.max(0.03f, power)));
                charging = false;
                power = 0;
            }
        } else {
            charging = false;
        }
        wasUseDown = down;
    }

    /** Fim do tick: sai se precisar, atualiza a câmera e o placar. */
    public static void endTick(Minecraft mc) {
        if (table == null) return;
        SinucaBlockEntity be = be(mc);
        if (be == null || mc.player == null || mc.player.isShiftKeyDown() || mc.player.blockPosition().distSqr(table) > 8 * 8 || mc.gui.screen() != null) {
            stop();
            if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("Taco guardado"));
            return;
        }
        Direction f = facing(mc);
        double top = 13.2 / 16 + SinucaBlockEntity.R;
        float yaw = mc.player.getYRot();
        float pitch = Mth.clamp(mc.player.getXRot(), 5f, 80f);
        Vec3 target;
        float tYaw = yaw, tPitch;
        if (myTurn(mc, be)) {
            // atrás da branca, olhando na direção da mira; o mouse para cima/baixo sobe e desce a câmera
            Vec3 cue = world(f, be.x[0], top, be.z[0]);
            double r = Math.toRadians(yaw), p = Math.toRadians(pitch), dist = 0.75 + pitch / 80.0 * 0.6;
            Vec3 back = new Vec3(Math.sin(r), 0, -Math.cos(r));
            target = cue.add(back.scale(Math.cos(p) * dist)).add(0, Math.sin(p) * dist + 0.04, 0);
            tPitch = pitch;
        } else {
            // bolas andando ou vez do outro: vista de cima da mesa inteira, do lado em que você está
            Vec3 center = world(f, SinucaBlockEntity.X_MAX / 2, top, SinucaBlockEntity.Z_MAX / 2);
            double r = Math.toRadians(yaw);
            Vec3 back = new Vec3(Math.sin(r), 0, -Math.cos(r));
            target = center.add(back.scale(1.1)).add(0, 2.1, 0);
            tPitch = 62f;
        }
        if (camNow == null) {
            camNow = mc.player.getEyePosition();
            yawNow = yaw;
            pitchNow = mc.player.getXRot();
        }
        camPrev = camNow;
        yawPrev = yawNow;
        pitchPrev = pitchNow;
        camNow = camNow.lerp(target, 0.45);
        yawNow = yawNow + Mth.wrapDegrees(tYaw - yawNow) * 0.6f;
        pitchNow = pitchNow + (tPitch - pitchNow) * 0.45f;

        // placar na barra de ação
        String me = mc.player.getName().getString();
        int mine = me.equals(be.names[1]) && !me.equals(be.names[0]) ? 1 : 0;
        StringBuilder sb = new StringBuilder();
        if (be.winner >= 0) {
            sb.append("§6").append(be.message);
        } else {
            int g = be.group[mine];
            sb.append(g == 0 ? "§fMesa aberta" : "§fVocê: §e" + SinucaBlockEntity.groupName(g) + " §7(" + be.remaining(g) + ")"
                    + (be.remaining(g) == 0 ? " §c→ mate a 8!" : ""));
            if (myTurn(mc, be)) {
                int bars = Math.round(power * 20);
                sb.append("   §aForça §2").append("|".repeat(bars)).append("§8").append("|".repeat(20 - bars));
            } else {
                sb.append("   §7").append(be.message);
            }
        }
        mc.player.sendOverlayMessage(Component.literal(sb.toString()));
    }

    /** Câmera da mesa (null = câmera normal). */
    public static Vec3 cameraPos(float pt) {
        if (table == null || camNow == null || camPrev == null) return null;
        return camPrev.lerp(camNow, pt);
    }

    public static float cameraYaw(float pt) {
        return yawPrev + Mth.wrapDegrees(yawNow - yawPrev) * pt;
    }

    public static float cameraPitch(float pt) {
        return Mth.lerp(pt, pitchPrev, pitchNow);
    }
}
