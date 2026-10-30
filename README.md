# D.I.A — Dynamic Island para Android

App Android inspirado na Dynamic Island do iPhone: uma cápsula preta sobre a câmera frontal
que cresce, se divide e mostra música, notificações, chamadas, carregamento, temporizadores
e atividades em andamento — com dados reais do Android, processados só no celular.

## Instalar
Baixe o `IlhaDinamica.apk` mais recente em **Releases** (cada envio para `main` gera um APK
assinado). Depois de instalado, use **Atualizar → Verificar atualizações** dentro do app.

Primeiro uso:
1. Abra o app → **Início → Permissões**.
2. Ative **Acessibilidade** (desenha a ilha) e **Acesso às notificações** (música, avisos, chamadas).
   Se aparecerem bloqueadas: Informações do app → ⋮ → *Permitir configurações restritas*.
3. Libere a **bateria sem restrição** (Motorola, Xiaomi e Samsung fecham apps em segundo plano).
4. Em **Personalizar**, ligue *Arrastar a ilha na tela* e encaixe sobre a câmera, se precisar.

## O que funciona
| Recurso | De onde vêm os dados |
|---|---|
| Música: capa, faixa, artista, play/pausa, anterior/próxima, barra de progresso com toque para pular | `MediaSessionManager` (Spotify, YouTube Music e qualquer player com sessão de mídia) |
| Notificações: faixa, cartão com botões, resposta rápida, contagem por app | `NotificationListenerService` |
| Chamadas: tocando (foto, nome, recusar/atender) e em andamento (duração) | Notificação da chamada (`CATEGORY_CALL`); os botões são os do próprio app |
| Carregador, bateria fraca, carga completa | Broadcasts de bateria do sistema |
| Temporizador e cronômetro próprios (pausar, retomar, cancelar, vibração e som ao terminar) | O próprio D.I.A |
| Temporizadores do app Relógio, navegação, downloads, gravação | Notificações em andamento com cronômetro ou progresso |
| Ilha dividida (bolinha) com a 2ª atividade ou uma notificação de passagem | Gerenciador de prioridades configurável |

Gestos: **toque** abre o app da atividade · **segurar** expande · **deslizar** alterna atividades
(ou troca a faixa, com só música) · **arrastar longe para a esquerda** fecha · **toque fora** recolhe.

## Personalização
Posição X/Y (arrastando na tela ou por ajuste fino), centralização automática, nova detecção da tela,
largura/altura/arredondamento, largura com atividade, raio e margens da forma expandida, opacidade,
tamanho dos textos, escala dos ícones, velocidade e elasticidade das molas, tempos de permanência,
favoritos, apps permitidos, ordem de prioridade, privacidade na tela bloqueada, backup local.

## Arquitetura
- `IslandService` — serviço de acessibilidade: janelas sobrepostas, estados, gestos, prioridades, bateria, temporizador.
- `NotificationWatcher` — música, notificações e atividades em andamento.
- `Island.kt` — a ilha em Jetpack Compose (molas físicas, morphing, bolinha que se separa).
- `Logica.kt` — regras puras com testes (`app/src/test`).
- `Prefs.kt` — ajustes em JSON (SharedPreferences), favoritos e backup.
- `Updater.kt` — versões no GitHub, verificação de SHA-256 e assinatura, instalação pelo instalador do Android, verificação periódica (WorkManager).
- Telas: `MainActivity.kt` (Início, Atualizar), `Telas.kt` (Personalizar, Atividades, Ajustes).

Limitações por versão e fabricante: [LIMITACOES.md](LIMITACOES.md).
