package com.boombox.net;

import com.boombox.BoomboxMod;
import com.boombox.QueueItem;
import com.boombox.SongData;
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
    public record BoomboxState(String key, String dim, BlockPos pos, UUID holder, SongData song, long serverNowMs)
            implements CustomPacketPayload {
        public static final Type<BoomboxState> TYPE = new Type<>(BoomboxMod.id("state"));
        private static final UUID NONE = new UUID(0, 0);
        public static final StreamCodec<ByteBuf, BoomboxState> CODEC = StreamCodec.composite(
                SHORT, BoomboxState::key,
                SHORT, BoomboxState::dim,
                BlockPos.STREAM_CODEC, BoomboxState::pos,
                UUIDUtil.STREAM_CODEC.map(u -> u.equals(NONE) ? null : u, u -> u == null ? NONE : u), BoomboxState::holder,
                SongData.STREAM_CODEC, BoomboxState::song,
                ByteBufCodecs.VAR_LONG, BoomboxState::serverNowMs,
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

    public static String playerKey(UUID id) {
        return "p|" + id;
    }
}
