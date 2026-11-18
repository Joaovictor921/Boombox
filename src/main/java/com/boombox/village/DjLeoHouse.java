package com.boombox.village;

import com.boombox.BoomboxMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Coloca a casa de rave do DJ Leo em todas as vilas (planície, deserto, savana, neve e taiga).
 * <p>
 * Enquanto uma vila é montada, a casa dele é a primeira opção de cada "lote de casa" da vila
 * até ser colocada uma vez; depois disso some da lista. Assim cada vila ganha exatamente uma
 * casa do DJ Leo (a não ser que não caiba em nenhum lote, o que é raro, porque ela é pequena).
 */
public final class DjLeoHouse {
    public static final String TEMPLATE = BoomboxMod.MOD_ID + ":djleo_house";
    private static final String[] VILLAGES = {"plains", "desert", "savanna", "snowy", "taiga"};

    private static volatile Set<StructureTemplatePool> housePools = Set.of();
    private static volatile StructurePoolElement element;
    /** Por thread de geração: a casa já foi colocada na vila que está sendo montada? */
    private static final ThreadLocal<boolean[]> PLACED = ThreadLocal.withInitial(() -> new boolean[]{true});

    private DjLeoHouse() {}

    public static void onServerStarting(MinecraftServer server) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        Set<StructureTemplatePool> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String v : VILLAGES) {
            pools.getOptional(Identifier.withDefaultNamespace("village/" + v + "/houses")).ifPresent(set::add);
        }
        housePools = set;
        element = StructurePoolElement.single(TEMPLATE).apply(StructureTemplatePool.Projection.RIGID);
        BoomboxMod.LOGGER.info("Boombox: casa do DJ Leo pronta para {} tipos de vila", set.size());
    }

    /** Começou a montar uma estrutura de peças (vila, etc.). */
    public static void startPlacement() {
        PLACED.get()[0] = false;
    }

    /** Uma peça foi colocada. */
    public static void piecePlaced(StructurePoolElement e) {
        if (e != null && e == element) PLACED.get()[0] = true;
    }

    /** Lista de casas sorteada para um lote: põe a do DJ Leo na frente se a vila ainda não tem. */
    public static List<StructurePoolElement> adjust(StructureTemplatePool pool, List<StructurePoolElement> shuffled) {
        StructurePoolElement e = element;
        if (e == null || PLACED.get()[0] || !housePools.contains(pool)) return shuffled;
        List<StructurePoolElement> out = new ArrayList<>(shuffled.size() + 1);
        out.add(e);
        out.addAll(shuffled);
        return out;
    }
}
