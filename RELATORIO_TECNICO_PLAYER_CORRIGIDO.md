# Relatório técnico — UnlockT3am Player corrigido

Data da análise: 17 de agosto de 2026

## Escopo

Esta análise e as correções foram feitas exclusivamente no projeto atual **Unlock player / UnlockT3am Player 1.0**. Nenhum projeto anterior foi usado como referência ou comparativo.

## Resultado executivo

| Área | Problema encontrado | Correção aplicada | Situação |
|---|---|---|---|
| Controles na TV | O dashboard e a navegação continuavam compostos atrás do vídeo; eventos de `KeyDown` podiam mudar o foco fora do player | O player passou a ser a única árvore de interface enquanto houver conteúdo selecionado; teclas do controle são consumidas e tratadas dentro do player | Corrigido no código e compilado |
| 4K e qualidade | A seleção de faixas não removia todas as limitações e não havia controle real de qualidade | Limites de resolução removidos, seleção restrita ao que o decoder suporta e três modos reais: Automática, Máxima suportada/4K e Economia até 720p | Corrigido; depende do hardware/codec da TV |
| Decodificação | A opção de hardware era persistida, mas não controlava de forma confiável a criação dos renderizadores | A opção agora seleciona decoder preferencial de hardware ou software, com fallback de decoder habilitado | Corrigido |
| Buffer | Os nomes Pequeno/Médio/Grande não ofereciam ajuste numérico real e os limites eram baixos | Controle persistente de reserva máxima entre 5 e 120 s e início entre 1 e 5 s, aplicado diretamente ao `DefaultLoadControl` | Corrigido |
| Transmissão | O fluxo antigo não garantia Cast do conteúdo com o aparelho remetente permanecendo como controle | Integração real do Google Cast com `CastPlayer`, seletor nativo de dispositivo e `ExoPlayer` local ligado ao controlador remoto | Corrigido para formatos/URLs aceitos pelo receptor |
| Layout TV/celular | Partes da home decidiam o layout pelo aparelho físico e ignoravam o modo escolhido | Home e player usam o modo salvo; cards, banner, quantidade de itens e foco têm dimensões próprias para TV e celular | Corrigido |
| Jogos do Dia | O filtro não reconhecia “jogo/jogos”, campeonatos e várias categorias esportivas | Busca normalizada, sem diferença de acentos, com mais termos e prioridade para a categoria “Jogos do Dia” | Corrigido |
| Top Filmes / Top Séries | As seções não existiam com esses nomes e dependiam da ordem bruta da API | Seções criadas e ordenadas por categoria de destaque/popularidade e avaliação do catálogo | Corrigido |
| Diagnóstico do player | Codec, bitrate, FPS, latência e estabilidade eram valores fixos de demonstração | Painel agora mostra saída local/Cast, faixas selecionadas, resolução, bitrate, FPS, buffer atual, reserva e rebuffers reais | Corrigido |

## Alterações de reprodução e estabilidade

### Sessão do player

- Cada tela de reprodução cria e encerra uma sessão determinística.
- O `ExoPlayer`, listeners e `CastPlayer` são liberados juntos ao trocar ou fechar conteúdo.
- O estado remoto/local usa a mesma interface `Player`, reduzindo divergências de controle.
- Erros de codec, conexão e timeout têm mensagens específicas.
- Políticas de nova tentativa foram diferenciadas: mais tolerância para canais ao vivo e menos para VOD.
- O vídeo usa `FIT` inicialmente, evitando corte causado pelo antigo preenchimento forçado.

### Rede

- Cronet usa HTTP/2 e QUIC quando o provedor está disponível.
- A inicialização foi protegida contra chamadas concorrentes.
- O executor passou de uma para quatro threads.
- O fallback HTTP aceita redirecionamento entre protocolos e usa 30 s para conexão/leitura.
- A reserva configurável reduz a sensibilidade a oscilações, mas não aumenta a velocidade real da internet.

### Buffer real

| Controle | Faixa | Efeito |
|---|---:|---|
| Reserva máxima | 5–120 s, passos de 5 s | Quanto conteúdo o player tenta manter adiantado |
| Iniciar após | 1–5 s | Quanto é carregado antes de começar |
| Mínimo interno | Aproximadamente 1/3 da reserva, mínimo de 3 s | Mantém os valores aceitos pelo Media3 coerentes |
| Reinício após travamento | Início + 1 s, limitado ao mínimo | Evita retornar cedo demais depois de um rebuffer |

