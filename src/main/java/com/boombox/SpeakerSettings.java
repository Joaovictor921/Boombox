package com.boombox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Configurações da caixa que não são a música: luzes, cor do gabinete, dono e Modo DJ.
 * Ficam no bloco e no item (componente "boombox:settings").
 *
 * @param light     modo das luzes (LIGHT_RAINBOW, LIGHT_FIXED, LIGHT_BEAT, LIGHT_OFF)
 * @param color     índice da cor fixa em {@link #COLORS}
 * @param dye       cor do gabinete (id do corante), -1 = preto original
 * @param djLock    Modo DJ: só o dono mexe no menu
 * @param owner     quem colocou a caixa (vazio = ninguém / DJ Leo)
 * @param ownerName nome do dono, para mostrar no menu
 * @param blown     caixa "estourada" (da casa do DJ Leo): bem mais alta e distorcida
 * @param leo       caixa do DJ Leo: toca sozinha quando alguém chega perto
 */
public record SpeakerSettings(int light, int color, int dye, boolean djLock, Optional<UUID> owner, String ownerName,
                              boolean blown, boolean leo) {

    public static final int LIGHT_RAINBOW = 0, LIGHT_FIXED = 1, LIGHT_BEAT = 2, LIGHT_OFF = 3;
    public static final String[] LIGHT_NAMES = {"Arco-íris", "Cor fixa", "Na batida", "Desligada"};

    /** Cores para o modo "cor fixa". */
    public static final int[] COLORS = {
            0xFF2020, 0xFF7A00, 0xFFE000, 0x7CFF00, 0x00E060, 0x00F0F0,
            0x2E9BFF, 0x2040FF, 0x8A2BFF, 0xFF20D0, 0xFF6FA8, 0xFFFFFF};
    public static final String[] COLOR_NAMES = {
            "Vermelho", "Laranja", "Amarelo", "Lima", "Verde", "Ciano",
            "Azul-claro", "Azul", "Roxo", "Magenta", "Rosa", "Branco"};

    public static final String LEO_NAME = "DJ Leo";

    public static final SpeakerSettings DEFAULT = new SpeakerSettings(LIGHT_RAINBOW, 0, -1, false, Optional.empty(), "", false, false);

    public static final Codec<SpeakerSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("light", 0).forGetter(SpeakerSettings::light),
            Codec.INT.optionalFieldOf("color", 0).forGetter(SpeakerSettings::color),
            Codec.INT.optionalFieldOf("dye", -1).forGetter(SpeakerSettings::dye),
            Codec.BOOL.optionalFieldOf("dj_lock", false).forGetter(SpeakerSettings::djLock),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(SpeakerSettings::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(SpeakerSettings::ownerName),
            Codec.BOOL.optionalFieldOf("blown", false).forGetter(SpeakerSettings::blown),
            Codec.BOOL.optionalFieldOf("leo", false).forGetter(SpeakerSettings::leo)
    ).apply(i, SpeakerSettings::new));

    public static final StreamCodec<ByteBuf, SpeakerSettings> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                ByteBufCodecs.VAR_INT.encode(buf, s.light);
                ByteBufCodecs.VAR_INT.encode(buf, s.color);
                ByteBufCodecs.VAR_INT.encode(buf, s.dye + 1);
                ByteBufCodecs.BOOL.encode(buf, s.djLock);
                ByteBufCodecs.BOOL.encode(buf, s.owner.isPresent());
                s.owner.ifPresent(u -> UUIDUtil.STREAM_CODEC.encode(buf, u));
                ByteBufCodecs.stringUtf8(64).encode(buf, s.ownerName);
                ByteBufCodecs.BOOL.encode(buf, s.blown);
                ByteBufCodecs.BOOL.encode(buf, s.leo);
            },
            buf -> {
                int light = ByteBufCodecs.VAR_INT.decode(buf);
                int color = ByteBufCodecs.VAR_INT.decode(buf);
                int dye = ByteBufCodecs.VAR_INT.decode(buf) - 1;
                boolean lock = ByteBufCodecs.BOOL.decode(buf);
                Optional<UUID> owner = ByteBufCodecs.BOOL.decode(buf) ? Optional.of(UUIDUtil.STREAM_CODEC.decode(buf)) : Optional.empty();
                String name = ByteBufCodecs.stringUtf8(64).decode(buf);
                return new SpeakerSettings(light, color, dye, lock, owner, name,
                        ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.BOOL.decode(buf));
            });

    public SpeakerSettings {
        light = Math.floorMod(light, 4);
        color = Math.floorMod(color, COLORS.length);
        dye = Math.max(-1, Math.min(15, dye));
        ownerName = ownerName.length() > 64 ? ownerName.substring(0, 64) : ownerName;
    }

    public boolean isDefault() {
        return equals(DEFAULT);
    }

    public int colorRgb() {
        return COLORS[color];
    }

    /** O jogador pode mexer no menu desta caixa? */
    public boolean canControl(UUID player) {
        if (!djLock) return true;
        return owner.isPresent() && owner.get().equals(player);
    }

    /** Nome de quem travou (para o aviso no menu). */
    public String lockedBy() {
        return ownerName.isEmpty() ? "o dono" : ownerName;
    }

    public SpeakerSettings withLight(int v) {
        return new SpeakerSettings(v, color, dye, djLock, owner, ownerName, blown, leo);
    }

    public SpeakerSettings withColor(int v) {
        return new SpeakerSettings(light, v, dye, djLock, owner, ownerName, blown, leo);
    }

    public SpeakerSettings withDye(int v) {
        return new SpeakerSettings(light, color, v, djLock, owner, ownerName, blown, leo);
    }

    public SpeakerSettings withLock(boolean v) {
        return new SpeakerSettings(light, color, dye, v, owner, ownerName, blown, leo);
    }

    /** Novo dono (ao colocar a caixa): deixa de ser a caixa do DJ Leo, mas continua estourada. */
    public SpeakerSettings withOwner(UUID id, String name) {
        return new SpeakerSettings(light, color, dye, djLock, Optional.ofNullable(id), name, blown, false);
    }

    /** Caixa da casa do DJ Leo: travada por ele, estourada e tocando sozinha. */
    public static SpeakerSettings leoSpeaker() {
        return new SpeakerSettings(LIGHT_RAINBOW, 0, -1, true, Optional.empty(), LEO_NAME, true, true);
    }
}
