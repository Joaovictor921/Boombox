package com.boombox.client;

import com.boombox.SpeakerType;

import com.boombox.SongData;
import com.boombox.SpeakerSettings;
import com.boombox.net.Payloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import com.boombox.QueueItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lista de boomboxes tocando (recebida do servidor), no chão ou na mão de jogadores.
 * Inicia/para o áudio conforme o jogador se aproxima ou se afasta, sempre no ponto certo
 * da música usando o relógio do servidor.
 */
public final class ClientBoomboxManager {
    public static final class State {
        public final String key;
        public final String dim;
        public final BlockPos pos;
        /** Tipo da caixa (define alcance e volume). */
        public final SpeakerType type;
        /** Quantas caixas estão sincronizadas com esta (0 = nenhuma). */
        public final int group;
        public final UUID holder;
        public final SongData song;
        /** Luz, Modo DJ, estourada... */
        public final SpeakerSettings settings;
        boolean finished;
        String error;
        boolean reportedEnd;
        boolean suggested;
        String prefetched;

        State(Payloads.BoomboxState p) {
            this.key = p.key();
            this.dim = p.dim();
            this.pos = p.pos();
            this.holder = p.holder();
            this.song = p.song();
            this.type = SpeakerType.byOrdinal(p.speakerType());
            this.group = p.group();
            this.settings = p.settings();
        }

        public String error() { return error; }
        public boolean finished() { return finished; }

        boolean sameStream(SongData other) {
            return song.url().equals(other.url()) && song.startMs() == other.startMs() && song.paused() == other.paused();
        }
    }

    /** Distância (blocos) em que o som começa a tocar / some completamente. */
    public static final double HEAR_DISTANCE = 48;
    private static final double FULL_VOLUME_DISTANCE = 6;

    private static final Map<String, State> STATES = new HashMap<>();
    private static final Map<String, AudioStream> STREAMS = new HashMap<>();
    /** Boomboxes paradas com configurações (só para o menu). */
    private static final Map<String, SongData> IDLE = new HashMap<>();
    /** id da entidade do jogador → chave da boombox que ele segura */
    private static final Map<Integer, String> HELD_BY_ENTITY = new HashMap<>();
    /**
     * Caixa → "unidade" de som. Caixas sincronizadas tocando a mesma música formam uma só unidade:
     * uma única fonte de som (na caixa mais perto de você), para não ouvir a voz duas vezes.
     */
    private static final Map<String, String> UNIT_OF = new HashMap<>();
    /** Caixas do DJ Leo paradas: alguém perto pesquisa músicas de rave para elas. */
    private static final Map<String, Payloads.BoomboxState> LEO_IDLE = new HashMap<>();
    /** Quando cada caixa do DJ Leo pediu músicas pela última vez (para não pedir sem parar). */
    private static final Map<String, Long> LEO_ASKED = new HashMap<>();
    /** O que o DJ Leo gosta de tocar (sorteia uma pesquisa). */
    private static final String[] LEO_SEARCHES = {
            "eletrofunk rave", "funk rave mix", "automotivo rave funk", "brega funk 2025",
            "rave brasileira mix", "funk mandelão", "rave funk remix", "aquecimento rave funk"};
    /** Quanto de parede tem entre cada caixa e você (medido a cada 4 ticks). */
    private static final Map<String, Float> OCCLUSION = new HashMap<>();
    /** Abafamento suavizado de cada unidade de som (para não dar "pulos" de volume). */
    private static final Map<String, Float> UNIT_OCCLUSION = new HashMap<>();
    private static int occlusionTick;
    /** Última vez que algum cliente pesquisou músicas para o DJ Leo (uma pesquisa por vez). */
    private static long lastLeoSearch;
    /** Caixa que soa mais alto para você agora (para a câmera tremer no grave) e o quanto ela soa. */
    private static String loudestKey;
    private static double loudestGain;
    /** relógio do servidor - relógio local */
    private static long clockOffset;
    private static boolean clockKnown;

    private ClientBoomboxManager() {}

