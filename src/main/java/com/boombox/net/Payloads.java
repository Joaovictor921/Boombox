package com.boombox.net;

import com.boombox.BoomboxMod;
import com.boombox.QueueItem;
import com.boombox.SongData;
import com.boombox.SpeakerSettings;
import com.boombox.Target;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

public final class Payloads {
    /** Tamanho máximo de um pedaço de upload (cliente → servidor tem limite de ~32 KB por pacote). */
    public static final int UPLOAD_CHUNK = 28_000;
    /** Tamanho de um pedaço de download (servidor → cliente). */
    public static final int DOWNLOAD_CHUNK = 256 * 1024;
    /** Tamanho máximo de arquivo de música enviado. */
    public static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024;

    private static final StreamCodec<ByteBuf, String> URL = ByteBufCodecs.stringUtf8(4096);
    private static final StreamCodec<ByteBuf, String> SHORT = ByteBufCodecs.stringUtf8(512);

    private Payloads() {}

    // ---------------- Cliente → Servidor ----------------

    /** Pede para a boombox tocar uma URL (YouTube, link direto ou "local:&lt;sha1&gt;"). */
    public record PlayRequest(Target target, String url, String title, long durationSec) implements CustomPacketPayload {
        public static final Type<PlayRequest> TYPE = new Type<>(BoomboxMod.id("play_request"));
        public static final StreamCodec<ByteBuf, PlayRequest> CODEC = StreamCodec.composite(
                Target.STREAM_CODEC, PlayRequest::target,
                URL, PlayRequest::url,
                SHORT, PlayRequest::title,
                ByteBufCodecs.VAR_LONG, PlayRequest::durationSec,
                PlayRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Parar, pausar, continuar ou mudar o volume. */
    public record Control(Target target, int action, int value) implements CustomPacketPayload {
        public static final int STOP = 0, PAUSE = 1, RESUME = 2, VOLUME = 3, SKIP = 4, AUTOPLAY = 5,
                QUEUE_REMOVE = 6, QUEUE_CLEAR = 7, QUEUE_PLAY_INDEX = 8;
        /** Sincroniza todas as caixas num raio de 8x8 chunks com esta / desfaz a sincronização. */
        public static final int SYNC = 9, UNSYNC = 10;
        /** Modo de luz (value = modo), cor fixa (value = índice da cor), Modo DJ (value 1 = travar). */
        public static final int LIGHT_MODE = 11, LIGHT_COLOR = 12, DJ_LOCK = 13;
        public static final Type<Control> TYPE = new Type<>(BoomboxMod.id("control"));
        public static final StreamCodec<ByteBuf, Control> CODEC = StreamCodec.composite(
                Target.STREAM_CODEC, Control::target,
                ByteBufCodecs.VAR_INT, Control::action,
                ByteBufCodecs.VAR_INT, Control::value,
                Control::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Adiciona músicas na fila (APPEND) ou toca a lista agora: a 1ª toca e o resto vira a fila (PLAY_NOW). */
    public record QueueEdit(Target target, int mode, List<QueueItem> items) implements CustomPacketPayload {
        public static final int APPEND = 0, PLAY_NOW = 1;
        public static final Type<QueueEdit> TYPE = new Type<>(BoomboxMod.id("queue_edit"));
        public static final StreamCodec<ByteBuf, QueueEdit> CODEC = StreamCodec.composite(
                Target.STREAM_CODEC, QueueEdit::target,
                ByteBufCodecs.VAR_INT, QueueEdit::mode,
                QueueItem.LIST_STREAM_CODEC, QueueEdit::items,
                QueueEdit::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Um cliente terminou de tocar a música até o fim (para o servidor passar para a próxima). */
    public record SongEnded(String key, String url, long startMs) implements CustomPacketPayload {
        public static final Type<SongEnded> TYPE = new Type<>(BoomboxMod.id("song_ended"));
        public static final StreamCodec<ByteBuf, SongEnded> CODEC = StreamCodec.composite(
                SHORT, SongEnded::key,
                URL, SongEnded::url,
                ByteBufCodecs.VAR_LONG, SongEnded::startMs,
                SongEnded::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sugestões para a fila automática (músicas parecidas com forUrl). */
    public record AutoSuggest(String key, String forUrl, List<QueueItem> items) implements CustomPacketPayload {
        public static final Type<AutoSuggest> TYPE = new Type<>(BoomboxMod.id("auto_suggest"));
        public static final StreamCodec<ByteBuf, AutoSuggest> CODEC = StreamCodec.composite(
                SHORT, AutoSuggest::key,
                URL, AutoSuggest::forUrl,
                QueueItem.LIST_STREAM_CODEC, AutoSuggest::items,
                AutoSuggest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pedido de música pro DJ Leo (o jogador paga 1 barra de ouro). */
    public record DjRequest(int djId, String url, String title, long durationSec) implements CustomPacketPayload {
        public static final Type<DjRequest> TYPE = new Type<>(BoomboxMod.id("dj_request"));
        public static final StreamCodec<ByteBuf, DjRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, DjRequest::djId,
                URL, DjRequest::url,
                SHORT, DjRequest::title,
                ByteBufCodecs.VAR_LONG, DjRequest::durationSec,
                DjRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Tacada na mesa de sinuca (ângulo nas coordenadas da mesa, força de 0 a 1). */
    public record SinucaShot(BlockPos table, float angle, float power) implements CustomPacketPayload {
        public static final Type<SinucaShot> TYPE = new Type<>(BoomboxMod.id("sinuca_shot"));
        public static final StreamCodec<ByteBuf, SinucaShot> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SinucaShot::table,
                ByteBufCodecs.FLOAT, SinucaShot::angle,
                ByteBufCodecs.FLOAT, SinucaShot::power,
                SinucaShot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pedido no cardápio do Seu Zé. mode: 0 = paga, 1 = pendura, 2 = paga o fiado. */
    public record ZeOrder(int zeId, int item, int mode) implements CustomPacketPayload {
        public static final Type<ZeOrder> TYPE = new Type<>(BoomboxMod.id("ze_order"));
        public static final StreamCodec<ByteBuf, ZeOrder> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ZeOrder::zeId,
                ByteBufCodecs.VAR_INT, ZeOrder::item,
                ByteBufCodecs.VAR_INT, ZeOrder::mode,
                ZeOrder::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Fim de uma partida de truco contra um aldeão do bar. */
    public record TrucoResult(int opponentId, boolean won) implements CustomPacketPayload {
        public static final Type<TrucoResult> TYPE = new Type<>(BoomboxMod.id("truco_result"));
        public static final StreamCodec<ByteBuf, TrucoResult> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TrucoResult::opponentId,
                ByteBufCodecs.BOOL, TrucoResult::won,
                TrucoResult::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Garante que o servidor tenha um arquivo local (para a fila/playlist), sem tocar agora. */
    public record PrepareFile(String sha1, String title) implements CustomPacketPayload {
        public static final Type<PrepareFile> TYPE = new Type<>(BoomboxMod.id("prepare_file"));
        public static final StreamCodec<ByteBuf, PrepareFile> CODEC = StreamCodec.composite(
                SHORT, PrepareFile::sha1,
                SHORT, PrepareFile::title,
                PrepareFile::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Um pedaço de um arquivo de música enviado pelo jogador. */
    public record UploadChunk(String sha1, long offset, long total, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunk> TYPE = new Type<>(BoomboxMod.id("upload_chunk"));
        public static final StreamCodec<ByteBuf, UploadChunk> CODEC = StreamCodec.composite(
                SHORT, UploadChunk::sha1,
                ByteBufCodecs.VAR_LONG, UploadChunk::offset,
                ByteBufCodecs.VAR_LONG, UploadChunk::total,
                ByteBufCodecs.byteArray(UPLOAD_CHUNK), UploadChunk::data,
                UploadChunk::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente pede ao servidor um arquivo enviado por outro jogador. */
    public record FileRequest(String sha1) implements CustomPacketPayload {
        public static final Type<FileRequest> TYPE = new Type<>(BoomboxMod.id("file_request"));
        public static final StreamCodec<ByteBuf, FileRequest> CODEC = SHORT.map(FileRequest::new, FileRequest::sha1);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------- Servidor → Cliente ----------------

    /**
     * Estado de uma boombox: no chão (pos) ou na mão de um jogador (holder).
     * song sem música = parada/removida.
     */
    /**
     * Estado de uma boombox/caixa. type = SpeakerType (ordinal); group = quantas caixas estão
     * sincronizadas com esta (0 = nenhuma).
     */
    public record BoomboxState(String key, String dim, BlockPos pos, UUID holder, SongData song, long serverNowMs,
                               int speakerType, int group, SpeakerSettings settings)
            implements CustomPacketPayload {
        public static final Type<BoomboxState> TYPE = new Type<>(BoomboxMod.id("state"));
        private static final UUID NONE = new UUID(0, 0);
        private static final StreamCodec<ByteBuf, UUID> HOLDER_CODEC =
                UUIDUtil.STREAM_CODEC.<UUID>map(u -> u.equals(NONE) ? null : u, u -> u == null ? NONE : u);
        public static final StreamCodec<ByteBuf, BoomboxState> CODEC = StreamCodec.composite(
                SHORT, BoomboxState::key,
                SHORT, BoomboxState::dim,
                BlockPos.STREAM_CODEC, BoomboxState::pos,
                HOLDER_CODEC, BoomboxState::holder,
                SongData.STREAM_CODEC, BoomboxState::song,
                ByteBufCodecs.VAR_LONG, BoomboxState::serverNowMs,
                ByteBufCodecs.VAR_INT, BoomboxState::speakerType,
                ByteBufCodecs.VAR_INT, BoomboxState::group,
                SpeakerSettings.STREAM_CODEC, BoomboxState::settings,
                BoomboxState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O servidor não tem esse arquivo: o cliente que pediu deve enviá-lo. */
    public record UploadNeeded(String sha1) implements CustomPacketPayload {
        public static final Type<UploadNeeded> TYPE = new Type<>(BoomboxMod.id("upload_needed"));
        public static final StreamCodec<ByteBuf, UploadNeeded> CODEC = SHORT.map(UploadNeeded::new, UploadNeeded::sha1);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O servidor já tem esse arquivo. */
    public record FileReady(String sha1) implements CustomPacketPayload {
        public static final Type<FileReady> TYPE = new Type<>(BoomboxMod.id("file_ready"));
        public static final StreamCodec<ByteBuf, FileReady> CODEC = SHORT.map(FileReady::new, FileReady::sha1);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pedaço de arquivo do servidor. total &lt; 0 = arquivo não existe. */
    public record FileChunk(String sha1, long offset, long total, byte[] data) implements CustomPacketPayload {
        public static final Type<FileChunk> TYPE = new Type<>(BoomboxMod.id("file_chunk"));
        public static final StreamCodec<ByteBuf, FileChunk> CODEC = StreamCodec.composite(
                SHORT, FileChunk::sha1,
                ByteBufCodecs.VAR_LONG, FileChunk::offset,
                ByteBufCodecs.VAR_LONG, FileChunk::total,
                ByteBufCodecs.byteArray(DOWNLOAD_CHUNK), FileChunk::data,
                FileChunk::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(PlayRequest.TYPE, PlayRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Control.TYPE, Control.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(QueueEdit.TYPE, QueueEdit.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SongEnded.TYPE, SongEnded.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AutoSuggest.TYPE, AutoSuggest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PrepareFile.TYPE, PrepareFile.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadChunk.TYPE, UploadChunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(FileRequest.TYPE, FileRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DjRequest.TYPE, DjRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TrucoResult.TYPE, TrucoResult.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ZeOrder.TYPE, ZeOrder.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SinucaShot.TYPE, SinucaShot.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(BoomboxState.TYPE, BoomboxState.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UploadNeeded.TYPE, UploadNeeded.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FileReady.TYPE, FileReady.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FileChunk.TYPE, FileChunk.CODEC);
    }

    public static boolean isSha1(String s) {
        return s != null && s.matches("[0-9a-f]{40}");
    }

    public static String blockKey(String dim, BlockPos pos) {
        return "b|" + dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String entityKey(UUID id) {
        return "e|" + id;
    }

    public static String playerKey(UUID id) {
        return "p|" + id;
    }
}
