# Limitações conhecidas

O D.I.A usa só APIs oficiais do Android e não inventa dados: se um app não publica uma
informação, ela não aparece na ilha.

## Versão do Android
- **Mínimo: Android 11 (API 30).** A posição da ilha depende de `WindowMetrics` e dos insets de recorte da tela, que só existem a partir do 11.
- **Android 12+**: o app de chamadas informa se a chamada está tocando ou em andamento (`android.callType`). No 11, isso é deduzido (cronômetro ligado = em andamento).
- **Android 13+**: permissões de acessibilidade e notificações vêm travadas em apps instalados por APK. Libere em Informações do app → ⋮ → *Permitir configurações restritas*. A notificação de "versão nova" precisa da permissão de notificações.
- **Android 14+**: abrir apps a partir da ilha usa o modo de início em segundo plano permitido pelo sistema; alguns apps podem ignorar.

## Por que acessibilidade?
Só uma janela de acessibilidade (`TYPE_ACCESSIBILITY_OVERLAY`) consegue ficar sobre a barra de status,
em volta da câmera. O serviço não lê o conteúdo das telas (`canRetrieveWindowContent=false`) e não recebe
eventos de digitação.

## Integrações
- **Chamadas**: atender/recusar só existem quando o app de chamadas oferece esses botões na notificação. Nome e foto vêm da própria notificação (sem acessar seus contatos).
- **Temporizadores de outros apps**: aparecem se o app Relógio mostrar a contagem na notificação (cronômetro). Relógios com notificação personalizada (RemoteViews) não expõem o tempo.
- **Navegação**: aparece o texto que o app de mapas coloca na notificação. Setas/ícones de manobra não são expostos.
- **Gravação / compartilhamento de tela**: detectados só quando o app publica uma notificação com cronômetro.
- **Música**: depende do player ter uma sessão de mídia. Alguns players não informam a duração (aí não há barra de progresso).
- **Tela cheia**: detectada pela visibilidade da barra de status. Em alguns fabricantes a informação pode atrasar ou não chegar; dá para desligar em Ajustes.

## Fabricantes
- **Motorola** (incl. Edge 60 Fusion): deixe a bateria do D.I.A como *Sem restrições*. O recorte da câmera é informado pelo sistema; use *Detectar tela novamente* após mudar o tamanho de exibição.
- **Xiaomi / HyperOS**: ative *Início automático* e *Sem restrições* de bateria; algumas versões escondem sobreposições na tela de bloqueio.
- **Samsung / One UI**: tire o app de *Apps em suspensão*.
- **Realme / OnePlus / ColorOS**: permita *Execução em segundo plano*.
- **Pixel**: sem ajustes extras conhecidos.

## O que ainda não foi validado
- Os testes automatizados cobrem as regras puras (prioridades, temporizador, versões, botões de chamada). Os comportamentos com o sistema (sobreposição, insets, chamadas reais) precisam ser testados em aparelho físico; reporte o que não funcionar no seu modelo.
- Gestos e tempos foram ajustados para o Motorola Edge 60 Fusion; outros aparelhos podem precisar de calibração em Personalizar.
