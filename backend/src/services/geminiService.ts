import { GoogleGenAI } from '@google/genai';
import { config } from '../config/env';

export interface StructuredIntent {
  intent:
    | 'CREATE_SALE'
    | 'CREATE_EXPENSE'
    | 'CREATE_CUSTOMER'
    | 'CREATE_PRODUCT'
    | 'UPDATE_STOCK'
    | 'QUERY_FINANCIALS'
    | 'QUERY_DEBT'
    | 'QUERY_TOP_SALES'
    | 'QUERY_STOCK'
    | 'QUERY_PRODUCTS'
    | 'QUERY_CUSTOMERS'
    | 'GENERATE_RECEIPT'
    | 'GENERATE_REPORT'
    | 'CONFIRM_ACTION'
    | 'CANCEL_ACTION'
    | 'PAIRING_CODE'
    | 'GREETING'
    | 'HELP'
    | 'UNKNOWN';
  payload?: any;
  needsConfirmation?: boolean;
  confirmationSummary?: string;
  confidence?: number;
}

export class GeminiService {
  private ai: GoogleGenAI | null = null;

  constructor() {
    if (config.gemini.apiKey) {
      try {
        this.ai = new GoogleGenAI({ apiKey: config.gemini.apiKey });
        console.log('🤖 [GEMINI SERVICE] Inicializado com modelo:', config.gemini.model);
      } catch (err) {
        console.warn('⚠️ [GEMINI SERVICE] Falha ao inicializar GoogleGenAI:', (err as Error).message);
      }
    }
  }

  /**
   * Converte uma mensagem em linguagem natural (texto) em uma intenção estruturada.
   */
  async interpretMessage(text: string): Promise<StructuredIntent> {
    const cleanText = text.trim();

    // 1. Verificação rápida de código de pareamento (ex: "MN-1234" ou "8492")
    const codeMatch = cleanText.match(/\b(MN-)?\d{4}\b/i);
    if (codeMatch && cleanText.length <= 10) {
      return {
        intent: 'PAIRING_CODE',
        payload: { code: codeMatch[0].toUpperCase() },
      };
    }

    // 2. Verificação de confirmação/cancelamento direto
    const lower = cleanText.toLowerCase();
    if (['sim', 'pode', 'ok', 'confirmado', 'manda ver', 'isso', 'pode registrar', 'bora'].includes(lower)) {
      return { intent: 'CONFIRM_ACTION' };
    }
    if (['não', 'nao', 'cancela', 'cancelar', 'não registra', 'deixa pra lá', 'esquece'].includes(lower)) {
      return { intent: 'CANCEL_ACTION' };
    }

    // 3. Se a API Gemini estiver configurada, utiliza o modelo
    if (this.ai && config.gemini.apiKey) {
      try {
        const result = await this.callGeminiIntentExtractor(cleanText);
        if (result && result.intent !== 'UNKNOWN') {
          return result;
        }
      } catch (error) {
        console.warn('⚠️ [GEMINI] Erro na chamada à API, utilizando parser local resiliente:', (error as Error).message);
      }
    }

    // 4. Parser Local Resiliente (garante 100% de funcionamento mesmo sem chave Gemini configurada)
    return this.fallbackLocalParser(cleanText);
  }

