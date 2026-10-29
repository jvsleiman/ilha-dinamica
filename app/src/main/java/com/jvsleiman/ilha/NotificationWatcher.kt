package com.jvsleiman.ilha

import android.service.notification.NotificationListenerService

/**
 * Etapa 1: só existe para o app aparecer em "Acesso às notificações".
 * Na etapa 2 passa a ler músicas (sessões de mídia) e notificações.
 */
class NotificationWatcher : NotificationListenerService()
