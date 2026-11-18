# Boombox — mod Fabric para Minecraft 26.3

Uma boombox que toca músicas para todo mundo por perto, sincronizadas em tempo real.

## Instalação
1. Instale o **Fabric Loader 0.19.5+** e o **Fabric API** para Minecraft 26.3.
2. Baixe o `boombox-1.2.0.jar` na aba [Releases](https://github.com/Joaovictor921/Boombox/releases/latest) e coloque-o na pasta `mods` **do cliente de cada jogador e do servidor**.
3. Na primeira vez que alguém entra num mundo, o mod baixa sozinho (Windows) para `.minecraft/boombox/bin`:
   - `yt-dlp` (YouTube), `ffmpeg` (decodificar o áudio) e `deno` (o yt-dlp precisa dele para o YouTube).
   - No Linux/macOS o `ffmpeg` precisa estar instalado (`sudo apt install ffmpeg` / `brew install ffmpeg`).

## Como usar
- Receita: barra de ferro em cima, tábuas + jukebox + tábuas no meio, tábuas + redstone + tábuas embaixo
  (ou pegue no criativo, aba *Blocos funcionais*).
- Clique com o botão direito na boombox para abrir o menu:
  - **Tocar**: cole um link (YouTube, SoundCloud, link direto de .mp3…) e toque. Se você digitar texto em vez de link, ele pesquisa.
  - **Pesquisar**: busca no YouTube e mostra os resultados; clique em um para tocar.
  - **Minhas músicas**: lista os arquivos em `.minecraft/boombox_musicas` (botão **Abrir pasta**).
    Ao tocar, o arquivo é enviado ao servidor (máx. 50 MB) e os outros jogadores o baixam automaticamente.
  - **Pausar / Continuar**: pausa para todo mundo e continua do mesmo ponto.
  - **Parar**.
  - **Volume** (0–100%): vale para todos que estão ouvindo essa boombox.
- O som é 3D: vem da boombox e diminui até sumir a ~48 blocos. Também segue o controle **Jukebox/Discos** das opções de som.

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
| 1.2.0 | 26.3 (Fabric) | [Releases → v1.2.0](https://github.com/Joaovictor921/Boombox/releases/tag/v1.2.0) |
| 1.0.0 | 26.3 (Fabric) | em breve |
