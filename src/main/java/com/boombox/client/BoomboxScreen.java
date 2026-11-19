package com.boombox.client;

import com.boombox.ModBlocks;
import com.boombox.QueueItem;
import com.boombox.BoomboxBlockEntity;
import com.boombox.BoomboxItem;
import com.boombox.SongData;
import com.boombox.SpeakerSettings;
import com.boombox.SpeakerType;
import net.minecraft.client.gui.components.AbstractWidget;
import com.boombox.Target;
import com.boombox.net.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Menu da boombox: link, pesquisa no YouTube, fila (com fila automática), playlists,
 * suas músicas, tocar/pausar, pular, parar e volume.
 */
public class BoomboxScreen extends Screen {
    private enum Mode { NONE, SEARCH, LOCAL, QUEUE, PLAYLISTS, PLAYLIST }

    private record Side(String label, int width, Runnable action) {}

    private record Row(String label, Runnable action, List<Side> sides) {}

    private record Tool(String label, int width, Runnable action) {}

    private static final int W = 320;
    private static final int LIST_TOP = 146;

    /** Playlist escolhida por último (para o botão "+PL" dos resultados). */
    private static String lastPlaylist;

    private final Target target;
    private EditBox input;
    private Button syncButton;
    private Button pauseButton;
    private Button lightButton;
    private Button colorButton;
    private Button djButton;
    /** Estava travada para mim (Modo DJ de outra pessoa) quando a tela foi montada. */
    private boolean builtLocked;
    private String text = "";
    private Mode mode = Mode.NONE;
    private List<YouTube.Result> searchResults = List.of();
    private Playlists.Playlist openPlaylist;
    private boolean confirmDelete;
    private int page;
    private String status = "";
    private boolean busy;
    private int volume = -1;
    private int rowsPerPage = 4;

    public BoomboxScreen(Target target) {
        super(Component.literal("Boombox"));
        this.target = target;
        Playlists.load();
    }

    // ------------------------------------------------------------ estado

    /** Nome da caixa (Boombox, Caixa de Festa Grande...) para o título. */
    /** Chave do paredão (barco/carrinho) que o menu controla, ou null. */
    private String entityKey() {
        Minecraft mc = Minecraft.getInstance();
        if (!target.isEntity() || mc.level == null) return null;
        var e = mc.level.getEntity(target.entity());
        return e == null ? null : Payloads.entityKey(e.getUUID());
    }

    private String speakerName() {
        Minecraft mc = Minecraft.getInstance();
        if (target.isEntity()) {
            return mc.level != null && mc.level.getEntity(target.entity()) instanceof com.boombox.entity.MountedSpeaker ms
                    ? "Paredão (" + ModBlocks.BLOCKS.get(ms.speakerType()).getName().getString() + ")" : "Paredão";
        }
        if (target.isHeld()) {
            return mc.player == null ? "Boombox (na mão)"
                    : mc.player.getItemInHand(target.hand()).getHoverName().getString() + " (na mão)";
        }
        if (mc.level != null) return mc.level.getBlockState(target.pos()).getBlock().getName().getString();
        return "Boombox";
    }

    private ClientBoomboxManager.State state() {
        Minecraft mc = Minecraft.getInstance();
        if (target.isHeld()) return mc.player == null ? null : ClientBoomboxManager.getHeld(mc.player.getUUID());
        if (target.isEntity()) {
            String k = entityKey();
            return k == null ? null : ClientBoomboxManager.getByKey(k);
        }
        return ClientBoomboxManager.getBlock(mc, target.pos());
    }

    /** Luz, cor, dono e Modo DJ desta caixa. */
    private SpeakerSettings settings() {
        Minecraft mc = Minecraft.getInstance();
        if (target.isEntity()) {
            String k = entityKey();
            return k == null ? SpeakerSettings.DEFAULT : ClientBoomboxManager.settingsForKey(k);
        }
        if (target.isHeld()) {
            return mc.player == null ? SpeakerSettings.DEFAULT
                    : mc.player.getItemInHand(target.hand()).getOrDefault(ModBlocks.SETTINGS, SpeakerSettings.DEFAULT);
        }
        if (mc.level != null && mc.level.getBlockEntity(target.pos()) instanceof BoomboxBlockEntity be) return be.settings();
        return SpeakerSettings.DEFAULT;
    }

