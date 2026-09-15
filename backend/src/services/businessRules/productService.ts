import { randomUUID } from 'crypto';
import { db } from '../firebase';
import { ProductDoc, StockMovementDoc } from '../../types/models';

export class ProductService {
  /**
   * Lista todos os produtos cadastrados para o usuário.
   */
  async listProducts(userId: string): Promise<ProductDoc[]> {
    const snap = await db().collection('users').doc(userId).collection('products').get();
    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as ProductDoc);
  }

  /**
   * Busca um produto por nome aproximado (case-insensitive).
   */
  async findProductByName(userId: string, name: string): Promise<ProductDoc | null> {
    const products = await this.listProducts(userId);
    const search = name.trim().toLowerCase();

    // 1. Busca exata
    const exact = products.find((p) => p.nome.trim().toLowerCase() === search);
    if (exact) return exact;

    // 2. Busca parcial / contenção
    const partial = products.find((p) => {
      const pName = p.nome.toLowerCase();
      return pName.includes(search) || search.includes(pName);
    });

    return partial || null;
  }

  /**
   * Cadastra um novo produto na coleção users/{userId}/products/{productId}.
   */
  async createProduct(
    userId: string,
    businessId: string,
    data: {
      nome: string;
      precoVenda: number;
      custo?: number;
      estoqueInicial?: number;
      categoria?: string;
    }
  ): Promise<ProductDoc> {
    const id = randomUUID();
    const now = Date.now();
    const estoque = data.estoqueInicial || 0;

    const product: ProductDoc = {
      id,
      ownerId: userId,
      businessId,
      nome: data.nome.trim(),
      categoria: data.categoria?.trim() || 'Geral',
      precoVenda: data.precoVenda,
      custo: data.custo || 0,
      estoqueAtual: estoque,
      estoqueMinimo: 0,
      dataDeCriacao: now,
      dataDeAtualizacao: now,
      updatedAt: now,
    };

    await db().collection('users').doc(userId).collection('products').doc(id).set(product);

    // Se tiver estoque inicial, registra a movimentação de entrada
    if (estoque > 0) {
      const movementId = randomUUID();
      const movement: StockMovementDoc = {
        id: movementId,
        ownerId: userId,
        businessId,
        productId: id,
        productName: product.nome,
        tipo: 'ENTRY',
        quantidade: estoque,
        estoqueAnterior: 0,
        estoqueNovo: estoque,
        data: now,
        motivo: 'Estoque inicial cadastrado via WhatsApp',
        dataDeCriacao: now,
        updatedAt: now,
      };

      await db()
        .collection('users')
        .doc(userId)
        .collection('stockMovements')
        .doc(movementId)
        .set(movement);
    }

    return product;
  }

  /**
   * Atualiza o estoque de um produto (ex: entrada de mercadoria ou saída por venda)
   * e registra a movimentação de estoque compatível com o app Android.
   */
  async updateStock(
    userId: string,
    businessId: string,
    productId: string,
    deltaQuantity: number,
    reason: string
  ): Promise<{ product: ProductDoc; movement: StockMovementDoc } | null> {
    const prodRef = db().collection('users').doc(userId).collection('products').doc(productId);
    const snap = await prodRef.get();
    if (!snap.exists) return null;

    const product = snap.data() as ProductDoc;
    const previousStock = product.estoqueAtual || 0;
    const newStock = Math.max(0, previousStock + deltaQuantity);
    const now = Date.now();

    await prodRef.update({
      estoqueAtual: newStock,
      dataDeAtualizacao: now,
      updatedAt: now,
    });

    product.estoqueAtual = newStock;

    // Registra a movimentação de estoque
    const movementId = randomUUID();
    const movement: StockMovementDoc = {
      id: movementId,
      ownerId: userId,
      businessId,
      productId: product.id,
      productName: product.nome,
      tipo: deltaQuantity >= 0 ? 'ENTRY' : 'EXIT',
      quantidade: Math.abs(deltaQuantity),
      estoqueAnterior: previousStock,
      estoqueNovo: newStock,
      data: now,
      motivo: reason,
      dataDeCriacao: now,
      updatedAt: now,
    };

    await db()
      .collection('users')
      .doc(userId)
      .collection('stockMovements')
      .doc(movementId)
      .set(movement);

    return { product, movement };
  }

  /**
   * Retorna os produtos com estoque baixo ou zerado.
   */
  async getLowStockProducts(userId: string): Promise<ProductDoc[]> {
    const products = await this.listProducts(userId);
    return products.filter((p) => p.estoqueAtual <= (p.estoqueMinimo || 0));
  }
}

export const productService = new ProductService();