Sugestões práticas:

- Internet estável: 30–50 s / início em 1–2 s.
- Wi‑Fi instável: 60–90 s / início em 2–3 s.
- Troca rápida de canais: 10–20 s / início em 1 s.
- Reserva muito alta aumenta memória, tempo de troca e tráfego; ela não corrige servidor lento.

## Qualidade, 4K e codecs

O projeto agora usa Media3 1.11.0 e não impõe limite de tamanho de vídeo ou viewport. A escolha respeita as capacidades reais informadas pelo decoder.

| Modo | Regra aplicada |
|---|---|
| Automática | ABR escolhe a faixa conforme rede e capacidade do aparelho |
| Máxima suportada / 4K | Força a faixa de maior bitrate que o renderer declara suportar |
| Economia | Limita a 1280×720 e 2,5 Mbps |

Para evitar tela preta, uma faixa acima das capacidades do renderer não é forçada. Assim, uma TV sem decoder HEVC Main10, AV1, perfil/nível adequado ou suporte HDR não receberá uma promessa falsa de 4K. Nessa situação o app escolhe a melhor faixa suportada; se a fonte tiver somente um codec incompatível, é exibido um erro de codec.

O painel técnico permite confirmar o que foi realmente selecionado, por exemplo `3840×2160`, codec e bitrate, em vez de exibir “4K” baseado apenas no nome do canal.

## Google Cast: conteúdo remoto com o app como controle

Foi configurado o Cast Framework e o receptor padrão do Google. O botão nativo abre os dispositivos disponíveis. O `CastPlayer` recebe o mesmo item de mídia e usa o player local como fallback/controlador.

Durante uma sessão remota:

- somente a mídia selecionada é enviada ao receptor; não é espelhamento da tela inteira;
- play, pause, posição e troca de item continuam controláveis no aparelho remetente;
- o player mostra um indicador de transmissão ativa;
- título, descrição e capa são enviados como metadados.

Limites do receptor padrão:

- a URL precisa ser alcançável diretamente pelo Chromecast/TV na rede;
- formatos e codecs precisam ser aceitos pelo receptor;
- streams que exigem headers HTTP privados, cookies, VPN somente no celular ou DRM específico podem exigir um receptor Cast personalizado;
- a seleção de qualidade do receptor pode ser independente da seleção local.

## Controle remoto e isolamento da interface na TV

Enquanto o player está aberto, o dashboard, sidebar e barra inferior deixam de ser compostos. Isso elimina o vazamento estrutural de foco e de eventos.

| Tecla | Ação no player |
|---|---|
| OK/Enter ou Play/Pause | Alterna reprodução e mostra os controles |
| Play | Reproduz |
| Pause | Pausa |
| Esquerda/Direita | Retrocede/avança 15 s em filmes e episódios |
| Cima/Baixo | Canal anterior/próximo ao vivo; abre lista em VOD/séries |
| Menu/Guide | Abre a lista de canais ou episódios |
| Info | Abre detalhes técnicos reais |
| Voltar | Fecha primeiro lista/controles/detalhes; depois fecha o player |

Os eventos relevantes de `KeyDown` são consumidos dentro do player antes da ação em `KeyUp`, impedindo a navegação padrão do Android de mover o foco para outra interface.

## Layouts separados

O modo escolhido em Configurações é a fonte principal da interface. A detecção física é usada somente antes de uma escolha.

No modo TV:

- sidebar e layout horizontal;
- banner com 300 dp;
- até 12 itens por seção;
- cards maiores e borda verde de foco;
- controles por D‑pad e teclas de mídia.

No modo celular:

- navegação inferior;
- banner com 220 dp;
- até 8 itens por seção;
- cards menores;
- gestos de brilho, volume e busca, além do botão Cast.

## Tela inicial

### Jogos do Dia

O texto é normalizado para minúsculas e sem acentos. O filtro reconhece, entre outros: jogo, jogos, partida, esporte, sport, futebol, Premiere, ESPN, SporTV, Combate, UFC, NBA, NFL, Champions, Libertadores, Brasileirão e campeonato. Itens cuja categoria contém “Jogos do Dia” recebem prioridade.