  /**
   * Transcreve e interpreta áudio do WhatsApp através do Gemini Multimodal.
   */
  async interpretAudio(audioBuffer: Buffer, mimeType = 'audio/ogg'): Promise<StructuredIntent> {
    if (!this.ai || !config.gemini.apiKey) {
      return {
        intent: 'UNKNOWN',
        payload: {
          error: 'Para envio de áudio, configure a chave GEMINI_API_KEY no backend para transcrição e interpretação pela IA.',
        },
      };
    }

    try {
      const prompt = `Você é o assistente inteligente do sistema "Meu Negócio".
Ouça o áudio do usuário e extraia a intenção estruturada e os dados em formato JSON estrito conforme o schema.
Responda APENAS com JSON sem markdown:
{
  "transcription": "texto transcrito do áudio",
  "intent": "CREATE_SALE" | "CREATE_EXPENSE" | "CREATE_CUSTOMER" | "CREATE_PRODUCT" | "UPDATE_STOCK" | "QUERY_FINANCIALS" | "QUERY_PRODUCTS" | "QUERY_CUSTOMERS" | "GENERATE_RECEIPT" | "GENERATE_REPORT" | "HELP" | "UNKNOWN",
  "payload": { ... },
  "needsConfirmation": true ou false,
  "confirmationSummary": "texto amigável para confirmação se necessário"
}`;

      const base64Audio = audioBuffer.toString('base64');
      const response = await this.ai.models.generateContent({
        model: config.gemini.model,
        contents: [
          {
            role: 'user',
            parts: [
              { text: prompt },
              {
                inlineData: {
                  mimeType,
                  data: base64Audio,
                },
              },
            ],
          },
        ],
      });

      const responseText = response.text || '';
      const cleanJson = responseText.replace(/```json/g, '').replace(/```/g, '').trim();
      const parsed = JSON.parse(cleanJson);

      return {
        intent: parsed.intent || 'UNKNOWN',
        payload: parsed.payload || {},
        needsConfirmation: parsed.needsConfirmation || false,
        confirmationSummary: parsed.confirmationSummary,
      };
    } catch (error) {
      console.error('Erro ao interpretar áudio com Gemini:', error);
      return {
        intent: 'UNKNOWN',
        payload: { error: 'Não foi possível processar o áudio com clareza. Tente falar novamente ou envie por texto.' },
      };
    }
  }

  /**
   * Chamada ao Gemini para interpretação de texto com saída JSON.
   */
  private async callGeminiIntentExtractor(text: string): Promise<StructuredIntent | null> {
    if (!this.ai) return null;

    const systemInstruction = `Você é o motor de IA do aplicativo "Meu Negócio".
Sua função é interpretar a mensagem do empresário no WhatsApp e converter em uma intenção estrita e dados estruturados em JSON.

Intenções possíveis:
- CREATE_SALE: venda. O payload DEVE conter:
    customerName: string (ex: "Carlos" ou "Cliente Balcão")
    items: array de objetos { productName: string, quantity: number, unitPrice: number }
    totalAmount: number
    paymentMethod: "Pix" | "Dinheiro" | "Cartão" | "A Prazo"
    isPaid: boolean
- CREATE_EXPENSE: despesa. O payload DEVE conter:
    descricao: string
    valor: number
    categoria: string (ex: "Combustível", "Aluguel", "Compra", "Manutenção", "Outros")
- CREATE_CUSTOMER: cadastro de cliente (nome, telefone).
- CREATE_PRODUCT: cadastro de produto (nome, precoVenda, estoqueInicial, categoria).
- UPDATE_STOCK: alteração de estoque (productName, deltaQuantity, reason).
- QUERY_FINANCIALS: perguntas como quanto vendi hoje, quanto vendi este mês, quanto tenho a receber, quanto gastei, ticket médio, lucro estimado. O payload deve conter:
    metric: "sales" | "expenses" | "receivables" | "profit" | "ticket_medio" | "top_products"
    period: "today" | "yesterday" | "week" | "month"
- QUERY_DEBT: quanto determinado cliente está devendo. O payload DEVE conter:
    customerName: string (ex: "João")
- QUERY_TOP_SALES: quais foram minhas maiores vendas ou melhores pedidos.
- QUERY_STOCK: quantas unidades de determinado produto há no estoque. O payload DEVE conter:
    productName: string (ex: "camisas" ou "camisa azul")
- QUERY_PRODUCTS: listar produtos, preço de produto, estoque de produto, produtos acabando.
- QUERY_CUSTOMERS: listar clientes, telefone de cliente.
- GENERATE_RECEIPT: comprovante de venda (id da venda, "última", ou nome do cliente).
- GENERATE_REPORT: relatório (tipo: financeiro, vendas, despesas, estoque; período: hoje, semana, mês).
- GREETING: saudações como oi, olá, bom dia.
- HELP: pedido de ajuda ou comandos disponíveis.

Responda APENAS em JSON no formato:
{
  "intent": "NOME_DA_INTENÇÃO",
  "payload": { ... },
  "needsConfirmation": true/false (true para vendas e despesas com valores acima de 0),
  "confirmationSummary": "resumo claro e curto em português com os dados para confirmação"
}`;

    const response = await this.ai.models.generateContent({
      model: config.gemini.model,
      contents: [
        {
          role: 'user',
          parts: [{ text: `${systemInstruction}\n\nMensagem do usuário: "${text}"` }],
        },
      ],
    });

    const respText = response.text || '';
    const cleanJson = respText.replace(/```json/g, '').replace(/```/g, '').trim();
    const parsed = JSON.parse(cleanJson) as StructuredIntent;

    // Normalização de chaves para resiliência entre idiomas e variações de saída do modelo
    if (parsed.payload) {
      if (parsed.payload.cliente && !parsed.payload.customerName) {
        parsed.payload.customerName = parsed.payload.cliente;
      }
      if (parsed.payload.clienteNome && !parsed.payload.customerName) {
        parsed.payload.customerName = parsed.payload.clienteNome;
      }
      if (parsed.payload.formaPagamento && !parsed.payload.paymentMethod) {
        parsed.payload.paymentMethod = parsed.payload.formaPagamento;
      }
      if (parsed.payload.formaDePagamento && !parsed.payload.paymentMethod) {
        parsed.payload.paymentMethod = parsed.payload.formaDePagamento;
      }
    }

    return parsed;
  }

