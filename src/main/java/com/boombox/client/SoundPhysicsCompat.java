package com.boombox.client;

import com.boombox.BoomboxMod;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * Integração opcional com o mod Sound Physics Remastered: aplica eco/reverberação (cavernas,
 * salas fechadas) e abafamento atrás de paredes no som da boombox.
 * <p>
 * O áudio da boombox usa uma fonte OpenAL própria, que o Sound Physics não enxerga sozinho.
 * Por isso chamamos {@code SoundPhysics.processSound(...)} por reflexão, sem depender do mod
 * para compilar: se ele não estiver instalado, nada acontece.
 */
public final class SoundPhysicsCompat {
    /** Nome do som que o Sound Physics vê (dá para configurar no sound_rates.properties dele). */
    private static final Identifier SOUND_ID = BoomboxMod.id("music");

    private static boolean checked;
    private static Method process;

    private SoundPhysicsCompat() {}

    public static boolean available() {
        if (!checked) {
            checked = true;
            try {
                Class<?> c = Class.forName("com.sonicether.soundphysics.SoundPhysics");
                process = c.getMethod("processSound", int.class, double.class, double.class, double.class,
                        SoundSource.class, Identifier.class);
                BoomboxMod.LOGGER.info("Boombox: Sound Physics Remastered encontrado, eco e abafamento ativados");
            } catch (ClassNotFoundException e) {
                process = null;
            } catch (Throwable t) {
                process = null;
                BoomboxMod.LOGGER.warn("Boombox: versão do Sound Physics Remastered não reconhecida, sem eco", t);
            }
        }
        return process != null;
    }

    /**
     * Calcula o ambiente (eco, abafamento) para a fonte OpenAL na posição dada.
     * Devolve a posição "redirecionada" que o Sound Physics sugere (som contornando paredes), ou null.
     * <p>
     * A categoria enviada é BLOCKS: o Sound Physics ignora a categoria de discos (RECORDS) por padrão,
     * e queremos o eco sempre. O volume da boombox continua seguindo o controle "Jukebox/Discos".
     */
    public static Vec3 process(int source, double x, double y, double z) {
        if (!available() || source == 0) return null;
        try {
            Object r = process.invoke(null, source, x, y, z, SoundSource.BLOCKS, SOUND_ID);
            return r instanceof Vec3 v ? v : null;
        } catch (Throwable t) {
            BoomboxMod.LOGGER.warn("Boombox: erro ao aplicar o Sound Physics; desativando a integração", t);
            process = null;
            return null;
        }
    }
}
