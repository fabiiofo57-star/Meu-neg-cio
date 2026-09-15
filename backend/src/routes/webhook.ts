import { Router, Request, Response } from 'express';
import { config } from '../config/env';
import { validateMetaSignature } from '../services/webhookValidator';
import {
  WhatsAppWebhookPayload,
  WhatsAppMessage,
  WhatsAppContact,
} from '../types/whatsapp';
import { messageDispatcher } from '../services/messageDispatcher';

export const webhookRouter = Router();

/**
 * ============================================================================
 * GET /webhook - VALIDAÇÃO INICIAL DO WEBHOOK PELA META
 * ============================================================================
 * Quando você clica em "Verificar e salvar" no painel de desenvolvedores da Meta,
 * a Meta faz uma requisição GET para esta URL com os seguintes parâmetros:
 *
 * - hub.mode: sempre "subscribe"
 * - hub.verify_token: o token que você definiu no painel
 * - hub.challenge: número aleatório gerado pela Meta
 *
 * O servidor deve responder com o mesmo "hub.challenge" e status 200.
 */
webhookRouter.get('/', (req: Request, res: Response): void => {
  const mode = req.query['hub.mode'] as string | undefined;
  const token = req.query['hub.verify_token'] as string | undefined;
  const challenge = req.query['hub.challenge'] as string | undefined;

  console.log('📡 [WEBHOOK GET] Requisição de verificação recebida da Meta:');
  console.log(`- hub.mode: ${mode}`);
  console.log(`- hub.verify_token: ${token ? '******' : '[NÃO INFORMADO]'}`);
  console.log(`- hub.challenge: ${challenge ? '[PRESENTE]' : '[AUSENTE]'}`);

  if (mode === 'subscribe' && token === config.meta.verifyToken) {
    console.log('✅ [WEBHOOK GET] Verificação bem-sucedida! Respondendo com challenge para a Meta.');
    res.status(200).type('text/plain').send(challenge);
  } else {
    console.warn('❌ [WEBHOOK GET] Falha na verificação! Token não confere ou modo inválido.');
    res.status(403).json({
      error: 'Falha na verificação do token',
      message: 'O hub.verify_token fornecido não confere com o token configurado no backend.',
    });
  }
});

/**
 * ============================================================================
 * POST /webhook - RECEBIMENTO DE EVENTOS E MENSAGENS DO WHATSAPP
 * ============================================================================
 * Sempre que um usuário envia texto, áudio ou uma mensagem muda de status,
 * a Meta envia uma notificação POST para este endpoint.
 *
 * Requisitos estritos da Meta:
 * 1. O servidor DEVE responder com HTTP 200 OK prontamente (menos de 3 segundos).
 * 2. O corpo deve ser autenticado usando o cabeçalho X-Hub-Signature-256.
 */
webhookRouter.post(
  '/',
  validateMetaSignature,
  async (req: Request, res: Response): Promise<void> => {
    // 1. Responde 200 imediatamente para a Meta para confirmar o recebimento
    res.status(200).send('EVENT_RECEIVED');

    try {
      const payload = req.body as WhatsAppWebhookPayload;

      // Validação de formato da Meta
      if (payload.object !== 'whatsapp_business_account') {
        console.log(`[WEBHOOK POST] Evento ignorado (object != whatsapp_business_account): ${payload.object}`);
        return;
      }

      if (!payload.entry || payload.entry.length === 0) {
        return;
      }

      // 2. Itera sobre as entradas e mudanças recebidas
      for (const entry of payload.entry) {
        const wabaId = entry.id;

        for (const change of entry.changes) {
          if (change.field !== 'messages') {
            continue;
          }

          const value = change.value;
          const contactsMap = new Map<string, WhatsAppContact>();
          value.contacts?.forEach((c) => contactsMap.set(c.wa_id, c));

          // 2.1 Processamento de Mensagens Recebidas (Texto, Áudio, etc.)
          if (value.messages && value.messages.length > 0) {
            for (const message of value.messages) {
              const contact = contactsMap.get(message.from);
              await handleIncomingMessage(message, contact, value.metadata.phone_number_id, wabaId);
            }
          }

          // 2.2 Notificações de Status de Envio (sent, delivered, read, failed)
          if (value.statuses && value.statuses.length > 0) {
            for (const status of value.statuses) {
              handleMessageStatus(status);
            }
          }
        }
      }
    } catch (error) {
      console.error('❌ [WEBHOOK POST] Erro inesperado ao processar evento:', error);
    }
  }
);

/**
 * Ponto central para recepção e roteamento da mensagem recebida.
 * Na Etapa 2, validamos e registramos a estrutura de forma robusta.
 * Nas Etapas 3 a 8, este método acionará a identificação do UID e o processador de IA.
 */
async function handleIncomingMessage(
  message: WhatsAppMessage,
  contact: WhatsAppContact | undefined,
  phoneNumberId: string,
  wabaId: string
): Promise<void> {
  const senderPhone = message.from;
  const senderName = contact?.profile?.name || 'Empreendedor';

  console.log('----------------------------------------------------');
  console.log(`📥 [MENSAGEM WHATSAPP RECEBIDA]`);
  console.log(`- De: ${senderName} (+${senderPhone})`);
  console.log(`- Tipo: ${message.type}`);
  console.log(`- ID da Mensagem: ${message.id}`);
  console.log(`- Timestamp: ${new Date(parseInt(message.timestamp, 10) * 1000).toISOString()}`);
  console.log(`- Phone Number ID: ${phoneNumberId} | WABA ID: ${wabaId}`);

  try {
    // Encaminha para o despachante central com controle de sessão e IA
    await messageDispatcher.dispatchMessage(message);
  } catch (error) {
    console.error(`❌ [DISPATCHER] Erro ao processar mensagem ${message.id}:`, error);
  }
  console.log('----------------------------------------------------');
}

/**
 * Registra atualizações de status de entrega de mensagens da Meta.
 */
function handleMessageStatus(status: {
  id: string;
  status: string;
  recipient_id: string;
  errors?: Array<{ code: number; title: string; message?: string }>;
}): void {
  console.log(`📊 [STATUS WHATSAPP] Mensagem ${status.id} -> ${status.status} (Destinatário: ${status.recipient_id})`);
  if (status.errors && status.errors.length > 0) {
    console.error(`⚠️ [ERRO DE ENTREGA META]`, status.errors);
  }
}
