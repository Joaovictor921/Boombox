package com.boombox.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * 3.8: o narrador da várzea. Narra no chat (para quem está perto do campo) os lances, os chutes, as defesas, os gols,
 * as bolas paradas e o fim de cada tempo, com frases sorteadas. Só narra jogo marcado por alguém (amistoso, copa,
 * liga ou desafio de pênaltis), não a pelada dos aldeões.
 */
public final class Narrator {
    public static final String NAME = "Narrador da Várzea";

    private static final String[] KICKOFF = {
            "Boa tarde, boa noite, seja lá que hora for! Bola rolando: {a} contra {b}!",
            "Tá valendo! {a} e {b} entram em campo, e o narrador aqui já tá sem voz de tanta emoção!",
            "Atenção, várzea! Começa {a} x {b}. Pega a pipoca e chega mais perto!",
            "Apita o juiz, rola a bola: {a} de um lado, {b} do outro. Que vença o melhor!",
            "Gramado prontinho, torcida na beira do campo: {a} x {b}, começou!"};
    private static final String[] SHOT = {
            "{n} arma o chute...", "Lá vai {n}, soltou a bomba!", "{n} bate pro gol!", "Olha o chute de {n}!",
            "{n} encheu o pé!", "{n} tenta de longe!", "Arriscou {n}!"};
    private static final String[] GOAL = {
            "GOOOOOOOOL! É de {n}! Que pintura, minha gente! {s}",
            "Lá dentro! {n} balança a rede e a várzea vem abaixo! {s}",
            "GOL! GOL! GOL! {n} não perdoou! {s}",
            "Ééé do {t}! {n} manda pro fundo do barbante! {s}",
            "Que golaço! Guarda esse lance, {n}, que vai pro álbum de família! {s}",
            "Estufou a rede! {n} correu pro abraço! {s}"};
    private static final String[] GOAL_TEAM = {
            "GOOOL do {t}! A bola entrou e a torcida enlouqueceu! {s}",
            "Lá dentro! Gol do {t}! {s}",
            "É rede! O {t} marca! {s}",
            "Gol do {t}! Que jogada coletiva, gente! {s}"};
    private static final String[] SAVE = {
            "Que defesa! {n} voou feito passarinho!", "Pegou! {n} fechou o gol!", "Espalmou {n}! Salvou o time!",
            "Defesaça de {n}! Isso aqui é goleiro de seleção!", "Nas mãos de {n}! Muro na meta!",
            "Não passa nada! {n} tá numa tarde inspirada!"};
    private static final String[] WIDE = {
            "Pra fora! Passou raspando a trave!", "Isolou! A bola foi buscar pipa no céu!", "Pra linha de fundo. Quase!",
            "Uuuuh! Tirou tinta da trave!", "Errou o alvo. Tiro de meta.", "Por cima! Faltou calibrar a mira!"};
    private static final String[] PENALTY = {
            "PÊNALTI! O juiz não teve dúvida! Cobrança para o {t}!", "Marca a cal! Pênalti pro {t}!",
            "Derrubou na área: é pênalti! Coração na mão, torcida do {t}!"};
    private static final String[] FREE_KICK = {
            "Falta perigosa pro {t}! Barreira armada...", "Tiro livre pro {t}, perto da área. Olha o perigo!",
            "Falta! A barreira se ajeita, o cobrador do {t} ajeita a bola..."};
    private static final String[] CORNER = {
            "Escanteio pro {t}! Todo mundo na área!", "Vai ter escanteio. O {t} sobe a zaga pra cabecear!",
            "Bola na bandeirinha: escanteio do {t}."};
    private static final String[] WALL = {
            "Na barreira! Os aldeões fecharam a passagem!", "Bateu na barreira! Que muralha!", "A barreira salvou!"};
    private static final String[] HALFTIME = {
            "Fim do primeiro tempo! {s}. Hora do caldo de cana!", "Intervalo na várzea: {s}. Os técnicos vão ter muito o que conversar.",
            "Acabou a primeira etapa. {s}."};
    private static final String[] SECOND_HALF = {
            "Começa o segundo tempo! Tudo pode acontecer!", "Bola rolando de novo! Segunda etapa!",
            "Voltaram do intervalo! Quem vai fazer a diferença?"};
    private static final String[] END = {
            "Fim de papo! {s}. Que jogo, minha gente!", "Apita o árbitro, acabou! {s}.",
            "Termina o jogo! {s}. Obrigado, várzea, e até a próxima!"};
    private static final String[] PLAY_OWN = {
            "{n} domina no campo de defesa e olha pra frente.", "{n} tira com calma lá de trás.", "{n} segura a bola na zaga.",
            "Troca de passes do {t} no próprio campo."};
    private static final String[] PLAY_MID = {
            "{n} conduz pelo meio-campo...", "{n} ajeita, levanta a cabeça e procura alguém.", "Bola com {n}, no círculo central.",
            "O {t} gira a bola no meio de campo.", "{n} carrega pela {side}!"};
    private static final String[] PLAY_ATTACK = {
            "{n} invade a área! Perigo!", "{n} chega na cara do gol!", "{n} vai pra cima da zaga!",
            "Pressão do {t} lá na frente!", "{n} avança pela {side}, olha o cruzamento!"};
    private static final String[] CH_START = {
            "Desafio de pênaltis valendo {bet} esmeralda(s)! {n} bate cinco, depois pega cinco. Vamos ver quem é bom de verdade!",
            "Atenção: {n} contra o time do Seu Zé, nos pênaltis! {bet} esmeralda(s) na mesa!"};
    private static final String[] CH_GOAL = {"Converteu! {n} mandou no cantinho!", "Gol! {n} deslocou o goleiro!", "Na rede! Frieza total de {n}!"};
    private static final String[] CH_MISS = {"Perdeu! {n} desperdiçou a cobrança!", "Pegou o goleiro do Seu Zé! Que defesa!", "Pra fora! {n} tremeu na hora H!"};
    private static final String[] CH_SAVE = {"Defendeu {n}! Pegou o pênalti!", "{n} adivinhou o canto e pegou!", "Que defesa no pênalti, {n}!"};
    private static final String[] CH_CONCEDE = {"O batedor do Seu Zé não perdoou!", "Gol do time do Seu Zé. {n} foi pro outro canto...", "Entrou! Nada pra fazer, {n}."};