  /**
   * Parser local robusto por regras e regex para funcionamento instantâneo e offline.
   */
  private fallbackLocalParser(text: string): StructuredIntent {
    const lower = text.toLowerCase();

    // 1. Saudações e Ajuda
    if (/^(oi|olá|ola|bom dia|boa tarde|boa noite|e ai|e aí|hey)\b/i.test(lower)) {
      return { intent: 'GREETING' };
    }
    if (lower.includes('ajuda') || lower.includes('como funciona') || lower.includes('comandos')) {
      return { intent: 'HELP' };
    }

    // 2. Comprovante de Venda
    if (lower.includes('comprovante') || lower.includes('recibo')) {
      let saleId = 'last';
      const idMatch = text.match(/venda\s+([a-zA-Z0-9_-]+)/i);
      if (idMatch) saleId = idMatch[1];
      return {
        intent: 'GENERATE_RECEIPT',
        payload: { saleId, query: text },
      };
    }

    // 3. Relatórios
    if (lower.includes('relatório') || lower.includes('relatorio')) {
      let type = 'financial';
      if (lower.includes('venda')) type = 'sales';
      if (lower.includes('despesa') || lower.includes('gasto')) type = 'expenses';
      if (lower.includes('estoque')) type = 'stock';

      let period = 'month';
      if (lower.includes('hoje') || lower.includes('dia')) period = 'today';
      if (lower.includes('semana')) period = 'week';

      return {
        intent: 'GENERATE_REPORT',
        payload: { type, period },
      };
    }

    // 4. Consultas Financeiras e Dívidas de Clientes
    if (lower.includes('devendo') || lower.includes('quanto deve') || lower.includes('dívida') || lower.includes('divida')) {
      const nameMatch = text.match(/(?:quanto\s+(?:o|a)?\s*|d[íi]vida\s+d[oe]\s+)([a-zA-ZÀ-ÿ]+)/i);
      return {
        intent: 'QUERY_DEBT',
        payload: { customerName: nameMatch ? nameMatch[1].trim() : 'Cliente' },
      };
    }

    if (lower.includes('maiores vendas') || lower.includes('maior venda') || lower.includes('melhores vendas')) {
      return { intent: 'QUERY_TOP_SALES' };
    }

    if ((lower.includes('quantas') || lower.includes('quantos') || lower.includes('estoque de')) && (lower.includes('estoque') || lower.includes('tenho'))) {
      const prodMatch = text.match(/(?:quant[ao]s?\s+)([a-zA-ZÀ-ÿ\s]+?)(?:\s+tenho|\s+no estoque|\s+em estoque|\s*$)/i) ||
                        text.match(/(?:estoque\s+d[oe]\s+)([a-zA-ZÀ-ÿ\s]+)/i);
      const productName = prodMatch ? prodMatch[1].trim() : text;
      return { intent: 'QUERY_STOCK', payload: { productName } };
    }

    if (
      lower.includes('quanto vendi') ||
      lower.includes('quanto gastei') ||
      lower.includes('quanto tenho') ||
      lower.includes('receber') ||
      lower.includes('lucro') ||
      lower.includes('ticket médio') ||
      lower.includes('ticket medio') ||
      lower.includes('mais vendeu')
    ) {
      let metric = 'sales';
      if (lower.includes('gastei') || lower.includes('despesa')) metric = 'expenses';
      if (lower.includes('receber')) metric = 'receivables';
      if (lower.includes('lucro')) metric = 'profit';
      if (lower.includes('ticket')) metric = 'ticket_medio';
      if (lower.includes('mais vendeu')) metric = 'top_products';

      let period = 'month';
      if (lower.includes('hoje')) period = 'today';
      if (lower.includes('ontem')) period = 'yesterday';
      if (lower.includes('semana')) period = 'week';

      return {
        intent: 'QUERY_FINANCIALS',
        payload: { metric, period },
      };
    }

    // 5. Consultas de Produtos / Estoque
    if (lower.includes('acabando') || lower.includes('estoque baixo')) {
      return { intent: 'QUERY_PRODUCTS', payload: { lowStockOnly: true } };
    }
    if (lower.includes('lista meus produtos') || lower.includes('meus produtos') || lower.includes('quais produtos')) {
      return { intent: 'QUERY_PRODUCTS', payload: { listAll: true } };
    }

    // 6. Consultas de Clientes
    if (lower.includes('meus clientes') || lower.includes('lista de clientes') || lower.includes('telefone do')) {
      const nameMatch = text.match(/(?:telefone|contato)\s+d[oe]\s+([a-zA-ZÀ-ÿ\s]+)/i);
      return {
        intent: 'QUERY_CUSTOMERS',
        payload: { customerName: nameMatch ? nameMatch[1].trim() : undefined },
      };
    }

    // 7. Cadastro de Despesa
    if (lower.includes('despesa') || lower.includes('gastei') || lower.includes('paguei')) {
      const valMatch = text.match(/(?:r\$\s*|\b)(\d+(?:[.,]\d{1,2})?)\s*(?:reais)?/i);
      const valor = valMatch ? parseFloat(valMatch[1].replace(',', '.')) : 0;

      let categoria = 'Outros';
      if (lower.includes('combustivel') || lower.includes('combustível') || lower.includes('gasolina')) categoria = 'Transporte';
      if (lower.includes('aluguel')) categoria = 'Aluguel';
      if (lower.includes('luz') || lower.includes('energia')) categoria = 'Energia';
      if (lower.includes('internet')) categoria = 'Internet';
      if (lower.includes('compra') || lower.includes('mercadoria')) categoria = 'Compra';

      return {
        intent: 'CREATE_EXPENSE',
        payload: {
          descricao: text.replace(/(?:registra|anota|adiciona)\s+(?:uma\s+)?despesa\s+(?:de\s+)?/i, '').trim(),
          valor,
          categoria,
        },
        needsConfirmation: valor > 0,
        confirmationSummary: `Despesa: ${categoria} no valor de R$ ${valor.toFixed(2)}`,
      };
    }

    // 8. Cadastro de Cliente
    if (lower.includes('cadastr') && lower.includes('cliente')) {
      const nameMatch = text.match(/cadastr[ea]\s+([a-zA-ZÀ-ÿ\s]+?)(?:\s+como\s+cliente|\s+cliente|,|\s*$)/i);
      const phoneMatch = text.match(/(?:telefone|tel|celular|fone|whatsapp)\s*([0-9\s()-]+)/i);
      return {
        intent: 'CREATE_CUSTOMER',
        payload: {
          nome: nameMatch ? nameMatch[1].replace(/como\s+cliente/i, '').trim() : 'Cliente',
          telefone: phoneMatch ? phoneMatch[1].replace(/\D/g, '') : '',
        },
      };
    }

    // 9. Cadastro de Produto
    if (lower.includes('cadastr') && (lower.includes('produto') || lower.includes('por') || lower.includes('reais'))) {
      const priceMatch = text.match(/(?:por|valor|preço|preco)\s*(?:r\$\s*)?(\d+(?:[.,]\d{1,2})?)/i);
      const preco = priceMatch ? parseFloat(priceMatch[1].replace(',', '.')) : 0;
      const name = text
        .replace(/cadastr[ea]\s+(?:o\s+produto\s+)?/i, '')
        .replace(/(?:por|valor|preço|preco)\s*(?:r\$\s*)?(\d+(?:[.,]\d{1,2})?)\s*(?:reais)?/i, '')
        .trim();

      return {
        intent: 'CREATE_PRODUCT',
        payload: {
          nome: name || 'Novo Produto',
          precoVenda: preco,
        },
      };
    }

    // 10. Registro de Venda (padrão principal)
    if (
      lower.includes('venda') ||
      lower.includes('vendi') ||
      lower.includes('comprou') ||
      lower.includes('anota aí') ||
      lower.includes('anota ai')
    ) {
      return this.parseSaleText(text);
    }

    return { intent: 'UNKNOWN', payload: { rawText: text } };
  }

