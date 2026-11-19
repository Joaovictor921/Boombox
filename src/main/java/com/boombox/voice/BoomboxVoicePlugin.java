package com.boombox.voice;

import com.boombox.BoomboxBlockEntity;
import com.boombox.BoomboxMod;
import com.boombox.ModBlocks;
import com.boombox.entity.MountedSpeaker;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Microfone (precisa do mod Simple Voice Chat): segurando o microfone, a sua voz também sai pelas caixas de som
 * perto de você (até 32 blocos, no máximo 6 caixas), como num show. Caixas no Modo DJ de outra pessoa ficam de fora.
 * Sem o Simple Voice Chat instalado esta classe nunca é carregada.
 */
public class BoomboxVoicePlugin implements VoicechatPlugin {
    private static final double RADIUS = 32;
    private static final int MAX_SPEAKERS = 6;

    /** Canal de som de cada caixa (a voz sai da posição dela). */
    private final Map<String, LocationalAudioChannel> channels = new ConcurrentHashMap<>();
    /** Caixas de cada jogador, recalculadas a cada segundo (procurar a cada pacote de voz seria pesado). */
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Spot(String key, Vec3 pos) {}

    private record Cached(long time, List<Spot> spots) {}

    @Override
    public String getPluginId() {
        return BoomboxMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMic);
    }

    private void onMic(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null) return;
        if (!(event.getSenderConnection().getPlayer().getPlayer() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(ModBlocks.MICROPHONE) && !player.getOffhandItem().is(ModBlocks.MICROPHONE)) return;
        VoicechatServerApi api = event.getVoicechat();
        byte[] opus = event.getPacket().getOpusEncodedData();
        ServerLevel level = player.level();
        for (Spot s : speakers(player)) {
            LocationalAudioChannel ch = channels.computeIfAbsent(s.key, k -> {
                LocationalAudioChannel c = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(level),
                        api.createPosition(s.pos.x, s.pos.y, s.pos.z));
                if (c != null) c.setDistance(48f);
                return c;
            });
            if (ch == null) continue;
            ch.updateLocation(api.createPosition(s.pos.x, s.pos.y, s.pos.z));
            ch.send(opus);
        }
    }

    /** Caixas perto do jogador que ele pode usar (no máximo 6, as mais perto). */
    private List<Spot> speakers(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Cached c = cache.get(player.getUUID());
        if (c != null && now - c.time < 1000) return c.spots;
        ServerLevel level = player.level();
        List<Spot> found = new ArrayList<>();
        BlockPos center = player.blockPosition();
        int cx = center.getX() >> 4, cz = center.getZ() >> 4, r = (int) (RADIUS / 16) + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (e.getValue() instanceof BoomboxBlockEntity be && be.settings().canControl(player.getUUID())) {
                        Vec3 p = Vec3.atCenterOf(e.getKey());
                        if (p.distanceTo(player.position()) <= RADIUS) found.add(new Spot("b" + e.getKey().asLong(), p));
                    }
                }
            }
        }
        for (MountedSpeaker ms : level.getEntitiesOfClass(MountedSpeaker.class, new AABB(center).inflate(RADIUS))) {
            if (ms.settings().canControl(player.getUUID())) found.add(new Spot("e" + ms.getUUID(), ms.position().add(0, 0.7, 0)));
        }
        found.sort(Comparator.comparingDouble(s -> s.pos.distanceToSqr(player.position())));
        List<Spot> spots = found.size() > MAX_SPEAKERS ? found.subList(0, MAX_SPEAKERS) : found;
        cache.put(player.getUUID(), new Cached(now, spots));
        return spots;
    }

    /** Usado por quem não tem o mod: nada. */
    public static boolean holdsMic(Player p) {
        return p.getMainHandItem().is(ModBlocks.MICROPHONE) || p.getOffhandItem().is(ModBlocks.MICROPHONE);
    }
}
