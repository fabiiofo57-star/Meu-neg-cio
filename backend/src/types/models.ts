/**
 * Interfaces dos documentos persistidos no Cloud Firestore.
 * Compatibilidade 1:1 com os dados sincronizados pelo aplicativo Android Meu Negócio.
 */

export interface BusinessDoc {
  id: string;
  userId: string;
  name: string;
  category: string;
  phone: string;
  city: string;
  description?: string | null;
  razaoSocial?: string | null;
  cnpj?: string | null;
  email?: string | null;
  address?: string | null;
  state?: string | null;
  cep?: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface ProductDoc {
  id: string;
  ownerId: string;
  businessId: string;
  nome: string;
  categoria: string;
  codigo?: string;
  precoVenda: number;
  custo: number;
  estoqueAtual: number;
  estoqueMinimo: number;
  foto?: string;
  dataDeCriacao: number;
  dataDeAtualizacao: number;
  updatedAt: number;
}

export interface CustomerDoc {
  id: string;
  ownerId: string;
  businessId: string;
  nome: string;
  telefone: string;
  email?: string;
  cpfCnpj?: string;
  endereco?: string;
  observacao?: string;
  dataDeCadastro: number;
  updatedAt: number;
}

export interface SaleItemDoc {
  id: string;
  productId: string;
  productName: string;
  quantidade: number;
  valorUnitario: number;
  custoUnitario: number;
  subtotal: number;
}

export interface SaleDoc {
  id: string;
  ownerId: string;
  businessId: string;
  clienteId?: string;
  clienteNome?: string;
  data: number; // timestamp em millis
  hora: string; // "HH:mm"
  itens: SaleItemDoc[];
  quantidadeTotal: number;
  desconto: number;
  valorTotal: number;
  formaDePagamento: string; // "Dinheiro", "Pix", "Cartão", "A Prazo", "Outro"
  statusDoPagamento: 'pago' | 'pago_parcial' | 'pendente' | 'atrasado';
  valorPago: number;
  valorRestante: number;
  dataDeVencimento?: number;
  observacao?: string;
  dataDeCriacao: number;
  updatedAt: number;
}

export interface ExpenseDoc {
  id: string;
  ownerId: string;
  businessId: string;
  descricao: string;
  categoria: string;
  valor: number;
  data: number;
  observacao?: string;
  dataDeCriacao: number;
  updatedAt: number;
}

export interface StockMovementDoc {
  id: string;
  ownerId: string;
  businessId: string;
  productId: string;
  productName: string;
  tipo: 'ENTRY' | 'EXIT' | 'ADJUSTMENT';
  quantidade: number;
  estoqueAnterior: number;
  estoqueNovo: number;
  data: number;
  motivo: string;
  dataDeCriacao: number;
  updatedAt: number;
}

export interface PaymentDoc {
  id: string;
  ownerId: string;
  businessId: string;
  saleId: string;
  customerId?: string;
  customerName?: string;
  amount: number;
  paymentMethod: string;
  date: number;
  timeString: string;
  isDemo: boolean;
  createdAt: number;
}

// Estruturas específicas para a integração do WhatsApp

export interface WhatsAppMappingDoc {
  userId: string;
  phoneNumber: string; // Telefone normalizado com código do país (ex: "5511999999999")
  status: 'connected' | 'disconnected';
  connectedAt: number;
  isPro: boolean;
  userName?: string;
  userEmail?: string;
}

export interface WhatsAppUserStatusDoc {
  phoneNumber?: string;
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  pairingCode?: string | null;
  pairingExpiresAt?: number | null;
  connectedAt?: number | null;
  isPro: boolean;
  updatedAt: number;
}

export interface ProcessedMessageDoc {
  messageId: string;
  senderPhone: string;
  userId?: string;
  type: string;
  processedAt: number;
}

export interface WhatsAppSessionDoc {
  userId: string;
  pendingAction: 'CREATE_SALE' | 'CREATE_EXPENSE' | 'NONE';
  payload: any;
  summary: string;
  createdAt: number;
  expiresAt: number; // TTL de 10 minutos
}