  private parseSaleText(text: string): StructuredIntent {
    let customerName = 'Cliente Balcão';
    const custMatch = text.match(/(?:pra|pro|para|cliente)\s+([a-zA-ZÀ-ÿ]+)/i);
    if (custMatch) customerName = custMatch[1].trim();

    // Forma de Pagamento
    let paymentMethod = 'Dinheiro';
    const lower = text.toLowerCase();
    if (lower.includes('pix')) paymentMethod = 'Pix';
    if (lower.includes('cartão') || lower.includes('cartao')) paymentMethod = 'Cartão';
    if (lower.includes('prazo') || lower.includes('fiado') || lower.includes('sexta') || lower.includes('segunda')) {
      paymentMethod = 'A Prazo';
    }

    // Quantidade
    let quantity = 1;
    const qtyMatch = text.match(/(\d+|uma|duas|três|tres|quatro|cinco|dez)\s+([a-zA-ZÀ-ÿ\s]+?)(?:\s+de\s+|\s+a\s+|\s+por\s+|,|$)/i);
    if (qtyMatch) {
      const qWord = qtyMatch[1].toLowerCase();
      const map: Record<string, number> = { uma: 1, um: 1, duas: 2, dois: 2, três: 3, tres: 3, quatro: 4, cinco: 5, dez: 10 };
      quantity = map[qWord] || parseInt(qWord, 10) || 1;
    }

    // Preço
    let unitPrice = 0;
    const priceMatch = text.match(/(?:de|a|por|r\$)\s*(\d+(?:[.,]\d{1,2})?)\s*(?:reais)?/i);
    if (priceMatch) {
      unitPrice = parseFloat(priceMatch[1].replace(',', '.'));
    }

    // Nome do produto
    let productName = 'Produto';
    if (qtyMatch && qtyMatch[2]) {
      productName = qtyMatch[2].trim();
    }

    const total = quantity * unitPrice;

    return {
      intent: 'CREATE_SALE',
      payload: {
        customerName,
        items: [{ productName, quantity, unitPrice }],
        totalAmount: total,
        paymentMethod,
        isPaid: paymentMethod !== 'A Prazo',
      },
      needsConfirmation: true,
      confirmationSummary: `Cliente: ${customerName}\nItem: ${quantity}x ${productName} (R$ ${unitPrice.toFixed(2)})\nTotal: R$ ${total.toFixed(2)} | Forma: ${paymentMethod}`,
    };
  }
}

export const geminiService = new GeminiService();