    private SpeakerType speakerType() {
        Minecraft mc = Minecraft.getInstance();
        if (target.isEntity()) {
            return mc.level != null && mc.level.getEntity(target.entity()) instanceof com.boombox.entity.MountedSpeaker ms
                    ? ms.speakerType() : SpeakerType.PARTY_LARGE;
        }
        if (target.isHeld()) return mc.player == null ? SpeakerType.BOOMBOX : BoomboxItem.type(mc.player.getItemInHand(target.hand()));
        if (mc.level != null && mc.level.getBlockState(target.pos()).getBlock() instanceof com.boombox.BoomboxBlock b) return b.type();
        return SpeakerType.BOOMBOX;
    }

    /** Modo DJ de outra pessoa: não posso mexer. */
    private boolean lockedForMe() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !target.isHeld() && !settings().canControl(mc.player.getUUID());
    }

    private SongData song() {
        ClientBoomboxManager.State st = state();
        if (st != null) return st.song;
        Minecraft mc = Minecraft.getInstance();
        if (target.isHeld() && mc.player != null) {
            SongData s = mc.player.getItemInHand(target.hand()).get(ModBlocks.SONG);
            if (s != null) return s;
        } else if (target.isEntity()) {
            String k = entityKey();
            SongData s = k == null ? null : ClientBoomboxManager.songForKey(k);
            if (s != null) return s;
        } else if (!target.isHeld()) {
            SongData s = ClientBoomboxManager.blockSong(mc, target.pos());
            if (s != null) return s;
        }
        return SongData.EMPTY;
    }

    // ------------------------------------------------------------ montagem da tela

    @Override
    protected void init() {
        if (volume < 0) volume = song().volume();
        int left = width / 2 - W / 2;
        rowsPerPage = Math.max(3, Math.min(8, (height - LIST_TOP - 26) / 21));

        input = new EditBox(font, left, 30, W, 20, Component.literal("Link ou pesquisa"));
        input.setMaxLength(2048);
        input.setHint(Component.literal("Link do YouTube, nome da música ou nome da playlist"));
        input.setValue(text);
        input.setResponder(s -> text = s);
        addRenderableWidget(input);
        setInitialFocus(input);

        // linha 1: tocar / pesquisar / pausar / pular / parar
        int bw = (W - 16) / 5;
        int y = 54;
        button("▶ Tocar", left, y, bw, this::playInput);
        button("Pesquisar", left + (bw + 4), y, bw, () -> search(text.trim()));
        pauseButton = button("❚❚ Pausar", left + 2 * (bw + 4), y, bw, this::togglePause);
        button("⏭ Pular", left + 3 * (bw + 4), y, bw, () -> {
            control(Payloads.Control.SKIP, 0);
            setStatus("Pulando para a próxima...");
        });
        button("■ Parar", left + 4 * (bw + 4), y, W - 4 * (bw + 4), () -> {
            control(Payloads.Control.STOP, 0);
            setStatus("Parado");
        });

        // linha 2: volume / sincronizar / fila / playlists / músicas
        y = 76;
        int vw = 92, sw = 78;
        addRenderableWidget(new VolumeSlider(left, y, vw, 20));
        syncButton = button("⇄ Sincronizar", left + vw + 4, y, sw, this::toggleSync);
        syncButton.setTooltip(Tooltip.create(Component.literal(
                "Faz todas as caixas de som numa área de 8x8 chunks em volta desta tocarem a mesma música, "
                        + "ao mesmo tempo. Clique de novo para separar esta caixa.")));
        if (!target.isBlock()) syncButton.active = false;
        int tx0 = left + vw + sw + 8;
        int tw = (W - vw - sw - 16) / 3;
        button(tab(Mode.QUEUE, "Fila"), tx0, y, tw, () -> setMode(Mode.QUEUE));
        button(tab(Mode.PLAYLISTS, "Playlists"), tx0 + tw + 4, y, tw, () -> setMode(Mode.PLAYLISTS));
        button(tab(Mode.LOCAL, "Músicas"), tx0 + 2 * (tw + 4), y, left + W - (tx0 + 2 * (tw + 4)), () -> setMode(Mode.LOCAL));

        // linha 3: luzes / cor / Modo DJ
        y = 98;
        SpeakerSettings st = settings();
        SpeakerType type = speakerType();
        boolean party = type != null && type.isParty();
        lightButton = button(lightLabel(st), left, y, 120, this::cycleLight);
        lightButton.setTooltip(Tooltip.create(Component.literal(party
                ? "Efeito dos LEDs: arco-íris, uma cor fixa, piscar só na batida ou desligado."
                : "Só as caixas de festa têm LEDs.")));
        lightButton.active = party;
        colorButton = button("", left + 124, y, 90, this::cycleColor);
        colorButton.setMessage(colorLabel(st));
        colorButton.setTooltip(Tooltip.create(Component.literal("Cor dos LEDs no modo \"Cor fixa\".")));
        colorButton.active = party && st.light() == SpeakerSettings.LIGHT_FIXED;
        djButton = button(djLabel(st), left + 218, y, W - 218, this::toggleDj);
        djButton.setTooltip(Tooltip.create(Component.literal(
                "Modo DJ: trava o menu desta caixa para só você trocar a música. "
                        + "Ninguém mais consegue pular, pausar ou mudar o volume.")));

        // barra de ferramentas da aba atual (alinhada à direita)
        List<Tool> tools = tools();
        int tx = left + W;
        for (int i = tools.size() - 1; i >= 0; i--) {
            Tool t = tools.get(i);
            tx -= t.width();
            button(t.label(), tx, 122, t.width(), t.action());
            tx -= 3;
        }

        // lista
        List<Row> rows = rows();
        int pages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < rowsPerPage; i++) {
            int idx = page * rowsPerPage + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            int ry = LIST_TOP + i * 21;
            int sidesW = 0;
            for (Side s : r.sides()) sidesW += s.width() + 2;
            button(fit(r.label(), W - sidesW - 10), left, ry, W - sidesW, r.action());
            int sx = left + W - sidesW + 2;
            for (Side s : r.sides()) {
                button(s.label(), sx, ry, s.width(), s.action());
                sx += s.width() + 2;
            }
        }
        if (pages > 1) {
            int py = LIST_TOP + rowsPerPage * 21;
            Button prev = button("<", left, py, 24, () -> { page--; refresh(); });
            Button next = button(">", left + W - 24, py, 24, () -> { page++; refresh(); });
            prev.active = page > 0;
            next.active = page < pages - 1;
        }

        // Modo DJ de outra pessoa: dá para ver o que toca, mas não mexer
        builtLocked = lockedForMe();
        if (builtLocked) {
            for (var child : children()) {
                if (child instanceof AbstractWidget w) w.active = false;
            }
        }
    }

    private String lightLabel(SpeakerSettings st) {
        return "✦ Luz: " + SpeakerSettings.LIGHT_NAMES[st.light()];
    }

    private Component colorLabel(SpeakerSettings st) {
        return Component.literal("■ ").withColor(st.colorRgb())
                .append(Component.literal(SpeakerSettings.COLOR_NAMES[st.color()]));
    }

    private String djLabel(SpeakerSettings st) {
        return st.djLock() ? "§b⚑ Modo DJ: ligado" : "⚑ Modo DJ: desligado";
    }

    private void cycleLight() {
        int next = (settings().light() + 1) % 4;
        control(Payloads.Control.LIGHT_MODE, next);
        setStatus("Luz: " + SpeakerSettings.LIGHT_NAMES[next]);
    }

    private void cycleColor() {
        int next = (settings().color() + 1) % SpeakerSettings.COLORS.length;
        control(Payloads.Control.LIGHT_COLOR, next);
        setStatus("Cor dos LEDs: " + SpeakerSettings.COLOR_NAMES[next]);
    }

    private void toggleDj() {
        control(Payloads.Control.DJ_LOCK, settings().djLock() ? 0 : 1);
    }

    private int syncedCount() {
        ClientBoomboxManager.State st = state();
        return st == null ? 0 : st.group;
    }

    private void toggleSync() {
        if (!target.isBlock()) return;
        if (syncedCount() > 1) {
            control(Payloads.Control.UNSYNC, 0);
            setStatus("Caixa separada das outras");
        } else {
            control(Payloads.Control.SYNC, 0);
            setStatus("Sincronizando as caixas numa área de 8x8 chunks...");
        }
    }

    private String tab(Mode m, String label) {
        boolean on = mode == m || (m == Mode.PLAYLISTS && mode == Mode.PLAYLIST);
        return on ? "§e" + label : label;
    }

    private Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> action.run()).bounds(x, y, w, 20).build());
    }

    private void setMode(Mode m) {
        if (m == Mode.PLAYLISTS) Playlists.load();
        mode = m;
        page = 0;
        confirmDelete = false;
        if (m == Mode.LOCAL && ClientFiles.listMusic().isEmpty()) {
            setStatus("Coloque .mp3/.ogg/.wav/.flac/.m4a na pasta boombox_musicas");
        } else if (m == Mode.PLAYLISTS && Playlists.all().isEmpty()) {
            setStatus("Digite um nome no campo de cima e clique em \"+ Nova\"");
        } else {
            setStatus("");
        }
        refresh();
    }

    private String listTitle() {
        return switch (mode) {
            case NONE -> "";
            case SEARCH -> "Resultados do YouTube";
            case LOCAL -> "Minhas músicas";
            case QUEUE -> "Próximas (" + song().queue().size() + ")";
            case PLAYLISTS -> "Playlists";
            case PLAYLIST -> openPlaylist == null ? "" : "♪ " + openPlaylist.name + " (" + openPlaylist.songs.size() + ")";
        };
    }

    private List<Tool> tools() {
        List<Tool> t = new ArrayList<>();
        switch (mode) {
            case LOCAL -> t.add(new Tool("Abrir pasta", 70, () -> openFolder(ClientFiles.musicDir())));
            case QUEUE -> {
                boolean auto = song().autoplay();
                t.add(new Tool(auto ? "Fila automática: §aSim" : "Fila automática: §cNão", 120, () -> {
                    control(Payloads.Control.AUTOPLAY, auto ? 0 : 1);
                    setStatus(auto ? "Fila automática desligada"
                            : "Fila automática ligada: quando a fila acabar, entram músicas parecidas");
                }));
                t.add(new Tool("Limpar", 50, () -> {
                    control(Payloads.Control.QUEUE_CLEAR, 0);
                    setStatus("Fila limpa");
                }));
            }
            case PLAYLISTS -> {
                t.add(new Tool("+ Nova", 54, this::createPlaylist));
                t.add(new Tool("Abrir pasta", 70, () -> openFolder(Playlists.dir())));
            }
            case PLAYLIST -> {
                t.add(new Tool("◀", 20, () -> setMode(Mode.PLAYLISTS)));
                t.add(new Tool("▶ Tudo", 44, () -> playPlaylist(false, 0)));
                t.add(new Tool("Aleatório", 52, () -> playPlaylist(true, 0)));
                t.add(new Tool("+ Tocando", 56, this::addCurrentToPlaylist));
                t.add(new Tool("+ Link", 40, this::addLinkToPlaylist));
                t.add(new Tool(confirmDelete ? "§cConfirmar" : "Apagar", 50, this::deletePlaylist));
            }
            default -> {}
        }
        return t;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        switch (mode) {
            case SEARCH -> {
                for (YouTube.Result r : searchResults) {
                    String d = YouTube.formatDuration(r.durationSec());
                    QueueItem item = new QueueItem(r.url(), r.title(), r.durationSec());
                    rows.add(new Row(r.title() + (d.isEmpty() ? "" : "  §7" + d), () -> play(item), List.of(
                            new Side("+Fila", 32, () -> enqueue(List.of(item))),
                            new Side("+PL", 26, () -> addToPlaylist(new Playlists.Song(r.url(), r.title(), r.durationSec()))))));
                }
            }
            case LOCAL -> {
                Path dir = ClientFiles.musicDir();
                for (Path p : ClientFiles.listMusic()) {
                    rows.add(new Row(dir.relativize(p).toString(),
                            () -> ClientFiles.playLocal(target, p, this::statusLater), List.of(
                            new Side("+Fila", 32, () -> enqueueSongs(List.of(Playlists.fileSong(p)), false)),
                            new Side("+PL", 26, () -> addToPlaylist(Playlists.fileSong(p))))));
                }
            }
            case QUEUE -> {
                List<QueueItem> q = song().queue();
                for (int i = 0; i < q.size(); i++) {
                    int idx = i;
                    QueueItem item = q.get(i);
                    String d = YouTube.formatDuration(item.durationSec());
                    rows.add(new Row((i + 1) + ". " + item.title() + (d.isEmpty() ? "" : "  §7" + d), () -> {
                        control(Payloads.Control.QUEUE_PLAY_INDEX, idx);
                        setStatus("Tocando: " + item.title());
                    }, List.of(new Side("✕", 20, () -> control(Payloads.Control.QUEUE_REMOVE, idx)))));
                }
            }
            case PLAYLISTS -> {
                for (Playlists.Playlist pl : Playlists.all()) {
                    rows.add(new Row("♪ " + pl.name + "  §7" + pl.songs.size() + " música(s)", () -> {
                        openPlaylist = pl;
                        lastPlaylist = pl.name;
                        setMode(Mode.PLAYLIST);
                    }, List.of(new Side("▶", 20, () -> {
                        openPlaylist = pl;
                        playPlaylist(false, 0);
                    }))));
                }
            }
            case PLAYLIST -> {
                if (openPlaylist != null) {
                    for (int i = 0; i < openPlaylist.songs.size(); i++) {
                        int idx = i;
                        Playlists.Song s = openPlaylist.songs.get(i);
                        String d = YouTube.formatDuration(s.durationSec());
                        rows.add(new Row((i + 1) + ". " + s.title() + (d.isEmpty() ? "" : "  §7" + d),
                                () -> playPlaylist(false, idx), List.of(
                                new Side("▲", 16, () -> { Playlists.move(openPlaylist, idx, -1); refresh(); }),
                                new Side("▼", 16, () -> { Playlists.move(openPlaylist, idx, 1); refresh(); }),
                                new Side("✕", 18, () -> { Playlists.remove(openPlaylist, idx); refresh(); }))));
                    }
                }
            }
            default -> {}
        }
        return rows;
    }

    private final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), volume / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Volume: " + (int) Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(value * 100);
            if (v != volume) {
                volume = v;
                control(Payloads.Control.VOLUME, v);
            }
        }
    }

    private void refresh() {
        if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
    }

    private void setStatus(String s) {
        status = s;
    }

    private void statusLater(String s) {
        Minecraft.getInstance().execute(() -> setStatus(s));
    }

    // ------------------------------------------------------------ ações: tocar

    private void control(int action, int value) {
        ClientPlayNetworking.send(new Payloads.Control(target, action, value));
    }

    private void togglePause() {
        ClientBoomboxManager.State st = state();
        if (st == null) {
            setStatus("Nada tocando");
            return;
        }
        if (st.song.paused()) {
            control(Payloads.Control.RESUME, 0);
            setStatus("Continuando...");
        } else {
            control(Payloads.Control.PAUSE, 0);
            setStatus("Pausado");
        }
    }

    private static boolean isUrl(String s) {
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("www.") || s.startsWith("youtu");
    }

    private static String normalizeUrl(String s) {
        return s.startsWith("http") ? s : "https://" + s;
    }

    private void playInput() {
        String t = text.trim();
        if (t.isEmpty()) {
            setStatus("Digite um link ou o nome de uma música");
            return;
        }
        if (!isUrl(t)) {
            search(t);
            return;
        }
        String url = normalizeUrl(t);
        async("Carregando link...", () -> infoOrUrl(url), r -> play(new QueueItem(r.url(), r.title(), r.durationSec())));
    }

    private static YouTube.Result infoOrUrl(String url) {
        try {
            return YouTube.info(url);
        } catch (Exception e) {
            return new YouTube.Result(url, url, 0);
        }
    }

    private void play(QueueItem item) {
        ClientPlayNetworking.send(new Payloads.PlayRequest(target, item.url(), item.title(), item.durationSec()));
        setStatus("Preparando: " + item.title());
    }

    private void enqueue(List<QueueItem> items) {
        if (items.isEmpty()) return;
        ClientPlayNetworking.send(new Payloads.QueueEdit(target, Payloads.QueueEdit.APPEND, items));
        setStatus(items.size() == 1 ? "Na fila: " + items.get(0).title() : items.size() + " músicas na fila");
    }

    /** Coloca músicas (links ou arquivos) na fila, ou toca agora (playNow). */
    private void enqueueSongs(List<Playlists.Song> songs, boolean playNow) {
        if (songs.isEmpty()) {
            setStatus("A playlist está vazia");
            return;
        }
        boolean hasFiles = songs.stream().anyMatch(Playlists.Song::isFile);
        setStatus(hasFiles ? "Enviando suas músicas para o servidor..." : "Carregando...");
        Playlists.toQueueItems(songs).whenComplete((items, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null || items == null || items.isEmpty()) {
                setStatus("Erro: nenhuma música pôde ser carregada");
                return;
            }
            if (playNow) {
                ClientPlayNetworking.send(new Payloads.QueueEdit(target, Payloads.QueueEdit.PLAY_NOW, items));
                setStatus("Tocando: " + items.get(0).title() + (items.size() > 1 ? " (+" + (items.size() - 1) + " na fila)" : ""));
            } else {
                enqueue(items);
            }
        }));
    }

    private void search(String query) {
        if (query.isEmpty()) {
            setStatus("Digite o que pesquisar");
            return;
        }
        async("Pesquisando \"" + query + "\" no YouTube...", () -> {
            try {
                return YouTube.search(query, 24);
            } catch (Exception e) {
                throw new RuntimeException(cause(e));
            }
        }, results -> {
            searchResults = results;
            mode = Mode.SEARCH;
            page = 0;
            setStatus(results.isEmpty() ? "Nada encontrado" : "Clique para tocar, +Fila para a fila, +PL para a playlist");
        });
    }

    // ------------------------------------------------------------ ações: playlists

    private Playlists.Playlist targetPlaylist() {
        if (mode == Mode.PLAYLIST && openPlaylist != null) return openPlaylist;
        if (lastPlaylist != null) {
            Playlists.Playlist p = Playlists.get(lastPlaylist);
            if (p != null) return p;
        }
        Playlists.Playlist fav = Playlists.create("Favoritas");
        lastPlaylist = fav.name;
        return fav;
    }

    private void addToPlaylist(Playlists.Song song) {
        Playlists.Playlist p = targetPlaylist();
        boolean added = Playlists.add(p, song);
        setStatus(added ? "Adicionada em \"" + p.name + "\": " + song.title() : "Já está em \"" + p.name + "\"");
    }

    private void createPlaylist() {
        String name = text.trim();
        if (name.isEmpty() || isUrl(name)) {
            setStatus("Digite o nome da nova playlist no campo de cima");
            return;
        }
        Playlists.Playlist p = Playlists.create(name);
        openPlaylist = p;
        lastPlaylist = p.name;
        text = "";
        setMode(Mode.PLAYLIST);
        setStatus("Playlist criada! Pesquise músicas e use +PL, ou cole um link e use \"+ Link\"");
    }

    private void playPlaylist(boolean shuffle, int from) {
        if (openPlaylist == null) return;
        List<Playlists.Song> songs = new ArrayList<>(openPlaylist.songs.subList(Math.min(from, openPlaylist.songs.size()), openPlaylist.songs.size()));
        if (shuffle) Collections.shuffle(songs);
        enqueueSongs(songs, true);
    }

    private void addCurrentToPlaylist() {
        SongData s = song();
        if (!s.hasSong()) {
            setStatus("Nada tocando agora");
            return;
        }
        addToPlaylist(new Playlists.Song(s.url(), s.title(), s.durationSec()));
        refresh();
    }

    private void addLinkToPlaylist() {
        String t = text.trim();
        if (!isUrl(t)) {
            setStatus("Cole um link no campo de cima");
            return;
        }
        String url = normalizeUrl(t);
        async("Carregando link...", () -> infoOrUrl(url), r -> {
            addToPlaylist(new Playlists.Song(r.url(), r.title(), r.durationSec()));
            text = "";
        });
    }

    private void deletePlaylist() {
        if (openPlaylist == null) return;
        if (!confirmDelete) {
            confirmDelete = true;
            setStatus("Clique de novo para apagar \"" + openPlaylist.name + "\"");
            refresh();
            return;
        }
        String name = openPlaylist.name;
        Playlists.delete(openPlaylist);
        if (name.equals(lastPlaylist)) lastPlaylist = null;
        openPlaylist = null;
        setMode(Mode.PLAYLISTS);
        setStatus("Playlist \"" + name + "\" apagada");
    }

    // ------------------------------------------------------------ utilidades

    private static void openFolder(Path dir) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"explorer.exe", dir.toAbsolutePath().toString()}
                : os.contains("mac") ? new String[]{"open", dir.toAbsolutePath().toString()}
                : new String[]{"xdg-open", dir.toAbsolutePath().toString()};
        try {
            new ProcessBuilder(cmd).start();
        } catch (Exception ignored) {}
    }

    private <T> void async(String message, Supplier<T> work, Consumer<T> done) {
        if (busy) return;
        busy = true;
        setStatus(message);
        CompletableFuture.supplyAsync(work, Tools.EXEC).whenComplete((r, err) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (err != null) setStatus("Erro: " + cause(err));
            else done.accept(r);
            refresh();
        }));
    }

    private static String cause(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private String fit(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && font.width(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    // ------------------------------------------------------------ desenho

    private int lastQueueHash;
    private boolean lastAuto;

    @Override
    public void tick() {
        super.tick();
        // a fila mudou (ex.: fila automática chegou)? redesenha a lista
        SongData s = song();
        int h = s.queue().hashCode();
        if (mode == Mode.QUEUE && (h != lastQueueHash || s.autoplay() != lastAuto)) refresh();
        lastQueueHash = h;
        lastAuto = s.autoplay();
        if (lockedForMe() != builtLocked) refresh(); // alguém ligou/desligou o Modo DJ
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        ClientBoomboxManager.State st = state();
        if (pauseButton != null) {
            boolean paused = st != null && st.song.paused();
            pauseButton.setMessage(Component.literal(paused ? "▶ Continuar" : "❚❚ Pausar"));
            pauseButton.active = st != null;
        }

        SpeakerSettings set = settings();
        if (lightButton != null) lightButton.setMessage(Component.literal(lightLabel(set)));
        if (colorButton != null) {
            colorButton.setMessage(colorLabel(set));
            SpeakerType ty = speakerType();
            colorButton.active = !builtLocked && ty != null && ty.isParty() && set.light() == SpeakerSettings.LIGHT_FIXED;
        }
        if (djButton != null) djButton.setMessage(Component.literal(djLabel(set)));

        if (syncButton != null && target.isBlock()) {
            int n = syncedCount();
            syncButton.setMessage(Component.literal(n > 1 ? "§b⇄ Juntas: " + n : "⇄ Sincronizar"));
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int left = cx - W / 2;
        g.centeredText(font, "§l♪ " + speakerName() + " ♪", cx, 6, 0xFFFFFFFF);

        String now;
        long startsIn = ClientBoomboxManager.startsInMs(st);
        if (st == null) now = "§7Nada tocando";
        else if (st.song.paused()) now = "§6Pausado: §f" + st.song.title() + " §7(" + YouTube.formatDuration(Math.max(1, st.song.pausedPosMs() / 1000)) + ")";
        else if (startsIn > 0) now = "§eComeçando em " + ((startsIn + 999) / 1000) + "s: §f" + st.song.title();
        else if (ClientBoomboxManager.isLoading(st)) now = "§eCarregando: §f" + st.song.title();
        else if (st.error() != null) now = "§cErro: " + st.error();
        else if (st.finished()) now = "§7Terminou: " + st.song.title();
        else {
            long pos = Math.max(0, st.song.positionMs(ClientBoomboxManager.serverNow()) / 1000);
            String dur = st.song.durationSec() > 0 ? " / " + YouTube.formatDuration(st.song.durationSec()) : "";
            now = "§aTocando: §f" + st.song.title() + " §7(" + YouTube.formatDuration(Math.max(1, pos)) + dur + ")";
        }
        g.centeredText(font, fit(now, W), cx, 18, 0xFFFFFFFF);

        String lt = listTitle();
        int toolsW = 0;
        for (Tool t : tools()) toolsW += t.width() + 3;
        int avail = W - toolsW - 4;
        if (!lt.isEmpty() && avail > 24) g.text(font, fit(lt, avail), left, 128, 0xFFAAAAAA);

        if (builtLocked) {
            int sy = LIST_TOP + rowsPerPage * 21 + 6;
            g.centeredText(font, "§c⚑ Modo DJ: só " + set.lockedBy() + " pode mexer nesta caixa", cx, sy, 0xFFFFFFFF);
        } else if (!status.isEmpty()) {
            int sy = LIST_TOP + rowsPerPage * 21 + 6;
            g.centeredText(font, fit(status, W - 60), cx, sy, 0xFFFFFF55);
        } else if (mode == Mode.NONE) {
            g.centeredText(font, "§7Pesquise uma música ou abra a Fila / Playlists / Músicas", cx, LIST_TOP + 6, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (input != null && input.isFocused() && (event.key() == 257 || event.key() == 335)) { // Enter
            playInput();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
