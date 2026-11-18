# Boombox — mod Fabric para Minecraft 26.3

Uma boombox que toca músicas para todo mundo por perto, sincronizadas em tempo real.

## Instalação
1. Instale o **Fabric Loader 0.19.5+** e o **Fabric API** para Minecraft 26.3.
2. Baixe o `boombox-1.5.0.jar` na aba [Releases](https://github.com/Joaovictor921/Boombox/releases/latest) e coloque-o na pasta `mods` **do cliente de cada jogador e do servidor**.
3. Na primeira vez que alguém entra num mundo, o mod baixa sozinho (Windows) para `.minecraft/boombox/bin`:
   - `yt-dlp` (YouTube), `ffmpeg` (decodificar o áudio) e `deno` (o yt-dlp precisa dele para o YouTube).
   - No Linux/macOS o `ffmpeg` precisa estar instalado (`sudo apt install ffmpeg` / `brew install ffmpeg`).

## Como usar
- Receita: barra de ferro em cima, tábuas + jukebox + tábuas no meio, tábuas + redstone + tábuas embaixo
  (ou pegue no criativo, na aba própria **Boombox e Caixas de Som**).
- Clique com o botão direito na boombox para abrir o menu:
  - **Tocar**: cole um link (YouTube, SoundCloud, link direto de .mp3…) e toque. Se você digitar texto em vez de link, ele pesquisa.
  - **Pesquisar**: busca no YouTube e mostra os resultados; clique em um para tocar.
  - **Minhas músicas**: lista os arquivos em `.minecraft/boombox_musicas` (botão **Abrir pasta**).
    Ao tocar, o arquivo é enviado ao servidor (máx. 50 MB) e os outros jogadores o baixam automaticamente.
  - **Pausar / Continuar**: pausa para todo mundo e continua do mesmo ponto.
  - **Parar**.
  - **Volume** (0–100%): vale para todos que estão ouvindo essa boombox.
- O som é 3D: vem da boombox e diminui até sumir a ~48 blocos. Também segue o controle **Jukebox/Discos** das opções de som.

## Novidades da 1.5
- **Caixas maiores tocam mais alto e mais longe** (a boombox é a menor):
  | Caixa | Volume (perto) | Som chega a |
  |---|---|---|
  | Boombox | 1x | 48 blocos |
  | Caixa de Festa Pequena | 1,4x | 64 blocos |
  | Caixa de Festa Média | 1,9x | 96 blocos |
  | Caixa de Festa Grande | 2,5x | 128 blocos |
- **Botão ⇄ Sincronizar** no menu de qualquer caixa no chão: junta todas as caixas (boombox e caixas de festa)
  numa área de **8x8 chunks** em volta dela (64 blocos para cada lado). Todas passam a tocar a mesma música,
  ao mesmo tempo, com a mesma fila e volume; mexer em qualquer uma (tocar, pausar, pular, volume) vale para todas.
  O botão mostra "Juntas: N"; clique de novo para separar aquela caixa.
  - Só entram caixas em chunks carregados. A sincronização dura até o servidor reiniciar ou a caixa ser quebrada
    (as músicas continuam iguais, mas cada uma volta a seguir a própria fila).

## Novidades da 1.4
- **Caixas de festa** em 3 tamanhos (estilo "party speaker"): gabinete preto em pé, anéis de LED nos
  alto-falantes que mudam de cor e pulsam com o grave, barra de luz que pisca nas batidas fortes.
  Tocam igual à boombox (mesmo menu, fila, playlists), mas só no chão.
  | Caixa | Tamanho | Som chega a | Receita |
  |---|---|---|---|
  | Pequena | ~0,8 bloco, 1 woofer | 64 blocos (1.5) | ferro em cima; concreto preto + bloco musical + concreto preto; concreto preto + redstone + concreto preto |
  | Média | ~1,3 bloco, 2 woofers | 96 blocos (1.5) | ferro + pó de glowstone + ferro; concreto preto + **caixa pequena** + concreto preto; concreto preto + bloco de redstone + concreto preto |
  | Grande | 1,5 bloco, 2 woofers grandes, rodinhas e alça de puxar | 128 blocos (1.5) | ferro + glowstone + ferro; concreto preto + **caixa média** + concreto preto; concreto preto + jukebox + concreto preto |
- **Alto-falantes não saem mais para fora** com grave forte: o aro ficou mais fundo e o curso do cone é limitado
  (vale para a boombox também).
- **Aba própria no modo criativo** com todas as caixas do mod.

## Novidades da 1.3
- **Começa do zero**: ao tocar uma música, todo mundo tem ~3 segundos para carregar ("Começando em 3s...")
  e a música começa junto para todos, do início, sem perder nada.
- **Fila**: use **+Fila** nos resultados da pesquisa ou nas suas músicas. A aba **Fila** mostra as próximas;
  clique numa para tocar já, ou **✕** para tirar. **⏭ Pular** passa para a próxima.
- **Fila automática** (ligada por padrão, na aba Fila): quando a fila acaba, entram músicas parecidas com a que
  está tocando (o "Mix" do YouTube daquela música), sem repetir as que já tocaram.
- **Playlists**: na aba **Playlists**, digite um nome no campo de cima e clique **+ Nova**. Dentro dela:
  **▶ Tudo**, **Aleatório**, **+ Tocando** (adiciona a música atual), **+ Link** (adiciona o link do campo),
  **▲▼** para reordenar e **✕** para tirar. Nos resultados da pesquisa, **+PL** adiciona na última playlist aberta
  (ou em "Favoritas"). Suas músicas da pasta `boombox_musicas` também podem entrar em playlists.
- As playlists ficam salvas em `.minecraft/boombox_playlists/<nome>.txt`, uma música por linha
  (`link | título | duração`), e são lidas quando o jogo abre. Dá para editar ou compartilhar esses arquivos.

## Novidades da 1.2
- **Levar na mão**: segure a boombox (mão principal ou secundária) e clique com o botão direito **no ar** para abrir o menu.
  Em terceira pessoa o jogador aparece com o braço levantado, segurando a boombox pela alça ao lado da cabeça,
  e o som sai de onde ele estiver. Clicar num bloco coloca a boombox no chão normalmente.
- **A música vai junto**: colocar a boombox no chão ou quebrá-la mantém a música tocando do mesmo ponto.
- **Modelo 3D novo**: alça, antena, toca-fitas, botões, display com equalizador e alto-falantes redondos.
- **Alto-falantes de verdade**: os woofers (grandes) empurram para frente com o **grave** e os tweeters
  (pequenos) vibram com o **agudo**, medidos do áudio que está tocando naquele instante. O equalizador do display
  também pula com a música. Funciona no chão, na mão e no inventário.

## Como a sincronia funciona
O servidor guarda *qual* música e *quando* começou (relógio do servidor). Cada cliente toca a música
por streaming direto do YouTube (sem baixar o arquivo) e pula direto para o ponto certo. Se o YouTube
bloquear o streaming, o mod baixa só o áudio para `.minecraft/boombox/cache` e toca de lá. Assim, quem chega ou se aproxima depois ouve a mesma parte que os outros.
A música continua mesmo se o servidor reiniciar.

## Compilar
Precisa de JDK 25+:
```
./gradlew build   # o jar sai em build/libs/
```

## Versões
| Versão | Minecraft | Download |
|---|---|---|
| 1.5.0 | 26.3 (Fabric) | [Releases → v1.5.0](https://github.com/Joaovictor921/Boombox/releases/tag/v1.5.0) |
| 1.4.0 | 26.3 (Fabric) | [Releases → v1.4.0](https://github.com/Joaovictor921/Boombox/releases/tag/v1.4.0) |
| 1.3.0 | 26.3 (Fabric) | [Releases → v1.3.0](https://github.com/Joaovictor921/Boombox/releases/tag/v1.3.0) |
| 1.2.0 | 26.3 (Fabric) | [Releases → v1.2.0](https://github.com/Joaovictor921/Boombox/releases/tag/v1.2.0) |

Também no CurseForge: [Synced Boombox](https://www.curseforge.com/minecraft/mc-mods/synced-boombox)

## Licença
MIT