    public static long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    public static void onState(Payloads.BoomboxState p) {
        // A diferença medida = diferença real - atraso da rede. O menor atraso dá a medida mais
        // precisa (a maior diferença), então guardamos o máximo; reinicia se mudar muito (outro servidor).
        long measured = p.serverNowMs() - System.currentTimeMillis();
        if (!clockKnown || measured > clockOffset || clockOffset - measured > 2000) {
            clockOffset = measured;
            clockKnown = true;
        }
        String k = p.key();
        State old = STATES.get(k);
        if (p.settings().leo() && !p.song().hasSong() && p.holder() == null) LEO_IDLE.put(k, p);
        else LEO_IDLE.remove(k);
        if (!p.song().hasSong()) {
            STATES.remove(k);
            closeStream(k);
            // boombox parada, mas com fila/volume/fila automática configurados: guarda para o menu
            if (p.song().equals(SongData.EMPTY.withVolume(p.song().volume())) && p.song().volume() == 100) IDLE.remove(k);
            else IDLE.put(k, p.song());
            return;
        }
        IDLE.remove(k);
        State st = new State(p);
        if (old != null && old.song.url().equals(p.song().url()) && old.song.startMs() == p.song().startMs()) {
            st.suggested = old.suggested;
            st.reportedEnd = old.reportedEnd;
            st.prefetched = old.prefetched;
        }
        if (old != null && old.sameStream(p.song())) {
            st.finished = old.finished;
            st.error = old.error;
        } else {
            closeStream(k);
        }
        STATES.put(k, st);
    }

    // ------------------------------------------------------------ consultas (menu e renderização)

    public static State getBlock(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return null;
        return STATES.get(Payloads.blockKey(mc.level.dimension().identifier().toString(), pos));
    }

    /** Dados da boombox no chão para o menu (tocando ou parada). */
    public static SongData blockSong(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return null;
        String k = Payloads.blockKey(mc.level.dimension().identifier().toString(), pos);
        State st = STATES.get(k);
        return st != null ? st.song : IDLE.get(k);
    }

    public static State getHeld(UUID player) {
        return STATES.get(Payloads.playerKey(player));
    }

    public static boolean isLoading(State st) {
        if (st == null) return false;
        AudioStream s = STREAMS.get(UNIT_OF.getOrDefault(st.key, st.key));
        return s != null && s.isLoading();
    }

    private static void levels(String key, float[] out) {
        out[0] = out[1] = 0;
        State st = key == null ? null : STATES.get(key);
        AudioStream s = key == null ? null : STREAMS.get(UNIT_OF.getOrDefault(key, key));
        if (st == null || s == null) {
            return;
        }
        s.levels(out);
        float vol = 0.35f + 0.65f * st.song.volume() / 100f;
        out[0] *= vol;
        out[1] *= vol;
    }

    /** Grave/agudo da boombox no chão (0–1). */
    public static void levelsForBlock(String dim, BlockPos pos, float[] out) {
        levels(Payloads.blockKey(dim, pos), out);
    }

    /** Grave/agudo da boombox na mão de um jogador (pelo id da entidade). */
    public static void levelsForEntity(int entityId, float[] out) {
        levels(HELD_BY_ENTITY.get(entityId), out);
    }

    /** Grave/agudo para um item (procura a boombox tocando essa mesma música). */
    public static void levelsForSong(SongData song, float[] out) {
        out[0] = out[1] = 0;
        if (song == null || !song.hasSong() || song.paused()) return;
        for (State st : STATES.values()) {
            if (st.holder != null && st.song.url().equals(song.url()) && st.song.startMs() == song.startMs()) {
                levels(st.key, out);
                return;
            }
        }
    }

    // ------------------------------------------------------------ tick

