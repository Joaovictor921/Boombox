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
    /** priv = com fone de ouvido: só quem segura a caixa ouve. */
    public record Entry(String dim, BlockPos pos, UUID holder, SongData song, int type, int group, SpeakerSettings settings, boolean priv) {}

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
    /** Último prêmio de truco de cada jogador. */
    private static final Map<UUID, Long> TRUCO_PRIZE = new ConcurrentHashMap<>();

    /** A balada do DJ Leo só funciona de noite (no fim de tarde já começa). */
    public static boolean raveOpen(ServerLevel level) {
        return level.isDarkOutside() || com.boombox.bar.ShowTime.isShowNow();
    }

    private static boolean showWas;
    private static int lastSoonAnnounce = -1;

    /** Show do DJ Leo no fim de semana (relógio do computador): aviso, troca de playlist e fogos. */
    private static int lastNewYearSecond = -1;

    /** Réveillon: contagem regressiva no chat e fogos nas vilas na virada do ano (relógio do computador). */
    public static void tickNewYear(MinecraftServer server) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int sec = now.getSecond();
        if (now.getMonthValue() == 12 && now.getDayOfMonth() == 31 && now.getHour() == 23 && now.getMinute() == 59 && sec >= 50) {
            if (sec != lastNewYearSecond) {
                lastNewYearSecond = sec;
                server.getPlayerList().broadcastSystemMessage(Component.literal("§6§l" + (60 - sec) + "..."), false);
            }
        } else if (now.getMonthValue() == 1 && now.getDayOfMonth() == 1 && now.getHour() == 0 && now.getMinute() == 0) {
            if (sec == 0 && lastNewYearSecond != 100) {
                lastNewYearSecond = 100;
                server.getPlayerList().broadcastSystemMessage(Component.literal("§e§l✦ FELIZ ANO NOVO! ✦ §r§6Feliz " + now.getYear() + "!"), false);
            }
            if (server.getTickCount() % 8 == 0) newYearFireworks(server);
        }
    }

    /** Fogos de ano novo em volta das vilas perto de cada jogador. */
    public static void newYearFireworks(MinecraftServer server) {
        ServerLevel level = server.overworld();
        var r = level.getRandom();
        for (ServerPlayer p : level.players()) {
            var villagers = level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, p.getBoundingBox().inflate(96));
            net.minecraft.world.phys.Vec3 c = villagers.isEmpty() ? p.position() : villagers.get(r.nextInt(villagers.size())).position();
            double x = c.x + r.nextInt(31) - 15, z = c.z + r.nextInt(31) - 15;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) x, (int) z);
            net.minecraft.world.item.ItemStack rocket = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET);
            int[] palette = {0xFF3060, 0xFFD020, 0x30E070, 0x30A0FF, 0xC040FF, 0xFFFFFF, 0xFF8020};
            var shapes = net.minecraft.world.item.component.FireworkExplosion.Shape.values();
            rocket.set(net.minecraft.core.component.DataComponents.FIREWORKS, new net.minecraft.world.item.component.Fireworks(1 + r.nextInt(2),
                    java.util.List.of(new net.minecraft.world.item.component.FireworkExplosion(shapes[r.nextInt(shapes.length)],
                            it.unimi.dsi.fastutil.ints.IntList.of(palette[r.nextInt(palette.length)], palette[r.nextInt(palette.length)]),
                            it.unimi.dsi.fastutil.ints.IntList.of(0xFFFFFF), r.nextBoolean(), r.nextBoolean()))));
            level.addFreshEntity(new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, x, y + 1, z, rocket));
        }
    }

    public static void tickShow(MinecraftServer server) {
        boolean show = com.boombox.bar.ShowTime.isShowNow();
        int day = java.time.LocalDate.now().getDayOfYear();
        if (com.boombox.bar.ShowTime.isShowSoon() && lastSoonAnnounce != day) {
            lastSoonAnnounce = day;
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§d§l♪ Hoje tem show do DJ Leo às " + com.boombox.bar.ShowTime.SHOW_HOUR + "h! §r§dCola na balada mais perto."), false);
        }
        if (show && !showWas) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§d§l♪ COMEÇOU O SHOW DO DJ LEO! §r§dFogos, fumaça e playlist especial até as " + (com.boombox.bar.ShowTime.SHOW_HOUR + 1) + "h."), false);
            // as caixas do DJ Leo trocam para a playlist do show
            for (Map.Entry<String, Entry> en : ACTIVE.entrySet()) {
                Entry e = en.getValue();
                if (!e.settings().leo() || e.holder() != null) continue;
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dim())));
                if (level == null || !(level.getBlockEntity(e.pos()) instanceof BoomboxBlockEntity be)) continue;
                Holder h = blockHolder(level, e.pos(), be);
                h.set(be.song().withQueue(List.of()).stopped());
                h.sync();
            }
        }
        showWas = show;
        if (show && server.getTickCount() % 160 == 0) {
            ServerLevel level = server.overworld();
            for (var dj : level.getEntities(com.boombox.entity.ModEntities.DJ_LEO, d -> true)) {
                if (level.getNearestPlayer(dj, 64) == null) continue;
                var r = level.getRandom();
                for (int i = 0; i < 3; i++) {
                    double x = dj.getX() + r.nextInt(17) - 8, y = dj.getY() + 12 + r.nextInt(6), z = dj.getZ() + r.nextInt(17) - 8;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK, x, y, z, 60, 0.2, 0.2, 0.2, 0.25);
                    level.playSound(null, x, y, z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                            net.minecraft.sounds.SoundSource.AMBIENT, 3f, 0.9f + r.nextFloat() * 0.2f);
                }
            }
        }
    }
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
                if (!payload.target().isBlock()) return;
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

        // pedido de música pro DJ Leo, pago com uma barra de ouro
        ServerPlayNetworking.registerGlobalReceiver(Payloads.DjRequest.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ServerLevel level = player.level();
            if (!(level.getEntity(payload.djId()) instanceof com.boombox.entity.DjLeo dj) || dj.distanceTo(player) > 10) return;
            String url = payload.url().trim();
            if (!validUrl(url) || url.startsWith("local:")) {
                player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Esse link aí eu não consigo tocar não."));
                return;
            }
            InteractionHand hand = player.getMainHandItem().is(net.minecraft.world.item.Items.GOLD_INGOT) ? InteractionHand.MAIN_HAND
                    : player.getOffhandItem().is(net.minecraft.world.item.Items.GOLD_INGOT) ? InteractionHand.OFF_HAND : null;
            if (hand == null) {
                player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Cadê o ouro? Sem barra de ouro não tem pedido!"));
                return;
            }
            String title = payload.title().isBlank() ? url : payload.title().trim();
            if (!raveOpen(level)) {
                player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Zzz... a balada só abre de noite, parceiro..."));
                return;
            }
            if (!djLeoPlay(level, dj.position(), new QueueItem(url, title, payload.durationSec()))) {
                player.sendSystemMessage(Component.literal("§d<DJ Leo>§r Minhas caixas sumiram! Não tenho onde tocar."));
                return;
            }
            if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
            dj.cheer();
            com.boombox.Achievements.award(player, "pediu_musica");
            player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "§d<DJ Leo>§r Essa vai pra você, " + player.getName().getString() + "! §e♪ " + title), false);
        });

        // fim de uma partida de truco: quem ganha leva uma esmeralda (no máximo uma por minuto)
        ServerPlayNetworking.registerGlobalReceiver(Payloads.TrucoResult.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!(player.level().getEntity(payload.opponentId()) instanceof com.boombox.entity.Raver r) || r.distanceTo(player) > 8) return;
            r.celebrate(!payload.won());
            if (!payload.won()) {
                player.sendSystemMessage(Component.literal("§a<Aldeão>§r Truco é comigo mesmo! Volta quando aprender, hein."));
                return;
            }
            long now = System.currentTimeMillis();
            Long last = TRUCO_PRIZE.get(player.getUUID());
            com.boombox.Achievements.award(player, "ganhou_no_truco");
            if (last != null && now - last < 60_000) {
                player.sendSystemMessage(Component.literal("§a<Aldeão>§r Ganhou de novo?! Agora é só de brincadeira, sem aposta."));
                return;
            }
            TRUCO_PRIZE.put(player.getUUID(), now);
            net.minecraft.world.item.ItemStack prize = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD);
            if (!player.getInventory().add(prize)) r.spawnAtLocation(player.level(), prize);
            player.sendSystemMessage(Component.literal("§a<Aldeão>§r Ganhou, ganhou... Toma a esmeralda. Vai lá no Seu Zé tomar uma!"));
        });

        // tacada de sinuca
        ServerPlayNetworking.registerGlobalReceiver(Payloads.SinucaShot.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (player.blockPosition().distSqr(payload.table()) > 8 * 8) return;
            if (player.level().getBlockEntity(payload.table()) instanceof com.boombox.bar.SinucaBlockEntity be) {
                be.playerShot(player, payload.angle(), payload.power());
            }
        });

        // pedido no cardápio do Seu Zé
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ZeOrder.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (player.level().getEntity(payload.zeId()) instanceof com.boombox.entity.SeuZe ze) {
                ze.order(player, payload.item(), payload.mode());
            }
        });

        // sugestões da fila automática (vêm de algum cliente que está ouvindo)
        ServerPlayNetworking.registerGlobalReceiver(Payloads.AutoSuggest.TYPE, (payload, ctx) -> {
            Holder h = byKey(ctx.player().level().getServer(), payload.key());
            if (h == null) return;
            SongData s = h.get();
            if (payload.forUrl().isEmpty()) {
                // a caixa do DJ Leo está parada: um cliente pesquisou músicas de rave para ela
                if (!h.settings().autoPlays() || s.hasSong()) return;
                if (h.settings().leo() && !raveOpen(ctx.player().level())) return; // de dia a balada está fechada
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
                    .filter(i -> !i.url().equals(s.url()))
                    .filter(i -> h.settings().ze() || !s.history().contains(i.url())) // o bar repete as músicas da playlist
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
                net.minecraft.world.entity.Entity holder = level.getEntity(e.holder());
                if (holder == null) continue;
                p = holder.position();
            } else {
                p = Vec3.atCenterOf(e.pos());
            }
            if (p.distanceToSqr(at) <= r2) return true;
        }
        return false;
    }

    /** Toca uma música nas caixas do DJ Leo perto desse ponto (todas as caixas do grupo dele). */
    public static boolean djLeoPlay(ServerLevel level, Vec3 near, QueueItem item) {
        BlockPos center = BlockPos.containing(near);
        BlockPos best = null;
        BoomboxBlockEntity bestBe = null;
        double bestD = Double.MAX_VALUE;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4, r = 2;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (e.getValue() instanceof BoomboxBlockEntity be && !be.isRemoved() && be.settings().leo()) {
                        double d = e.getKey().distSqr(center);
                        if (d < bestD && d <= 32 * 32) {
                            bestD = d;
                            best = e.getKey();
                            bestBe = be;
                        }
                    }
                }
            }
        }
        if (bestBe == null) return false;
        linkLeoIfNeeded(level, best, System.currentTimeMillis());
        Holder h = blockHolder(level, best, bestBe);
        startItem(level.getServer(), h, item, System.currentTimeMillis());
        h.sync();
        return true;
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
        if (target.isEntity()) {
            if (!(player.level().getEntity(target.entity()) instanceof com.boombox.entity.MountedSpeaker ms)
                    || ms.distanceToSqr(player) > MAX_USE_DISTANCE_SQ) return null;
            return entityHolder(player.level(), ms);
        }
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
        if (key.startsWith("e|")) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(e.dim())));
            if (level != null && level.getEntity(e.holder()) instanceof com.boombox.entity.MountedSpeaker ms) return entityHolder(level, ms);
            return null;
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

    private static Holder entityHolder(ServerLevel level, com.boombox.entity.MountedSpeaker ms) {
        return new Holder() {
            @Override public SongData get() { return ms.song(); }
            @Override public void set(SongData song) { ms.setSong(song); }
            @Override public void sync() { syncEntity(level, ms); }
            @Override public SpeakerSettings settings() { return ms.settings(); }
            @Override public void setSettings(SpeakerSettings s) { ms.setSettings(s); syncEntity(level, ms); }
        };
    }

    /** Paredão no barco/carrinho: passa para a próxima música quando acaba e avisa os jogadores. */
    public static void tickEntity(ServerLevel level, com.boombox.entity.MountedSpeaker ms) {
        long now = System.currentTimeMillis();
        if (ms.song().isOver(now)) {
            Holder h = entityHolder(level, ms);
            advance(level.getServer(), h, now);
        }
        syncEntity(level, ms);
    }

    static void syncEntity(ServerLevel level, com.boombox.entity.MountedSpeaker ms) {
        update(level.getServer(), Payloads.entityKey(ms.getUUID()),
                new Entry(dim(level), BlockPos.ZERO, ms.getUUID(), ms.song(), ms.speakerType().ordinal(), 0, ms.settings(), false));
    }

    public static void removeEntity(ServerLevel level, com.boombox.entity.MountedSpeaker ms) {
        remove(level.getServer(), Payloads.entityKey(ms.getUUID()));
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
        update(level.getServer(), key, new Entry(dim(level), pos.immutable(), null, song, type, group, settings, false));
    }

    /** Tick da boombox no chão: passa para a próxima quando a música acaba. */
    public static void tickBlock(ServerLevel level, BlockPos pos, BoomboxBlockEntity be) {
        long now = System.currentTimeMillis();
        if (be.settings().leo()) {
            linkLeoIfNeeded(level, pos, now);
            // amanheceu: a balada fecha (para a música e limpa a fila)
            if (!raveOpen(level) && (be.song().hasSong() || !be.song().queue().isEmpty())) {
                Holder h = blockHolder(level, pos, be);
                h.set(be.song().withQueue(List.of()).stopped());
                h.sync();
            }
        }
        if (be.song().isOver(now)) {
            Holder h = blockHolder(level, pos, be);
            advance(level.getServer(), h, now);
            h.sync();
        }
        syncBlock(level, pos, be.song());
    }

    /** Última tentativa de juntar as caixas do DJ Leo (por caixa), para não procurar a cada tick. */
    private static final Map<String, Long> LEO_LINK_TRY = new ConcurrentHashMap<>();
    /** Raio (blocos) em que as caixas da casa do DJ Leo se juntam. */
    private static final int LEO_LINK_RADIUS = 24;

    /**
     * Casa do DJ Leo: todas as caixas dele por perto viram um grupo sincronizado (uma só fonte de som,
     * todas mexendo juntas). Os grupos ficam só na memória, então isso é refeito depois de reiniciar.
     */
    private static void linkLeoIfNeeded(ServerLevel level, BlockPos pos, long now) {
        String key = Payloads.blockKey(dim(level), pos);
        if (GROUPS.containsKey(key)) return;
        Long last = LEO_LINK_TRY.get(key);
        if (last != null && now - last < 30_000) return;
        LEO_LINK_TRY.put(key, now);
        Set<BlockPos> found = new LinkedHashSet<>();
        BoomboxBlockEntity playing = null;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, r = (LEO_LINK_RADIUS >> 4) + 1;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockPos p = e.getKey();
                    if (e.getValue() instanceof BoomboxBlockEntity obe && !obe.isRemoved() && obe.settings().leo()
                            && p.distManhattan(pos) <= LEO_LINK_RADIUS * 2 && Math.abs(p.getY() - pos.getY()) <= 12) {
                        found.add(p.immutable());
                        if (playing == null && obe.song().hasSong()) playing = obe;
                    }
                }
            }
        }
        if (found.size() < 2) return;
        for (BlockPos p : found) unlink(level, p);
        Group g = new Group(dim(level), ConcurrentHashMap.newKeySet());
        g.members().addAll(found);
        for (BlockPos p : found) GROUPS.put(Payloads.blockKey(dim(level), p), g);
        if (playing != null) {
            SongData song = playing.song();
            for (BlockPos p : found) {
                BoomboxBlockEntity o = loadedSpeaker(level, p);
                if (o != null) o.setSong(song);
            }
        }
        syncGroup(level, g);
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
            update(server, key, new Entry(dim(player.level()), BlockPos.ZERO, player.getUUID(), song, type, 0, settings,
                    player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(com.boombox.bar.BarBlocks.HEADPHONES)));
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
                net.minecraft.world.entity.Entity holder = level.getEntity(e.holder());
                if (holder == null) continue;
                at = holder.blockPosition();
            } else if (!level.isLoaded(at)) {
                continue;
            }
            // conquista: colado numa caixa grande ou estourada tocando (a menos de 2 blocos)
            if (e.settings().blown() || e.type() == SpeakerType.PARTY_LARGE.ordinal()) {
                net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atCenterOf(at);
                for (ServerPlayer pl : level.players()) {
                    if (pl.position().distanceToSqr(c) < 2.2 * 2.2 && !pl.getUUID().equals(e.holder())) {
                        com.boombox.Achievements.award(pl, "colado_no_paredao");
                    }
                }
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
        if (old != null) broadcast(server, key, new Entry(old.dim(), old.pos(), old.holder(), SongData.EMPTY, old.type(), 0, SpeakerSettings.DEFAULT, false));
    }

    private static Payloads.BoomboxState toPayload(String key, Entry e, long now) {
        return new Payloads.BoomboxState(key, e.dim(), e.pos(), e.holder(), e.song(), now, e.type(), e.group(), e.settings());
    }

    private static void broadcast(MinecraftServer server, String key, Entry e) {
        if (server == null) return;
        long now = System.currentTimeMillis();
        Payloads.BoomboxState payload = toPayload(key, e, now);
        // com fone: os outros jogadores recebem "parada" (não ouvem)
        Payloads.BoomboxState silent = e.priv() ? toPayload(key, new Entry(e.dim(), e.pos(), e.holder(), SongData.EMPTY, e.type(), 0,
                SpeakerSettings.DEFAULT, true), now) : payload;
        for (ServerPlayer p : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(p, Payloads.BoomboxState.TYPE)) {
                ServerPlayNetworking.send(p, e.priv() && !p.getUUID().equals(e.holder()) ? silent : payload);
            }
        }
    }

    public static void sendAllTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Payloads.BoomboxState.TYPE)) return;
        long now = System.currentTimeMillis();
        ACTIVE.forEach((k, e) -> {
            if (!e.priv() || player.getUUID().equals(e.holder())) ServerPlayNetworking.send(player, toPayload(k, e, now));
        });
    }

    public static void clear() {
        ACTIVE.clear();
        GROUPS.clear();
        LEO_LINK_TRY.clear();
    }
}
