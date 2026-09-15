# Backend Meu Negócio - WhatsApp Business Cloud API & Webhook

Backend dedicado para a integração oficial entre o aplicativo **Meu Negócio** e a **WhatsApp Business Platform / WhatsApp Cloud API**.

---

## 📌 O que foi implementado na Etapa 2

1. **Servidor HTTP Seguro (Express + TypeScript)**
   - Inicialização e tratamento de ciclo de vida com shutdown gracioso.
   - Endpoint de saúde: `GET /health`.
   - Captura de corpo bruto (`rawBody`) para verificação criptográfica sem alterações de formatação.

2. **Validação do Webhook pela Meta: `GET /webhook`**
   - Valida `hub.mode === 'subscribe'`.
   - Compara o `hub.verify_token` configurado.
   - Responde com o `hub.challenge` com status `200 OK` e tipo `text/plain` (exigência estrita da Meta).
   - Rejeita tentativas não autorizadas com `403 Forbidden`.

3. **Recepção e Segurança do Webhook: `POST /webhook`**
   - Middleware de segurança com validação da assinatura **HMAC-SHA256** presente no cabeçalho `X-Hub-Signature-256`.
   - Comparação em tempo constante (`crypto.timingSafeEqual`) contra ataques de timing.
   - Resposta imediata `200 EVENT_RECEIVED` em menos de 3 segundos para evitar reenvio/bloqueio pela Meta.
   - Extração estruturada de remetente (telefone internacional), tipo da mensagem (texto, áudio, interativo, etc.), timestamp e ID da mensagem.
   - Rastreamento e log de status de entrega (`sent`, `delivered`, `read`, `failed`).

4. **Cliente WhatsApp Cloud API (`WhatsAppClient`)**
   - Estrutura pronta para envio de mensagens de texto (`sendTextMessage`).
   - Estrutura pronta para envio de PDFs e comprovantes (`sendDocumentMessage`).
   - Estrutura pronta para botões interativos (`sendInteractiveButtons`).
   - Marcação de leitura (`markAsRead`).
   - Tratamento detalhado de erros com extração do código e `fbtrace_id` da Meta.

---

## 🔑 Credenciais e Variáveis de Ambiente Necessárias

As variáveis de ambiente estão definidas em `backend/.env.example`:

| Variável | O que é | Onde obter no Painel da Meta |
| :--- | :--- | :--- |
| `META_WEBHOOK_VERIFY_TOKEN` | Token de segurança definido por você | Você escolhe (ex: `meu_negocio_verify_token_2026`) e insere o mesmo no painel da Meta |
| `META_APP_SECRET` | Chave secreta do aplicativo Meta | Painel Meta > Configurações do app > Básico > Chave Secreta do Aplicativo |
| `WHATSAPP_PHONE_NUMBER_ID` | ID do número de telefone | Painel Meta > WhatsApp > Configuração da API > ID do número de telefone |
| `WHATSAPP_BUSINESS_ACCOUNT_ID` | ID da conta do WhatsApp Business (WABA) | Painel Meta > WhatsApp > Configuração da API > ID da conta do WhatsApp Business |
| `WHATSAPP_ACCESS_TOKEN` | Token de acesso de longa duração / permanente | Painel Meta > Usuários do Sistema > Gerar Token (com permissão `whatsapp_business_messaging`) |
| `META_API_VERSION` | Versão da Graph API | `v21.0` (padrão) |

---

## 🛠️ Como testar localmente

```bash
# Entrar na pasta do backend
cd backend

# Instalar dependências
npm install

# Compilar TypeScript
npm run build

# Executar a suíte de testes automatizados do Webhook
node dist/testWebhook.js
```
