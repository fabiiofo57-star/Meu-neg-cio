import axios, { AxiosError } from 'axios';
import { config } from '../config/env';
import {
  WhatsAppSendTextRequest,
  WhatsAppSendDocumentRequest,
  WhatsAppSendInteractiveButtonRequest,
  WhatsAppSendResponse,
} from '../types/whatsapp';

/**
 * Cliente oficial para envio de mensagens via WhatsApp Cloud API (Meta Graph API).
 *
 * Todas as requisições utilizam o endpoint oficial:
 * POST https://graph.facebook.com/{apiVersion}/{phoneNumberId}/messages
 * com cabeçalho de autorização: Authorization: Bearer {accessToken}
 */
export class WhatsAppClient {
  private get baseUrl(): string {
    return `https://graph.facebook.com/${config.meta.apiVersion}/${config.meta.phoneNumberId}/messages`;
  }

  private get mediaUploadUrl(): string {
    return `https://graph.facebook.com/${config.meta.apiVersion}/${config.meta.phoneNumberId}/media`;
  }

  private get headers(): Record<string, string> {
    return {
      Authorization: `Bearer ${config.meta.accessToken}`,
      'Content-Type': 'application/json',
    };
  }

  /**
   * Envia uma mensagem de texto simples para um número de WhatsApp.
   *
   * @param to Número de telefone do destinatário no formato internacional (ex: "5511999999999")
   * @param text Texto da mensagem
   */
  async sendTextMessage(to: string, text: string): Promise<WhatsAppSendResponse | null> {
    const cleanTo = this.normalizePhoneNumber(to);

    if (!config.meta.accessToken || !config.meta.phoneNumberId) {
      console.warn(
        `[WHATSAPP CLIENT] Não foi possível enviar mensagem para ${cleanTo}: META_ACCESS_TOKEN ou META_PHONE_NUMBER_ID não configurados.`
      );
      console.log(`[WHATSAPP MENSAGEM LOCAL/DEV] Para: ${cleanTo}\nConteúdo: ${text}`);
      return null;
    }

    const payload: WhatsAppSendTextRequest = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: cleanTo,
      type: 'text',
      text: {
        preview_url: false,
        body: text,
      },
    };

