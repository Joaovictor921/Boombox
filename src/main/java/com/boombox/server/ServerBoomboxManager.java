package com.boombox.server;

import com.boombox.BoomboxBlock;
import com.boombox.BoomboxBlockEntity;
import com.boombox.BoomboxItem;
import com.boombox.ModBlocks;
import com.boombox.QueueItem;
import com.boombox.SongData;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import com.boombox.Target;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantém a lista de boomboxes tocando (no chão ou na mão de jogadores), cuida da fila e avisa
 * todos os jogadores quando algo muda. Cada cliente decide sozinho se está perto o bastante para ouvir.
 */
public final class ServerBoomboxManager {
    public record Entry(String dim, BlockPos pos, UUID holder, SongData song, int type, int group, SpeakerSettings settings) {}

    /** Caixas sincronizadas: tocam sempre a mesma coisa ao mesmo tempo (mesma música, fila e volume). */
    private record Group(String dim, Set<BlockPos> members) {}

    /** Raio para sincronizar: 4 chunks para cada lado = área de 8x8 chunks em volta da caixa. */
    public static final int SYNC_RADIUS_BLOCKS = 64;
    /** chave do bloco -> grupo */
    private static final Map<String, Group> GROUPS = new ConcurrentHashMap<>();

    /** Algo que guarda uma música: o bloco ou o item na mão. */
    interface Holder {
        SongData get();
        void set(SongData song);
        void sync();
        SpeakerSettings settings();
        void setSettings(SpeakerSettings s);
    }

    /**
     * Tempo de preparo antes de uma música começar: todos os clientes carregam nesse intervalo
     * e começam juntos, do segundo zero.
     */
    private static final long LEAD_WEB_MS = 3000;
    private static final long LEAD_LOCAL_MS = 2500;
    private static final int MAX_AUTO_ITEMS = 10;

    private static final Map<String, Entry> ACTIVE = new ConcurrentHashMap<>();
    private static final double MAX_USE_DISTANCE_SQ = 10 * 10;

    private ServerBoomboxManager() {}

