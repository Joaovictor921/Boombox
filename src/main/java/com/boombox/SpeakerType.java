package com.boombox;

import java.util.Locale;

/**
 * Os modelos de caixa de som do mod. Todas tocam música do mesmo jeito; mudam o tamanho,
 * o visual e até onde o som chega.
 * Medidas em pixels (16 = 1 bloco): largura, profundidade e altura total (com alça).
 */
public enum SpeakerType {
    BOOMBOX(48, 15, 7.5f, 13.5f),
    PARTY_SMALL(40, 9, 7, 13.5f),
    PARTY_MEDIUM(56, 12, 10, 21.5f),
    PARTY_LARGE(72, 15, 12, 24);

    /** Distância (blocos) até onde o som é ouvido. */
    public final double hearDistance;
    public final float width, depth, height;

    SpeakerType(double hearDistance, float width, float depth, float height) {
        this.hearDistance = hearDistance;
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

    public static SpeakerType byId(String id) {
        for (SpeakerType t : values()) if (t.id().equals(id)) return t;
        return BOOMBOX;
    }
}