    try {
      console.log(`[WHATSAPP CLIENT] Enviando texto real para ${cleanTo}...`);
      const response = await axios.post<WhatsAppSendResponse>(this.baseUrl, payload, {
        headers: this.headers,
        timeout: 15000,
      });

      console.log(`[WHATSAPP CLIENT] Mensagem entregue à Meta para ${cleanTo}. ID:`, response.data.messages?.[0]?.id);
      return response.data;
    } catch (error) {
      this.handleApiError('sendTextMessage', error, cleanTo);
      throw error;
    }
  }

  /**
   * Faz upload de mídia binária (PDF de comprovante ou relatório) diretamente para a Meta Cloud API.
   * Retorna o media_id oficial para envio instantâneo sem necessidade de hospedagem externa de URLs.
   */
  async uploadMedia(fileBuffer: Buffer, mimeType: string, filename: string): Promise<string | null> {
    if (!config.meta.accessToken || !config.meta.phoneNumberId) {
      console.warn('[WHATSAPP CLIENT] Upload de mídia ignorado (credenciais ausentes).');
      return null;
    }

    try {
      console.log(`[WHATSAPP CLIENT] Fazendo upload de mídia (${filename}, ${fileBuffer.length} bytes) para Meta...`);
      const formData = new FormData();
      formData.append('messaging_product', 'whatsapp');
      formData.append('type', mimeType);
      formData.append('file', new Blob([fileBuffer], { type: mimeType }), filename);

      const response = await axios.post<{ id: string }>(this.mediaUploadUrl, formData, {
        headers: {
          Authorization: `Bearer ${config.meta.accessToken}`,
        },
        timeout: 30000,
      });

      const mediaId = response.data?.id;
      console.log(`[WHATSAPP CLIENT] Upload concluído na Meta! media_id:`, mediaId);
      return mediaId || null;
    } catch (error) {
      this.handleApiError('uploadMedia', error, 'META_MEDIA_API');
      return null;
    }
  }

  /**
   * Envia um documento em PDF (Comprovante de Venda ou Relatório Financeiro).
   * Suporta envio por URL pública ou por upload direto de buffer de arquivo (media_id).
   */
  async sendDocument(
    to: string,
    options: {
      documentUrl?: string;
      fileBuffer?: Buffer;
      filename: string;
      caption?: string;
    }
  ): Promise<WhatsAppSendResponse | null> {
    const cleanTo = this.normalizePhoneNumber(to);
    const { documentUrl, fileBuffer, filename, caption } = options;

    if (!config.meta.accessToken || !config.meta.phoneNumberId) {
      console.warn(`[WHATSAPP CLIENT] Envio de documento ignorado para ${cleanTo} (credenciais ausentes).`);
      return null;
    }

    // Se tiver buffer, tenta upload direto para a Meta primeiro (garante que não depende de URLs externas)
    let mediaId: string | null = null;
    if (fileBuffer) {
      mediaId = await this.uploadMedia(fileBuffer, 'application/pdf', filename);
    }

    const payload: WhatsAppSendDocumentRequest = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: cleanTo,
      type: 'document',
      document: mediaId
        ? { id: mediaId, filename, caption: caption || undefined }
        : { link: documentUrl || '', filename, caption: caption || undefined },
    };

    try {
      console.log(`[WHATSAPP CLIENT] Enviando documento oficial (${filename}) para ${cleanTo}...`);
      const response = await axios.post<WhatsAppSendResponse>(this.baseUrl, payload, {
        headers: this.headers,
        timeout: 20000,
      });

      console.log(`[WHATSAPP CLIENT] Documento enviado para ${cleanTo}. ID:`, response.data.messages?.[0]?.id);
      return response.data;
    } catch (error) {
      this.handleApiError('sendDocument', error, cleanTo);
      throw error;
    }
  }

  /**
   * Compatibilidade com chamadas existentes de sendDocumentMessage por URL.
   */
  async sendDocumentMessage(
    to: string,
    documentUrl: string,
    filename: string,
    caption?: string
  ): Promise<WhatsAppSendResponse | null> {
    return this.sendDocument(to, { documentUrl, filename, caption });
  }

  /**
   * Envia uma mensagem baseada em Modelo Oficial (Template Message) aprovado pela Meta.
   * Utilizado quando a janela de 24 horas da sessão estiver expirada.
   */
  async sendTemplateMessage(
    to: string,
    templateName: string,
    languageCode = 'pt_BR',
    components?: any[]
  ): Promise<WhatsAppSendResponse | null> {
    const cleanTo = this.normalizePhoneNumber(to);

    if (!config.meta.accessToken || !config.meta.phoneNumberId) {
      console.warn(`[WHATSAPP CLIENT] Envio de template ignorado (credenciais ausentes).`);
      return null;
    }

    const payload = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: cleanTo,
      type: 'template',
      template: {
        name: templateName,
        language: { code: languageCode },
        components: components || [],
      },
    };

    try {
      console.log(`[WHATSAPP CLIENT] Enviando template (${templateName}) para ${cleanTo}...`);
      const response = await axios.post<WhatsAppSendResponse>(this.baseUrl, payload, {
        headers: this.headers,
        timeout: 15000,
      });
      return response.data;
    } catch (error) {
      this.handleApiError('sendTemplateMessage', error, cleanTo);
      throw error;
    }
  }

  /**
   * Envia botões interativos de resposta rápida (ex: "Sim, confirmar" / "Não, cancelar").
   */
  async sendInteractiveButtons(
    to: string,
    text: string,
    buttons: Array<{ id: string; title: string }>
  ): Promise<WhatsAppSendResponse | null> {
    const cleanTo = this.normalizePhoneNumber(to);

    if (!config.meta.accessToken || !config.meta.phoneNumberId) {
      console.warn(`[WHATSAPP CLIENT] Envio de botões ignorado (credenciais ausentes). Destinatário: ${cleanTo}`);
      return null;
    }

    const payload: WhatsAppSendInteractiveButtonRequest = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: cleanTo,
      type: 'interactive',
      interactive: {
        type: 'button',
        body: { text },
        action: {
          buttons: buttons.slice(0, 3).map((btn) => ({
            type: 'reply',
            reply: {
              id: btn.id,
              title: btn.title.slice(0, 20),
            },
          })),
        },
      },
    };

    try {
      console.log(`[WHATSAPP CLIENT] Enviando botões interativos para ${cleanTo}...`);
      const response = await axios.post<WhatsAppSendResponse>(this.baseUrl, payload, {
        headers: this.headers,
        timeout: 15000,
      });

      return response.data;
    } catch (error) {
      this.handleApiError('sendInteractiveButtons', error, cleanTo);
      throw error;
    }
  }

  /**
   * Marca uma mensagem recebida como lida (status 'read' com tiques azuis).
   */
  async markAsRead(messageId: string): Promise<boolean> {
    if (!config.meta.accessToken || !config.meta.phoneNumberId) return false;

    try {
      await axios.post(
        this.baseUrl,
        {
          messaging_product: 'whatsapp',
          status: 'read',
          message_id: messageId,
        },
        { headers: this.headers, timeout: 5000 }
      );
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Normaliza o telefone para o formato internacional com apenas dígitos.
   */
  private normalizePhoneNumber(phone: string): string {
    return phone.replace(/\D/g, '');
  }

  /**
   * Tratamento de erro detalhado com extração de diagnósticos da Meta Graph API.
   */
  private handleApiError(action: string, error: unknown, recipient: string): void {
    if (error && (error as AxiosError).isAxiosError) {
      const axiosErr = error as AxiosError<{
        error?: {
          message: string;
          type: string;
          code: number;
          error_subcode?: number;
          fbtrace_id?: string;
        };
      }>;

      const metaError = axiosErr.response?.data?.error;
      console.error(`❌ [ERRO WHATSAPP CLOUD API] ${action} para ${recipient}:`);
      console.error(`Status HTTP: ${axiosErr.response?.status}`);
      if (metaError) {
        console.error(`Código Meta: ${metaError.code} (Subcode: ${metaError.error_subcode || 'N/A'})`);
        console.error(`Mensagem: ${metaError.message}`);
        console.error(`Trace ID: ${metaError.fbtrace_id}`);
      } else {
        console.error(`Resposta:`, axiosErr.response?.data);
      }
    } else {
      console.error(`❌ [ERRO INESPERADO] ${action} para ${recipient}:`, error);
    }
  }
}

export const whatsappClient = new WhatsAppClient();