    /** Caixas sincronizadas com a mesma música dividem uma única unidade de som. */
    private static String unitKey(State st) {
        if (st.group > 1 && st.holder == null) {
            return "g|" + st.dim + "|" + st.song.startMs() + "|" + st.song.url();
        }
        return st.key;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!STREAMS.isEmpty()) stopAll();
            return;
        }
        String dim = mc.level.dimension().identifier().toString();
        Vec3 ear = mc.gameRenderer.mainCamera().position();
        float master = mc.options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        HELD_BY_ENTITY.clear();
        UNIT_OF.clear();

        tickLeo(mc, dim);
        loudestKey = null;
        loudestGain = 0;
        boolean measure = occlusionTick++ % 4 == 0;

        Map<String, List<State>> units = new LinkedHashMap<>();
        for (State st : STATES.values()) {
            String uk = unitKey(st);
            UNIT_OF.put(st.key, uk);
            units.computeIfAbsent(uk, k -> new ArrayList<>()).add(st);
            if (st.holder != null) {
                Player holder = mc.level.getPlayerByUUID(st.holder);
                if (holder != null) HELD_BY_ENTITY.put(holder.getId(), st.key);
            }
        }

        for (Map.Entry<String, List<State>> unit : units.entrySet()) {
            String uk = unit.getKey();
            List<State> members = unit.getValue();
            AudioStream stream = STREAMS.get(uk);
            if (stream != null && stream.blown() != members.get(0).settings.blown()) {
                closeStream(uk); // a caixa foi estourada / trocada: reabre com o som certo
                stream = null;
            }

            // de qual caixa o som sai: a que soaria mais alto para você (normalmente a mais perto)
            State rep = null;
            Vec3 repSrc = null;
            double repDist = Double.MAX_VALUE, repGain = -1;
            // centro "de massa" das caixas que você ouve (peso = volume²) e o quanto elas se espalham
            double wSum = 0, cx = 0, cy = 0, cz = 0;
            List<double[]> heard = new ArrayList<>();
            for (State st : members) {
                Vec3 src = sourcePosition(mc, st);
                if (src == null || !st.dim.equals(dim)) continue;
                double dist = Math.sqrt(ear.distanceToSqr(src));
                double hear = hearDistance(mc, st);
                if (dist >= (stream != null ? hear + 16 : hear)) continue;
                if (measure || !OCCLUSION.containsKey(st.key)) OCCLUSION.put(st.key, occlusion(mc, src, ear));
                // paredes no caminho abafam: a caixa "mais audível" é a menos escondida
                double g = gainAt(st, dist) * occlusionGain(OCCLUSION.get(st.key));
                double w = g * g;
                if (w > 1e-6) {
                    wSum += w;
                    cx += src.x * w;
                    cy += src.y * w;
                    cz += src.z * w;
                    heard.add(new double[]{src.x, src.y, src.z, w});
                }
                if (rep == null || g > repGain) {
                    rep = st;
                    repSrc = src;
                    repDist = dist;
                    repGain = g;
                }
            }
            boolean finished = false;
            for (State st : members) finished |= st.finished;
            boolean shouldPlay = rep != null && !rep.song.paused() && !finished;

            if (shouldPlay && stream == null) {
                SongData song = rep.song;
                stream = new AudioStream(song.url(), uk, () -> song.positionMs(serverNow()), rep.settings.blown());
                stream.start();
                STREAMS.put(uk, stream);
            } else if (!shouldPlay && stream != null) {
                closeStream(uk);
                stream = null;
            }
            if (stream == null) continue;

            // abafamento suavizado: quem está do lado de fora ouve só o grave, baixinho
            float target = OCCLUSION.getOrDefault(rep.key, 0f);
            float occ = UNIT_OCCLUSION.getOrDefault(uk, target);
            occ += (target - occ) * 0.3f;
            UNIT_OCCLUSION.put(uk, occ);
            double open = gainAt(rep, repDist);
            float gain = (float) (master * open * occlusionGain(occ));
            if (master > 0.05f && !stream.isLoading()) {
                // tremor: só colado numa caixa (2 a 5 blocos, mais para caixas mais altas) e sem parede no meio
                for (State st : members) {
                    Vec3 src = sourcePosition(mc, st);
                    if (src == null || !st.dim.equals(dim) || OCCLUSION.getOrDefault(st.key, 9f) > 0.3f) continue;
                    float loud = st.type.loudness * (st.settings.blown() ? BLOWN_LOUDNESS : 1f);
                    double reach = 2.0 + 0.6 * Math.min(5f, loud);
                    double near = 1.0 - Math.sqrt(ear.distanceToSqr(src)) / reach;
                    if (near > loudestGain) {
                        loudestGain = near;
                        loudestKey = st.key;
                    }
                }
            }
            // Várias caixas juntas: o som sai do centro delas, "espalhado" pelo tamanho do grupo. Dentro da
            // rave você fica dentro desse raio e ouve dos dois lados; de fora, o som vem da direção da casa.
            // Antes saía só da caixa mais alta: se ela estivesse do seu lado, um lado do fone ficava mudo.
            Vec3 at = repSrc;
            float radius = 0.5f;
            if (heard.size() > 1 && wSum > 0) {
                at = new Vec3(cx / wSum, cy / wSum, cz / wSum);
                double var = 0;
                for (double[] h : heard) {
                    double dx = h[0] - at.x, dy = h[1] - at.y, dz = h[2] - at.z;
                    var += h[3] * (dx * dx + dy * dy + dz * dz);
                }
                radius = (float) Math.max(0.5, Math.sqrt(var / wSum) * 1.3);
            }
            stream.tick(at.x, at.y, at.z, gain, occlusionHighs(occ), radius);
            if (!stream.isLoading()) maybeAutoQueue(mc, rep, members);
            if (!rep.song.queue().isEmpty() && !rep.song.queue().get(0).url().equals(rep.prefetched)) {
                String next = rep.song.queue().get(0).url();
                for (State st : members) st.prefetched = next;
                YouTube.prefetch(next); // a próxima já fica pronta
            }
            if (stream.isFinished()) {
                String error = stream.error();
                boolean report = error == null && members.stream().noneMatch(st -> st.reportedEnd);
                for (State st : members) {
                    st.finished = true;
                    st.error = error;
                    st.reportedEnd = true;
                }
                if (report) {
                    ClientPlayNetworking.send(new Payloads.SongEnded(rep.key, rep.song.url(), rep.song.startMs()));
                }
                if (error != null && repDist < 16) {
                    mc.player.sendOverlayMessage(Component.literal("§cBoombox: " + shorten(error)));
                }
                closeStream(uk);
            }
        }

        // streams sem unidade (segurança)
        Iterator<Map.Entry<String, AudioStream>> it = STREAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AudioStream> e = it.next();
            if (!units.containsKey(e.getKey())) {
                e.getValue().close();
                it.remove();
            }
        }
    }

    /** Volume (sem o controle de som do jogo) de uma caixa a essa distância. */
    private static double gainAt(State st, double dist) {
        double hear = st.type.hearDistance;
        double full = FULL_VOLUME_DISTANCE * hear / HEAR_DISTANCE; // caixas maiores enchem mais o ambiente
        double att = dist <= full ? 1.0 : Math.max(0.0, 1.0 - (dist - full) / (hear - full));
        float loud = st.type.loudness; // caixas maiores tocam mais alto (também na mão)
        if (st.settings.blown()) loud *= BLOWN_LOUDNESS; // caixa estourada do DJ Leo: exagerada
        return (st.song.volume() / 100.0) * att * att * loud;
    }

    /** Até onde o som chega: quanto maior a caixa, mais longe (o servidor informa o tipo). */
    private static double hearDistance(Minecraft mc, State st) {
        return st.type.hearDistance * (st.settings.blown() ? BLOWN_RANGE : 1.0);
    }

    /**
     * Quanto a câmera deve tremer agora (0 a 1): só bem perto de uma caixa alta e quando o grave bate forte,
     * como uma câmera encostada numa caixa de som no talo. Respeita a opção "Efeitos de tela" do jogo.
     */
    public static float cameraShake(Minecraft mc) {
        if (loudestKey == null) return 0f;
        float[] lv = new float[2];
        levels(loudestKey, lv);
        float near = (float) Math.max(0, Math.min(1, loudestGain * 1.6));                // 0 longe, 1 colado
        float kick = Math.max(0f, Math.min(1f, (lv[0] - 0.5f) / 0.35f));               // só no grave forte
        return near * kick * mc.options.screenEffectScale().get().floatValue();
    }

    /** Caixa estourada: quanto mais alta e até onde vai. */
    private static final float BLOWN_LOUDNESS = 5.0f;

    /**
     * Quanto de parede tem entre a caixa e o ouvido: anda pela linha reta em passos de 1/4 de bloco
     * e soma 1 por bloco sólido (pedra, madeira...), 0,45 por bloco "vazado" (porta, vidro, grade, laje)
     * e 0,3 por água. As outras caixas de som e o próprio bloco da caixa não contam.
     */
    public static float occlusion(Minecraft mc, Vec3 from, Vec3 to) {
        double dist = from.distanceTo(to);
        if (dist < 1.0 || mc.level == null) return 0f;
        int steps = (int) Math.ceil(dist / 0.25);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long src = BlockPos.containing(from).asLong(), ear = BlockPos.containing(to).asLong(), last = Long.MIN_VALUE;
        float occ = 0f;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            m.set(Math.floor(from.x + (to.x - from.x) * t), Math.floor(from.y + (to.y - from.y) * t),
                    Math.floor(from.z + (to.z - from.z) * t));
            long k = m.asLong();
            if (k == last || k == src || k == ear) continue;
            last = k;
            var s = mc.level.getBlockState(m);
            if (s.isAir() || s.getBlock() instanceof com.boombox.BoomboxBlock) continue;
            if (s.isSolidRender()) occ += 1f;
            else if (!s.getCollisionShape(mc.level, m).isEmpty()) occ += 0.45f;
            else if (!s.getFluidState().isEmpty()) occ += 0.3f;
            if (occ >= 6f) break;
        }
        return occ;
    }

    /** Volume que passa pelas paredes (1 = nada no caminho). Com o Sound Physics ele também abafa, então aqui é mais leve. */
    public static double occlusionGain(float occ) {
        double k = SoundPhysicsCompat.available() ? 1.1 : 1.5; // uma parede: 33% / 22% do volume
        return Math.max(0.03, Math.exp(-k * occ));
    }

    /** Quanto dos agudos passa pelas paredes (o grave atravessa, o agudo some). */
    private static float occlusionHighs(float occ) {
        return (float) Math.max(0.02, Math.exp(-2.0 * occ));
    }
    private static final double BLOWN_RANGE = 1.4;

    /**
     * Caixas do DJ Leo paradas e perto de você: pesquisa músicas de rave e manda para o servidor
     * (ele aceita só se a caixa ainda estiver parada). Tenta de novo a cada 40 s.
     */
    private static void tickLeo(Minecraft mc, String dim) {
        if (LEO_IDLE.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 me = mc.player.position();
        for (Payloads.BoomboxState p : LEO_IDLE.values()) {
            if (!p.dim().equals(dim) || me.distanceToSqr(Vec3.atCenterOf(p.pos())) > 40 * 40) continue;
            Long last = LEO_ASKED.get(p.key());
            if (last != null && now - last < 40_000) continue;
            if (now - lastLeoSearch < 20_000) return; // a casa tem várias caixas: uma pesquisa serve para todas
            lastLeoSearch = now;
            LEO_ASKED.put(p.key(), now);
            String key = p.key();
            String query = LEO_SEARCHES[(int) (Math.random() * LEO_SEARCHES.length)];
            long delay = (long) (Math.random() * 1500);
            Tools.EXEC.submit(() -> {
                try {
                    Thread.sleep(delay);
                    Tools.ensureReady().get();
                    List<YouTube.Result> found = new ArrayList<>(YouTube.search(query, 12));
                    java.util.Collections.shuffle(found);
                    List<QueueItem> items = found.stream()
                            .filter(r -> r.durationSec() <= 0 || r.durationSec() < 15 * 60) // sem mixes de horas
                            .map(r -> new QueueItem(r.url(), r.title(), r.durationSec()))
                            .limit(8).toList();
                    if (items.isEmpty()) return;
                    mc.execute(() -> {
                        if (LEO_IDLE.containsKey(key)) ClientPlayNetworking.send(new Payloads.AutoSuggest(key, "", items));
                    });
                } catch (Exception e) {
                    com.boombox.BoomboxMod.LOGGER.info("Boombox: DJ Leo não achou músicas: {}", e.getMessage());
                }
            });
        }
    }

    /**
     * A caixa tocando mais perto desse ponto (até radius blocos), ou null. Usada pelos papagaios,
     * pelos LEDs de rave e pelo DJ Leo para dançar.
     */
    public static State playingNear(Minecraft mc, Vec3 at, double radius) {
        if (mc.level == null) return null;
        String dim = mc.level.dimension().identifier().toString();
        State best = null;
        double bestD = radius * radius;
        for (State st : STATES.values()) {
            if (!st.dim.equals(dim) || st.song.paused() || st.finished) continue;
            AudioStream s = STREAMS.get(UNIT_OF.getOrDefault(st.key, st.key));
            if (s == null || s.isLoading()) continue;
            Vec3 src = sourcePosition(mc, st);
            if (src == null) continue;
            double d = src.distanceToSqr(at);
            if (d <= bestD) {
                bestD = d;
                best = st;
            }
        }
        return best;
    }

    /** Grave/agudo de uma caixa (0–1). */
    public static void levelsOf(State st, float[] out) {
        levels(st == null ? null : st.key, out);
    }

    /**
     * Fila automática: quando a fila está vazia, busca músicas parecidas (Mix do YouTube) e sugere
     * ao servidor. Vários ouvintes podem tentar; o servidor aceita só a primeira sugestão.
     */
    private static void maybeAutoQueue(Minecraft mc, State st, List<State> members) {
        if (st.suggested || !st.song.autoplay() || !st.song.queue().isEmpty() || st.song.paused()) return;
        if (YouTube.videoId(st.song.url()) == null) return;
        for (State m : members) m.suggested = true;
        String key = st.key, url = st.song.url();
        long delay = (long) (Math.random() * 2500);
        Tools.EXEC.submit(() -> {
            try {
                Thread.sleep(delay);
                if (!stillNeedsSuggestions(mc, key, url)) return;
                List<YouTube.Result> related = YouTube.related(url, 12);
                List<QueueItem> items = related.stream()
                        .map(r -> new QueueItem(r.url(), r.title(), r.durationSec()))
                        .toList();
                if (items.isEmpty()) return;
                mc.execute(() -> {
                    State now = STATES.get(key);
                    if (now == null || !now.song.url().equals(url) || !now.song.queue().isEmpty()) return;
                    List<QueueItem> fresh = items.stream().filter(i -> !now.song.history().contains(i.url())).limit(8).toList();
                    if (!fresh.isEmpty()) ClientPlayNetworking.send(new Payloads.AutoSuggest(key, url, fresh));
                });
            } catch (Exception e) {
                com.boombox.BoomboxMod.LOGGER.info("Boombox: fila automática falhou: {}", e.getMessage());
            }
        });
    }

    private static boolean stillNeedsSuggestions(Minecraft mc, String key, String url) {
        java.util.concurrent.CompletableFuture<Boolean> f = new java.util.concurrent.CompletableFuture<>();
        mc.execute(() -> {
            State now = STATES.get(key);
            f.complete(now != null && now.song.url().equals(url) && now.song.queue().isEmpty() && now.song.autoplay());
        });
        try {
            return f.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /** Quantos ms faltam para a música começar (0 se já começou). */
    public static long startsInMs(State st) {
        if (st == null || st.song.paused()) return 0;
        return Math.max(0, -st.song.positionMs(serverNow()));
    }

    /** De onde o som sai: centro do bloco, ou ao lado da cabeça de quem segura a boombox. */
    private static Vec3 sourcePosition(Minecraft mc, State st) {
        if (st.holder == null) {
            return new Vec3(st.pos.getX() + 0.5, st.pos.getY() + 0.4, st.pos.getZ() + 0.5);
        }
        Player p = mc.level.getPlayerByUUID(st.holder);
        if (p == null) return null;
        // boombox: ao lado da cabeça; caixas de festa: na altura da mão / atrás do jogador
        return st.type == SpeakerType.BOOMBOX ? p.getEyePosition() : p.position().add(0, 0.7, 0);
    }

    private static String shorten(String s) {
        return s.length() > 90 ? s.substring(0, 90) + "..." : s;
    }

    private static void closeStream(String k) {
        AudioStream s = STREAMS.remove(k);
        if (s != null) s.close();
    }

    public static void stopAll() {
        STREAMS.values().forEach(AudioStream::close);
        STREAMS.clear();
    }

    public static void clear() {
        stopAll();
        STATES.clear();
        IDLE.clear();
        LEO_IDLE.clear();
        LEO_ASKED.clear();
        OCCLUSION.clear();
        UNIT_OCCLUSION.clear();
        lastLeoSearch = 0;
        HELD_BY_ENTITY.clear();
        UNIT_OF.clear();
        clockKnown = false;
    }
}