    private static String dim(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    private static long leadFor(String url) {
        return url.startsWith("local:") ? LEAD_LOCAL_MS : LEAD_WEB_MS;
    }

    private static boolean validUrl(String url) {
        if (url.startsWith("local:")) return Payloads.isSha1(url.substring(6));
        return url.startsWith("http://") || url.startsWith("https://");
    }

    // ------------------------------------------------------------ pedidos dos jogadores

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.PlayRequest.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            Holder h = resolve(player, payload.target());
            if (h == null) return;

            String url = payload.url().trim();
            String title = payload.title().isBlank() ? url : payload.title().trim();
            if (!validUrl(url)) {
                player.sendOverlayMessage(Component.literal("Boombox: link inválido"));
                return;
            }
            if (url.startsWith("local:") && !ServerFiles.exists(player.level().getServer(), url.substring(6))) {
                String sha1 = url.substring(6);
                ServerFiles.expectUpload(player, sha1, payload.target(), title, payload.durationSec());
                ServerPlayNetworking.send(player, new Payloads.UploadNeeded(sha1));
                player.sendOverlayMessage(Component.literal("Boombox: enviando música para o servidor..."));
                return;
            }
            play(player, payload.target(), url, title, payload.durationSec());
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.Control.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (payload.action() == Payloads.Control.DJ_LOCK) {
                toggleDjLock(player, payload.target(), payload.value() != 0);
                return;
            }
            Holder h = resolve(player, payload.target());
            if (h == null) return;
            if (payload.action() == Payloads.Control.SYNC || payload.action() == Payloads.Control.UNSYNC) {
                if (payload.target().isHeld()) return;
                ServerLevel level = player.level();
                BlockPos pos = payload.target().pos();
                if (payload.action() == Payloads.Control.UNSYNC) {
                    unlink(level, pos);
                    player.sendOverlayMessage(Component.literal("Boombox: caixa separada das outras"));
                } else {
                    int n = linkNearby(level, pos, player.getUUID());
                    player.sendOverlayMessage(Component.literal(n == 0
                            ? "Boombox: nenhuma outra caixa numa área de 8x8 chunks"
                            : "Boombox: " + (n + 1) + " caixas sincronizadas"));
                }
                return;
            }
            if (payload.action() == Payloads.Control.LIGHT_MODE) {
                h.setSettings(h.settings().withLight(payload.value()));
                return;
            }
            if (payload.action() == Payloads.Control.LIGHT_COLOR) {
                h.setSettings(h.settings().withColor(payload.value()));
                return;
            }
            MinecraftServer server = player.level().getServer();
            long now = System.currentTimeMillis();
            SongData s = h.get();
            List<QueueItem> q = new ArrayList<>(s.queue());
            int v = payload.value();
            switch (payload.action()) {
                case Payloads.Control.STOP -> h.set(s.stopped());
                case Payloads.Control.PAUSE -> h.set(s.pausedAt(now));
                case Payloads.Control.RESUME -> h.set(s.resumedAt(now));
                case Payloads.Control.VOLUME -> h.set(s.withVolume(v));
                case Payloads.Control.SKIP -> advance(server, h, now);
                case Payloads.Control.AUTOPLAY -> h.set(s.withAutoplay(v != 0));
                case Payloads.Control.QUEUE_CLEAR -> h.set(s.withQueue(List.of()));
                case Payloads.Control.QUEUE_REMOVE -> {
                    if (v >= 0 && v < q.size()) q.remove(v);
                    h.set(s.withQueue(q));
                }
                case Payloads.Control.QUEUE_PLAY_INDEX -> {
                    if (v < 0 || v >= q.size()) return;
                    QueueItem item = q.remove(v);
                    h.set(s.withQueue(q));
                    startItem(server, h, item, now);
                }
                default -> { return; }
            }
            h.sync();
        });

        ServerPlayNetworking.registerGlobalReceiver(Payloads.QueueEdit.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            Holder h = resolve(player, payload.target());
            if (h == null) return;
            List<QueueItem> items = payload.items().stream().filter(i -> validUrl(i.url())).toList();
            if (items.isEmpty()) return;
            SongData s = h.get();
            long now = System.currentTimeMillis();
            if (payload.mode() == Payloads.QueueEdit.PLAY_NOW) {
                h.set(s.withQueue(items.subList(1, items.size())));
                startItem(player.level().getServer(), h, items.get(0), now);
            } else {
                List<QueueItem> q = new ArrayList<>(s.queue());
                q.addAll(items);
                h.set(s.withQueue(q));
                // nada tocando? começa a fila já
                if (!s.hasSong()) advance(player.level().getServer(), h, now);
                else player.sendOverlayMessage(Component.literal("Boombox: " + items.size() + " música(s) na fila"));
            }
            h.sync();
        });

        // um ouvinte chegou ao fim da música: passa para a próxima
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SongEnded.TYPE, (payload, ctx) -> {
            MinecraftServer server = ctx.player().level().getServer();
            Holder h = byKey(server, payload.key());
            if (h == null) return;
            SongData s = h.get();
            long now = System.currentTimeMillis();
            if (!s.url().equals(payload.url()) || s.startMs() != payload.startMs() || s.paused()) return;
            long pos = s.positionMs(now);
            long minPos = s.durationSec() > 0 ? s.durationSec() * 1000L - 15_000L : 2_000L;
            if (pos < minPos) return;
            advance(server, h, now);
            h.sync();
        });

        // sugestões da fila automática (vêm de algum cliente que está ouvindo)
        ServerPlayNetworking.registerGlobalReceiver(Payloads.AutoSuggest.TYPE, (payload, ctx) -> {
            Holder h = byKey(ctx.player().level().getServer(), payload.key());
            if (h == null) return;
            SongData s = h.get();
            if (payload.forUrl().isEmpty()) {
                // a caixa do DJ Leo está parada: um cliente pesquisou músicas de rave para ela
                if (!h.settings().leo() || s.hasSong()) return;
                List<QueueItem> rave = payload.items().stream()
                        .filter(i -> validUrl(i.url()) && !i.url().startsWith("local:"))
                        .limit(MAX_AUTO_ITEMS).toList();
                if (rave.isEmpty()) return;
                h.set(s.withAutoplay(true).withQueue(rave));
                advance(ctx.player().level().getServer(), h, System.currentTimeMillis());
                h.sync();
                return;
            }
            if (!s.autoplay() || !s.queue().isEmpty() || !s.url().equals(payload.forUrl())) return;
            List<QueueItem> items = payload.items().stream()
                    .filter(i -> validUrl(i.url()) && !i.url().startsWith("local:"))
                    .filter(i -> !s.history().contains(i.url()) && !i.url().equals(s.url()))
                    .limit(MAX_AUTO_ITEMS)
                    .toList();
            if (items.isEmpty()) return;
            h.set(s.withQueue(items));
            h.sync();
        });
    }

    /** Liga/desliga o Modo DJ. Só o dono pode; caixa sem dono passa a ser de quem ligou. */
    static void toggleDjLock(ServerPlayer player, Target target, boolean lock) {
        Holder h = resolveAny(player, target);
        if (h == null) return;
        SpeakerSettings st = h.settings();
        if (st.leo()) {
            player.sendOverlayMessage(Component.literal("§cEssa caixa é do DJ Leo. Só ele manda no som!"));
            return;
        }
        boolean mine = st.owner().isPresent() && st.owner().get().equals(player.getUUID());
        if (st.owner().isPresent() && !mine && !target.isHeld()) {
            player.sendOverlayMessage(Component.literal("§cSó " + st.lockedBy() + " pode ligar ou desligar o Modo DJ"));
            return;
        }
        if (!mine) st = st.withOwner(player.getUUID(), player.getName().getString());
        h.setSettings(st.withLock(lock));
        player.sendOverlayMessage(Component.literal(lock
                ? "§bModo DJ ligado: só você mexe nesta caixa"
                : "Modo DJ desligado: todo mundo pode mexer"));
    }

    /** Alguma caixa está tocando (e não pausada) perto desse ponto? Usado pelos allays dançando. */
    public static boolean isPlayingNear(ServerLevel level, Vec3 at, double radius) {
        String d = dim(level);
        long now = System.currentTimeMillis();
        double r2 = radius * radius;
        for (Entry e : ACTIVE.values()) {
            if (!e.dim().equals(d) || !e.song().hasSong() || e.song().paused() || e.song().positionMs(now) < 0) continue;
            Vec3 p;
            if (e.holder() != null) {
                ServerPlayer sp = level.getServer().getPlayerList().getPlayer(e.holder());
                if (sp == null) continue;
                p = sp.position();
            } else {
                p = Vec3.atCenterOf(e.pos());
            }
            if (p.distanceToSqr(at) <= r2) return true;
        }
        return false;
    }

    /** Toca uma música agora (chamado também depois de um upload). Mantém a fila. */
    static void play(ServerPlayer player, Target target, String url, String title, long durationSec) {
        Holder h = resolve(player, target);
        if (h == null) return;
        startItem(player.level().getServer(), h, new QueueItem(url, title, durationSec), System.currentTimeMillis());
        h.sync();
    }

    private static void startItem(MinecraftServer server, Holder h, QueueItem item, long now) {
        if (item.url().startsWith("local:") && !ServerFiles.exists(server, item.url().substring(6))) {
            advance(server, h, now); // arquivo não chegou ao servidor: pula
            return;
        }
        h.set(h.get().withSong(item.url(), item.title().isBlank() ? item.url() : item.title(), item.durationSec(),
                now + leadFor(item.url())));
    }

    /** Próxima música da fila (ou para, se a fila acabou). */
    static void advance(MinecraftServer server, Holder h, long now) {
        SongData s = h.get();
        List<QueueItem> q = new ArrayList<>(s.queue());
        while (!q.isEmpty()) {
            QueueItem next = q.remove(0);
            if (next.url().startsWith("local:") && !ServerFiles.exists(server, next.url().substring(6))) continue;
            h.set(s.withQueue(q).withSong(next.url(), next.title().isBlank() ? next.url() : next.title(),
                    next.durationSec(), now + leadFor(next.url())));
            return;
        }
        h.set(s.withQueue(List.of()).stopped());
    }

    /** Encontra a boombox que o jogador quer controlar, conferindo distância/mão. */
    static Holder resolve(ServerPlayer player, Target target) {
        Holder h = resolveAny(player, target);
        if (h != null && !h.settings().canControl(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("§cModo DJ: só " + h.settings().lockedBy() + " pode mexer nesta caixa"));
            return null;
        }
        return h;
    }

    /** Como resolve, mas sem conferir o Modo DJ. */
    static Holder resolveAny(ServerPlayer player, Target target) {
        if (target.isHeld()) {
            ItemStack stack = player.getItemInHand(target.hand());
            if (!BoomboxItem.isBoombox(stack)) return null;
            return stackHolder(player, stack);
        }
        BlockPos pos = target.pos();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (player.position().distanceToSqr(center) > MAX_USE_DISTANCE_SQ) return null;
        if (!(player.level().getBlockEntity(pos) instanceof BoomboxBlockEntity be)) return null;
        return blockHolder(player.level(), pos, be);
    }

    /** Encontra uma boombox ativa pela chave (para pedidos de quem só está ouvindo). */
    static Holder byKey(MinecraftServer server, String key) {
        Entry e = ACTIVE.get(key);
        if (e == null) return null;
        if (e.holder() == null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dim())));
            if (level == null || !(level.getBlockEntity(e.pos()) instanceof BoomboxBlockEntity be)) return null;
            return blockHolder(level, e.pos(), be);
        }
        ServerPlayer p = server.getPlayerList().getPlayer(e.holder());
        if (p == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = p.getItemInHand(hand);
            SongData s = BoomboxItem.isBoombox(stack) ? stack.get(ModBlocks.SONG) : null;
            if (s != null && s.url().equals(e.song().url()) && s.startMs() == e.song().startMs()) {
                return stackHolder(p, stack);
            }
        }
        return null;
    }

    private static Holder blockHolder(ServerLevel level, BlockPos pos, BoomboxBlockEntity be) {
        Group g = GROUPS.get(Payloads.blockKey(dim(level), pos));
        if (g != null) {
            // caixa sincronizada: qualquer mudança vale para todas do grupo
            return new Holder() {
                @Override public SongData get() { return be.song(); }
                @Override public void set(SongData song) {
                    for (BlockPos m : g.members()) {
                        BoomboxBlockEntity o = loadedSpeaker(level, m);
                        if (o != null) o.setSong(song);
                    }
                }
                @Override public void sync() { syncGroup(level, g); }
                @Override public SpeakerSettings settings() { return be.settings(); }
                @Override public void setSettings(SpeakerSettings s) { be.setSettings(s); syncBlock(level, pos, be.song()); }
            };
        }
        return new Holder() {
            @Override public SongData get() { return be.song(); }
            @Override public void set(SongData song) { be.setSong(song); }
            @Override public void sync() { syncBlock(level, pos, be.song()); }
            @Override public SpeakerSettings settings() { return be.settings(); }
            @Override public void setSettings(SpeakerSettings s) { be.setSettings(s); syncBlock(level, pos, be.song()); }
        };
    }

    private static Holder stackHolder(ServerPlayer player, ItemStack stack) {
        return new Holder() {
            @Override public SongData get() { return stack.getOrDefault(ModBlocks.SONG, SongData.EMPTY); }
            @Override public void set(SongData song) { stack.set(ModBlocks.SONG, song); }
            @Override public void sync() { syncPlayer(player); }
            @Override public SpeakerSettings settings() { return stack.getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT); }
            @Override public void setSettings(SpeakerSettings s) { stack.set(ModBlocks.SETTINGS, s); syncPlayer(player); }
        };
    }

    // ------------------------------------------------------------ sincronização

    /** Chamado pelo tick da boombox no chão. */
    public static void syncBlock(ServerLevel level, BlockPos pos, SongData song) {
        String key = Payloads.blockKey(dim(level), pos);
        Group g = GROUPS.get(key);
        int group = g == null ? 0 : g.members().size();
        SpeakerSettings settings = level.getBlockEntity(pos) instanceof BoomboxBlockEntity be ? be.settings() : SpeakerSettings.DEFAULT;
        boolean defaults = group == 0 && !song.hasSong() && song.queue().isEmpty() && song.autoplay() && song.volume() == 100
                && !settings.leo() && !settings.djLock();
        if (defaults) {
            remove(level.getServer(), key);
            return;
        }
        int type = level.getBlockState(pos).getBlock() instanceof BoomboxBlock b ? b.type().ordinal() : 0;
        update(level.getServer(), key, new Entry(dim(level), pos.immutable(), null, song, type, group, settings));
    }

    /** Tick da boombox no chão: passa para a próxima quando a música acaba. */
    public static void tickBlock(ServerLevel level, BlockPos pos, BoomboxBlockEntity be) {
        long now = System.currentTimeMillis();
        if (be.song().isOver(now)) {
            Holder h = blockHolder(level, pos, be);
            advance(level.getServer(), h, now);
            h.sync();
        }
        syncBlock(level, pos, be.song());
    }

    public static void removeBlock(ServerLevel level, BlockPos pos) {
        unlink(level, pos);
        remove(level.getServer(), Payloads.blockKey(dim(level), pos));
    }

    // ------------------------------------------------------------ caixas sincronizadas

    private static BoomboxBlockEntity loadedSpeaker(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof BoomboxBlockEntity be && !be.isRemoved() ? be : null;
    }

    private static void syncGroup(ServerLevel level, Group g) {
        for (BlockPos m : g.members()) {
            BoomboxBlockEntity o = loadedSpeaker(level, m);
            if (o != null) syncBlock(level, m, o.song());
        }
    }

    /**
     * Junta esta caixa com todas as outras caixas carregadas numa área de 8x8 chunks em volta dela.
     * Todas passam a tocar o que esta caixa está tocando. Devolve quantas outras caixas entraram.
     */
    static int linkNearby(ServerLevel level, BlockPos center, UUID who) {
        BoomboxBlockEntity self = loadedSpeaker(level, center);
        if (self == null) return 0;
        Set<BlockPos> found = new LinkedHashSet<>();
        found.add(center.immutable());
        int cx = center.getX() >> 4, cz = center.getZ() >> 4, r = SYNC_RADIUS_BLOCKS >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockPos p = e.getKey();
                    if (e.getValue() instanceof BoomboxBlockEntity obe && !obe.isRemoved() && !obe.settings().leo() && obe.settings().canControl(who)
                            && Math.abs(p.getX() - center.getX()) <= SYNC_RADIUS_BLOCKS
                            && Math.abs(p.getZ() - center.getZ()) <= SYNC_RADIUS_BLOCKS) {
                        found.add(p.immutable());
                    }
                }
            }
        }
        if (found.size() < 2) return 0;
        for (BlockPos p : found) unlink(level, p); // sai de grupos antigos
        Group g = new Group(dim(level), ConcurrentHashMap.newKeySet());
        g.members().addAll(found);
        for (BlockPos p : found) GROUPS.put(Payloads.blockKey(dim(level), p), g);
        SongData song = self.song();
        for (BlockPos p : found) {
            BoomboxBlockEntity o = loadedSpeaker(level, p);
            if (o != null) o.setSong(song);
        }
        syncGroup(level, g);
        return found.size() - 1;
    }

    /** Tira a caixa do grupo (ela continua tocando, mas sozinha). */
    static void unlink(ServerLevel level, BlockPos pos) {
        Group g = GROUPS.remove(Payloads.blockKey(dim(level), pos));
        if (g == null) return;
        g.members().remove(pos);
        if (g.members().size() < 2) {
            for (BlockPos m : g.members()) GROUPS.remove(Payloads.blockKey(dim(level), m));
        }
        syncGroup(level, g); // as que sobraram recebem a nova contagem
        BoomboxBlockEntity self = loadedSpeaker(level, pos);
        if (self != null) syncBlock(level, pos, self.song());
    }

    /** Confere se o jogador está segurando uma boombox tocando. */
    public static void syncPlayer(ServerPlayer player) {
        String key = Payloads.playerKey(player.getUUID());
        MinecraftServer server = player.level().getServer();
        long now = System.currentTimeMillis();
        SongData song = null;
        SpeakerSettings settings = SpeakerSettings.DEFAULT;
        int type = 0;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!BoomboxItem.isBoombox(stack)) continue;
            SongData s = stack.get(ModBlocks.SONG);
            if (s == null || !s.hasSong()) continue;
            if (s.isOver(now)) {
                advance(server, stackHolder(player, stack), now);
                s = stack.get(ModBlocks.SONG);
                if (s == null || !s.hasSong()) continue;
            }
            song = s;
            settings = stack.getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT);
            SpeakerType t = BoomboxItem.type(stack);
            type = t == null ? 0 : t.ordinal();
            break;
        }
        if (song == null || player.isSpectator()) {
            remove(server, key);
        } else {
            update(server, key, new Entry(dim(player.level()), BlockPos.ZERO, player.getUUID(), song, type, 0, settings));
        }
    }

    /** Allays perto de uma caixa tocando começam a dançar (igual com a jukebox). */
    public static void tickDancers(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Entry e : ACTIVE.values()) {
            if (!e.song().hasSong() || e.song().paused() || e.song().positionMs(now) < 0) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dim())));
            if (level == null) continue;
            BlockPos at = e.pos();
            if (e.holder() != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.holder());
                if (p == null) continue;
                at = p.blockPosition();
            } else if (!level.isLoaded(at)) {
                continue;
            }
            for (var allay : level.getEntitiesOfClass(net.minecraft.world.entity.animal.allay.Allay.class,
                    new net.minecraft.world.phys.AABB(at).inflate(8))) {
                allay.setJukeboxPlaying(at, true);
            }
        }
    }

    public static void tickPlayers(MinecraftServer server) {
        for (ServerPlayer p : PlayerLookup.all(server)) syncPlayer(p);
    }

    public static void onDisconnect(ServerPlayer player) {
        remove(player.level().getServer(), Payloads.playerKey(player.getUUID()));
    }

    private static void update(MinecraftServer server, String key, Entry e) {
        Entry old = ACTIVE.put(key, e);
        if (!e.equals(old)) broadcast(server, key, e);
    }

    private static void remove(MinecraftServer server, String key) {
        Entry old = ACTIVE.remove(key);
        if (old != null) broadcast(server, key, new Entry(old.dim(), old.pos(), old.holder(), SongData.EMPTY, old.type(), 0, SpeakerSettings.DEFAULT));
    }

    private static Payloads.BoomboxState toPayload(String key, Entry e, long now) {
        return new Payloads.BoomboxState(key, e.dim(), e.pos(), e.holder(), e.song(), now, e.type(), e.group(), e.settings());
    }

    private static void broadcast(MinecraftServer server, String key, Entry e) {
        if (server == null) return;
        Payloads.BoomboxState payload = toPayload(key, e, System.currentTimeMillis());
        for (ServerPlayer p : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(p, Payloads.BoomboxState.TYPE)) {
                ServerPlayNetworking.send(p, payload);
            }
        }
    }

    public static void sendAllTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Payloads.BoomboxState.TYPE)) return;
        long now = System.currentTimeMillis();
        ACTIVE.forEach((k, e) -> ServerPlayNetworking.send(player, toPayload(k, e, now)));
    }

    public static void clear() {
        ACTIVE.clear();
        GROUPS.clear();
    }
}