    private final Ball ball;
    private int lastLine = -1000, nextPlay;
    /** Quantas falas já saíram (para o teste). */
    public int lines;

    Narrator(Ball ball) {
        this.ball = ball;
    }

    private boolean active() {
        return ball.mode() != Ball.AUTO;
    }

    private static String pick(String[] a, RandomSource r) {
        return a[r.nextInt(a.length)];
    }

    private String fill(String t, String n, String team) {
        return t.replace("{n}", n == null ? "" : n).replace("{t}", team == null ? "" : team)
                .replace("{s}", ball.scoreLine()).replace("{a}", ball.teamName(Ball.TEAM_A)).replace("{b}", ball.teamName(Ball.TEAM_B));
    }

    /** Fala no chat de quem está perto do campo. force = fala mesmo se acabou de falar (gol, defesa...). */
    void say(String line, boolean force) {
        if (!(ball.level() instanceof ServerLevel sl)) return;
        if (!force && ball.tickCount - lastLine < 60) return;
        lastLine = ball.tickCount;
        lines++;
        Component c = Component.literal("§d🎙 §6<" + NAME + "> §f" + line);
        for (var p : sl.players()) if (p.distanceTo(ball) < 64) p.sendSystemMessage(c);
    }

    void kickoff() {
        if (active() && ball.mode() != Ball.CHALLENGE) say(fill(pick(KICKOFF, ball.getRandom()), null, null), true);
        nextPlay = ball.tickCount + 200;
    }

    void shot(String who) {
        if (active() && ball.mode() != Ball.CHALLENGE) say(fill(pick(SHOT, ball.getRandom()), who, null), true);
    }

    void goal(String who, String team) {
        if (!active() || ball.mode() == Ball.CHALLENGE) return;
        say(who != null ? fill(pick(GOAL, ball.getRandom()), who, team) : fill(pick(GOAL_TEAM, ball.getRandom()), null, team), true);
    }

    void save(String keeper) {
        if (active() && ball.mode() != Ball.CHALLENGE) say(fill(pick(SAVE, ball.getRandom()), keeper, null), true);
    }

    void wide() {
        if (active() && ball.mode() != Ball.CHALLENGE) say(pick(WIDE, ball.getRandom()), true);
    }

    void restart(int kind, String team, boolean wall) {
        if (!active() || ball.mode() == Ball.CHALLENGE) return;
        RandomSource r = ball.getRandom();
        switch (kind) {
            case Ball.PENALTY -> say(fill(pick(PENALTY, r), null, team), true);
            case Ball.FREE_KICK -> { if (wall) say(fill(pick(FREE_KICK, r), null, team), false); }
            case Ball.CORNER -> { if (r.nextInt(2) == 0) say(fill(pick(CORNER, r), null, team), false); }
            default -> { }
        }
    }

    void wallHit() {
        if (active()) say(pick(WALL, ball.getRandom()), true);
    }

    void halftime() {
        if (active()) say(fill(pick(HALFTIME, ball.getRandom()), null, null), true);
    }

    void secondHalf() {
        if (active()) say(pick(SECOND_HALF, ball.getRandom()), true);
        nextPlay = ball.tickCount + 200;
    }

    void end() {
        if (active() && ball.mode() != Ball.CHALLENGE) say(fill(pick(END, ball.getRandom()), null, null), true);
    }

    /** Narração do lance (de vez em quando, com a bola rolando): quem está com ela e onde. */
    void tickPlay(String who, int team, double uFrac, double vSide) {
        if (!active() || ball.mode() == Ball.CHALLENGE || ball.tickCount < nextPlay) return;
        nextPlay = ball.tickCount + 220 + ball.getRandom().nextInt(200);
        if (who == null) return;
        String[] set = uFrac < 0.35 ? PLAY_OWN : uFrac < 0.7 ? PLAY_MID : PLAY_ATTACK;
        String side = vSide < 0 ? "esquerda" : "direita";
        say(fill(pick(set, ball.getRandom()), who, ball.teamName(team)).replace("{side}", side), false);
    }

    // ---- desafio de pênaltis
    void challengeStart(String who, int bet) {
        say(fill(pick(CH_START, ball.getRandom()), who, null).replace("{bet}", Integer.toString(bet)), true);
    }

    void challengeKick(String who, boolean humanKicks, boolean goal) {
        String[] set = humanKicks ? (goal ? CH_GOAL : CH_MISS) : (goal ? CH_CONCEDE : CH_SAVE);
        say(fill(pick(set, ball.getRandom()), who, null), true);
    }

    void line(String text) {
        say(text, true);
    }
}
