package com.boombox;

import java.util.Locale;

/**
 * Os modelos de caixa de som do mod. Todas tocam música do mesmo jeito; mudam o tamanho,
 * o visual e até onde o som chega.
 * Medidas em pixels (16 = 1 bloco): largura, profundidade e altura total (com alça).
 */
public enum SpeakerType {
    // alcance (blocos), volume (x boombox), largura, profundidade, altura
    BOOMBOX(48, 1.0f, 15, 7.5f, 13.5f),
    PARTY_SMALL(64, 1.4f, 9, 7, 13.5f),
    PARTY_MEDIUM(96, 1.9f, 12, 10, 21.5f),
    PARTY_LARGE(128, 2.5f, 15, 12, 24);

    /** Distância (blocos) até onde o som é ouvido: quanto maior a caixa, mais longe. */
    public final double hearDistance;
    /** Volume em relação à boombox: caixas maiores tocam mais alto. */
    public final float loudness;
    public final float width, depth, height;

    SpeakerType(double hearDistance, float loudness, float width, float depth, float height) {
        this.hearDistance = hearDistance;
        this.loudness = loudness;
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    /** Nome no registro (boombox, party_speaker_small, ...). */
    public String id() {
        return switch (this) {
            case BOOMBOX -> "boombox";
            default -> "party_speaker_" + name().substring(6).toLowerCase(Locale.ROOT);
        };
    }

    public boolean isParty() {
        return this != BOOMBOX;
    }

    public static SpeakerType byOrdinal(int i) {
        SpeakerType[] v = values();
        return i >= 0 && i < v.length ? v[i] : BOOMBOX;
    }

    public static SpeakerType byId(String id) {
        for (SpeakerType t : values()) if (t.id().equals(id)) return t;
        return BOOMBOX;
    }
}