### Top Filmes e Top Séries

As seções aparecem sempre que o catálogo correspondente não está vazio. A ordenação prioriza categorias com “Top”, “Mais assistidos”, “Popular”, “Destaque”, “Lançamento” ou “Recomendado” e depois usa a avaliação numérica disponível. Se o provedor não fornece esses metadados, o conteúdo ainda aparece, preservando uma ordem determinística estável.

## Correções adicionais

- Picture-in-Picture agora verifica Android 8/API 26 antes de chamar APIs que não existem no Android 7.
- Favoritos de séries passaram a observar o `StateFlow`, atualizando a tela quando os dados mudam.
- A solicitação de notificação sem permissão declarada e sem uso pelo player foi removida.
- Gradle Wrapper foi alinhado para 9.3.1, versão compatível com o Android Gradle Plugin 9.1.1 usado pelo projeto.
- `compileSdk` foi alinhado ao SDK 36 estável.
- Media3 foi atualizado de 1.5.1 para 1.11.0.
- Testes de template quebrados foram substituídos por testes reais de buffer, qualidade e modelo.

## Validação executada

| Verificação | Resultado |
|---|---|
| `assembleDebug` | Sucesso |
| Testes unitários | 6 executados, 0 falhas |
| Android Lint | 0 erros, 32 avisos, 4 sugestões |
| Classes duplicadas | Verificação aprovada |
| Assinatura do APK | APK Signature Scheme v2 válida, certificado de debug |
| Pacote | `com.aistudio.unlockplayer.kuyzba` |
| minSdk / targetSdk / compileSdk | 24 / 36 / 36 |
| Tamanho do APK | 36.217.746 bytes |
| SHA‑256 | `71393a248939d59ebf71f036dbc078c18f556fd8191bc79a53a1cd64bed54a9e` |

Os 32 avisos restantes são não bloqueantes: APIs visuais depreciadas, recursos antigos não usados, recomendações de ícone, versões opcionais de dependências e avisos de estilo/performance. Nenhum é erro de compilação, execução do player ou segurança de tipo do novo fluxo.

## O que ainda precisa de teste em aparelhos reais

O ambiente de compilação não possui a TV do usuário, Chromecast nem credenciais de um provedor IPTV. Portanto, o seguinte roteiro deve ser executado antes de publicar:

1. TV Android/Google TV com H.264 1080p ao vivo.
2. HEVC 2160p SDR e HEVC Main10/HDR, verificando o painel técnico.
3. AV1 2160p em aparelho compatível.
4. HLS adaptativo alternando Automática e Máxima suportada.
5. MPEG‑TS direto, MP4, MKV e WebM.
6. Wi‑Fi 2,4 GHz e 5 GHz com reservas de 10, 50 e 90 s.
7. Todos os botões de D‑pad, mídia, Menu, Guide, Info e Voltar.
8. Troca rápida entre 20 canais para observar memória e liberação de sessão.
9. Cast de HLS e MP4 para Chromecast/Google TV, confirmando controle pelo celular.
10. Catálogo real com categorias “Jogos do Dia”, “Top Filmes” e “Top Séries”.

## Itens opcionais para uma próxima etapa

- Receptor Cast personalizado para headers, cookies, DRM ou telemetria própria.
- Seletor completo de faixa de áudio, legenda e resolução individual.
- MediaSession e notificação de reprodução para controles externos.
- Métricas de QoE: tempo para primeiro frame, taxa de rebuffers, erros por codec e CDN.
- Testes instrumentados em uma matriz de TVs físicas.
- APK/AAB de produção assinado com o keystore oficial.
- Inclusão de `google-services.json` se os recursos Firebase do projeto forem usados em produção.

## Referências oficiais

- [Media3 — versões](https://developer.android.com/jetpack/androidx/releases/media3)
- [Media3 Cast](https://developer.android.com/media/media3/cast)
- [Formatos suportados pelo ExoPlayer](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Seleção de faixas](https://developer.android.com/media/media3/exoplayer/track-selection)
- [Customização e LoadControl](https://developer.android.com/media/media3/exoplayer/customization)
- [Foco em Compose para TV/D-pad](https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior)
- [Compatibilidade do Android Gradle Plugin 9.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes)

