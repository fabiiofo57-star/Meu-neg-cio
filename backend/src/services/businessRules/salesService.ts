import { randomUUID } from 'crypto';
import { db } from '../firebase';
import { SaleDoc, SaleItemDoc, PaymentDoc } from '../../types/models';
import { productService } from './productService';
import { customerService } from './customerService';

export interface CreateSaleInput {
  customerName?: string;
  items: Array<{
    productName: string;
    quantity: number;
    unitPrice?: number;
  }>;
  totalAmount?: number;
  paymentMethod?: string; // "Pix", "Dinheiro", "Cartão", "A Prazo"
  dueDate?: number | null;
  observation?: string;
  isPaid?: boolean;
}

export class SalesService {
  /**
   * Cria uma venda no Firestore sob users/{userId}/sales/{saleId},
   * atualizando estoque e criando movimentações correspondentes.
   */
  async createSale(
    userId: string,
    businessId: string,
    input: CreateSaleInput
  ): Promise<{ sale: SaleDoc; summary: string }> {
    const saleId = randomUUID();
    const now = Date.now();
    const dateObj = new Date(now);
    const hora = `${String(dateObj.getHours()).padStart(2, '0')}:${String(dateObj.getMinutes()).padStart(2, '0')}`;

    // 1. Resolve ou cadastra o cliente
    let customerId = '';
    let customerName = input.customerName?.trim() || 'Cliente Balcão';

    if (customerName && customerName.toLowerCase() !== 'cliente balcão') {
      const existingCustomer = await customerService.findCustomerByName(userId, customerName);
      if (existingCustomer) {
        customerId = existingCustomer.id;
        customerName = existingCustomer.nome;
      } else {
        // Cria o cliente automaticamente para agilizar o fluxo
        const newCustomer = await customerService.createCustomer(userId, businessId, {
          nome: customerName,
          observacao: 'Cadastrado automaticamente na venda via WhatsApp',
        });
        customerId = newCustomer.id;
        customerName = newCustomer.nome;
      }
    }

    // 2. Processa os itens e atualiza o estoque
    const saleItems: SaleItemDoc[] = [];
    let calculatedTotal = 0;

    for (const itemInput of input.items) {
      const existingProduct = await productService.findProductByName(userId, itemInput.productName);
      const qty = Math.max(1, itemInput.quantity || 1);
      let unitPrice = itemInput.unitPrice || 0;
      let unitCost = 0;
      let productId = '';
      let productName = itemInput.productName.trim();

      if (existingProduct) {
        productId = existingProduct.id;
        productName = existingProduct.nome;
        unitCost = existingProduct.custo || 0;
        if (!unitPrice || unitPrice <= 0) {
          unitPrice = existingProduct.precoVenda;
        }

        // Dá baixa no estoque
        await productService.updateStock(
          userId,
          businessId,
          existingProduct.id,
          -qty,
          `Saída por venda via WhatsApp #${saleId.slice(0, 6)}`
        );
      } else {
        // Cria um ID temporário se for produto avulso
        productId = randomUUID();
        if (!unitPrice || unitPrice <= 0) {
          unitPrice = input.totalAmount && input.items.length === 1 ? input.totalAmount : 10.0;
        }
      }

      const subtotal = qty * unitPrice;
      calculatedTotal += subtotal;

      saleItems.push({
        id: randomUUID(),
        productId,
        productName,
        quantidade: qty,
        valorUnitario: unitPrice,
        custoUnitario: unitCost,
        subtotal,
      });
    }

    const finalTotal = input.totalAmount && input.totalAmount > 0 ? input.totalAmount : calculatedTotal;
    const paymentMethod = this.normalizePaymentMethod(input.paymentMethod);
    const isPrazo = paymentMethod.toLowerCase() === 'a prazo' || input.isPaid === false;
    const statusDoPagamento = isPrazo ? 'pendente' : 'pago';
    const paidAmount = isPrazo ? 0.0 : finalTotal;
    const remainingAmount = isPrazo ? finalTotal : 0.0;

    const sale: SaleDoc = {
      id: saleId,
      ownerId: userId,
      businessId,
      clienteId: customerId,
      clienteNome: customerName,
      data: now,
      hora,
      itens: saleItems,
      quantidadeTotal: saleItems.reduce((acc, item) => acc + item.quantidade, 0),
      desconto: 0,
      valorTotal: finalTotal,
      formaDePagamento: paymentMethod,
      statusDoPagamento,
      valorPago: paidAmount,
      valorRestante: remainingAmount,
      dataDeVencimento: input.dueDate || (isPrazo ? now + 7 * 86400000 : 0),
      observacao: input.observation || 'Venda registrada via WhatsApp',
      dataDeCriacao: now,
      updatedAt: now,
    };

    // Salva a venda no Firestore sob users/{userId}/sales/{saleId}
    await db().collection('users').doc(userId).collection('sales').doc(saleId).set(sale);

    // Se estiver paga, gera o registro de pagamento
    if (paidAmount > 0) {
      const paymentId = randomUUID();
      const payment: PaymentDoc = {
        id: paymentId,
        ownerId: userId,
        businessId,
        saleId,
        customerId,
        customerName,
        amount: paidAmount,
        paymentMethod,
        date: now,
        timeString: hora,
        isDemo: false,
        createdAt: now,
      };

      await db().collection('users').doc(userId).collection('payments').doc(paymentId).set(payment);
    }

    // Monta o resumo formatado em texto
    const itemsSummary = saleItems.map((it) => `• ${it.quantidade}x ${it.productName} (R$ ${it.valorUnitario.toFixed(2)})`).join('\n');
    const summary = `✅ *Venda Registrada com Sucesso!*\n\n` +
      `👤 *Cliente:* ${customerName}\n` +
      `📦 *Itens:*\n${itemsSummary}\n` +
      `💰 *Total:* R$ ${finalTotal.toFixed(2)}\n` +
      `💳 *Forma:* ${paymentMethod} (${statusDoPagamento === 'pago' ? 'Pago' : 'A Prazo / Pendente'})\n` +
      `🕒 *Data/Hora:* ${new Date(now).toLocaleDateString('pt-BR')} às ${hora}`;

    return { sale, summary };
  }

