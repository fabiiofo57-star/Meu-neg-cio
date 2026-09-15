/**
 * Tipos oficiais da API do WhatsApp Cloud (Meta Graph API)
 * Especificação: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks/payload-examples
 */

export interface WhatsAppWebhookPayload {
  object: string; // Esperado: "whatsapp_business_account"
  entry?: WhatsAppEntry[];
}

export interface WhatsAppEntry {
  id: string; // WhatsApp Business Account ID (WABA ID)
  changes: WhatsAppChange[];
}

export interface WhatsAppChange {
  value: WhatsAppValue;
  field: string; // Ex: "messages"
}

export interface WhatsAppMetadata {
  display_phone_number: string;
  phone_number_id: string;
}

export interface WhatsAppContact {
  profile: {
    name?: string;
  };
  wa_id: string; // Número do remetente no formato internacional (sem '+', ex: "5511999999999")
}

export type WhatsAppMessageType =
  | 'text'
  | 'audio'
  | 'image'
  | 'document'
  | 'interactive'
  | 'button'
  | 'location'
  | 'unsupported';

export interface WhatsAppTextMessage {
  body: string;
}

export interface WhatsAppMediaMessage {
  id: string;
  mime_type: string;
  sha256?: string;
  caption?: string;
  filename?: string;
}

export interface WhatsAppInteractiveResponse {
  type: 'button_reply' | 'list_reply';
  button_reply?: {
    id: string;
    title: string;
  };
  list_reply?: {
    id: string;
    title: string;
    description?: string;
  };
}

export interface WhatsAppMessage {
  from: string; // Número do usuário (ex: "5511999999999")
  id: string; // ID único da mensagem (ex: "wamid.HBgL...")
  timestamp: string; // Unix timestamp
  type: WhatsAppMessageType;
  text?: WhatsAppTextMessage;
  audio?: WhatsAppMediaMessage;
  image?: WhatsAppMediaMessage;
  document?: WhatsAppMediaMessage;
  interactive?: WhatsAppInteractiveResponse;
}

export interface WhatsAppStatus {
  id: string;
  status: 'sent' | 'delivered' | 'read' | 'failed';
  timestamp: string;
  recipient_id: string;
  pricing?: {
    billable: boolean;
    pricing_model: string;
    category: string;
  };
  conversation?: {
    id: string;
    expiration_timestamp?: string;
    origin?: {
      type: string;
    };
  };
  errors?: Array<{
    code: number;
    title: string;
    message?: string;
    error_data?: {
      details?: string;
    };
  }>;
}

export interface WhatsAppValue {
  messaging_product: 'whatsapp';
  metadata: WhatsAppMetadata;
  contacts?: WhatsAppContact[];
  messages?: WhatsAppMessage[];
  statuses?: WhatsAppStatus[];
  errors?: Array<{
    code: number;
    title: string;
    message?: string;
  }>;
}

// ============================================================================
// Tipos para Envio de Mensagens (WhatsApp Cloud API Outgoing Messages)
// ============================================================================

export interface WhatsAppSendTextRequest {
  messaging_product: 'whatsapp';
  recipient_type?: 'individual';
  to: string; // Número com código do país (ex: "5511999999999")
  type: 'text';
  text: {
    preview_url?: boolean;
    body: string;
  };
}

export interface WhatsAppSendDocumentRequest {
  messaging_product: 'whatsapp';
  recipient_type?: 'individual';
  to: string;
  type: 'document';
  document: {
    id?: string;
    link?: string;
    caption?: string;
    filename?: string;
  };
}

export interface WhatsAppSendInteractiveButtonRequest {
  messaging_product: 'whatsapp';
  recipient_type?: 'individual';
  to: string;
  type: 'interactive';
  interactive: {
    type: 'button';
    body: {
      text: string;
    };
    action: {
      buttons: Array<{
        type: 'reply';
        reply: {
          id: string;
          title: string;
        };
      }>;
    };
  };
}

export interface WhatsAppSendResponse {
  messaging_product: string;
  contacts: Array<{
    input: string;
    wa_id: string;
  }>;
  messages: Array<{
    id: string;
  }>;
}