  /**
   * Obtém a última venda registrada pelo usuário.
   */
  async getLastSale(userId: string): Promise<SaleDoc | null> {
    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .orderBy('data', 'desc')
      .limit(1)
      .get();

    if (snap.empty) return null;
    return snap.docs[0].data() as SaleDoc;
  }

  /**
   * Busca uma venda por ID ou prefixo do ID.
   */
  async getSaleById(userId: string, saleId: string): Promise<SaleDoc | null> {
    const cleanId = saleId.trim();
    const docSnap = await db().collection('users').doc(userId).collection('sales').doc(cleanId).get();
    if (docSnap.exists) return docSnap.data() as SaleDoc;

    // Busca por prefixo (ex: últimos 6 dígitos)
    const allSnap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .orderBy('data', 'desc')
      .limit(50)
      .get();

    for (const doc of allSnap.docs) {
      if (doc.id.toLowerCase().startsWith(cleanId.toLowerCase())) {
        return doc.data() as SaleDoc;
      }
    }

    return null;
  }

  /**
   * Busca vendas de um cliente específico.
   */
  async getSalesByCustomer(userId: string, customerName: string): Promise<SaleDoc[]> {
    const search = customerName.trim().toLowerCase();
    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .orderBy('data', 'desc')
      .limit(30)
      .get();

    return snap.docs
      .map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc)
      .filter((s: SaleDoc) => s.clienteNome && s.clienteNome.toLowerCase().includes(search));
  }

  private normalizePaymentMethod(method?: string): string {
    if (!method) return 'Dinheiro';
    const m = method.trim().toLowerCase();
    if (m.includes('pix')) return 'Pix';
    if (m.includes('cart') || m.includes('credito') || m.includes('debito')) return 'Cartão';
    if (m.includes('prazo') || m.includes('fiado') || m.includes('sexta') || m.includes('segunda')) return 'A Prazo';
    if (m.includes('dinheiro') || m.includes('vista')) return 'Dinheiro';
    return 'Outro';
  }
}

export const salesService = new SalesService();
