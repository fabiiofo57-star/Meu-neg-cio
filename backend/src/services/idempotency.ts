import { db, firebaseService } from './firebase';
import { ProcessedMessageDoc } from '../types/models';

/**
 * Serviço de Idempotência:
 * Garante que a Meta não processe a mesma mensagem duas vezes,
 * prevenindo duplicidades de vendas, despesas, clientes e estoque.
 */
export class IdempotencyService {
  private memoryCache = new Set<string>();

  /**
   * Verifica e marca uma mensagem como em processamento de forma atômica.
   */
  async checkAndLock(messageId: string, senderPhone: string, type: string): Promise<boolean> {
    if (!messageId) return true;

    // 1. Verificação rápida em memória
    if (this.memoryCache.has(messageId)) {
      console.warn(`🛑 [IDEMPOTÊNCIA] Mensagem ${messageId} já registrada em memória. Ignorando duplicata.`);
      return false;
    }

    this.memoryCache.add(messageId);
    if (this.memoryCache.size > 5000) {
      const firstItem = this.memoryCache.values().next().value;
      if (firstItem) this.memoryCache.delete(firstItem);
    }

    // 2. Verificação persistente no Firestore
    if (!firebaseService.isReady()) {
      return true;
    }

    try {
      const docRef = db().collection('processed_messages').doc(messageId);
      const doc = await docRef.get();

      if (doc.exists) {
        console.warn(`🛑 [IDEMPOTÊNCIA] Mensagem ${messageId} já processada no Firestore. Descartando execução repetida.`);
        return false;
      }

      const record: ProcessedMessageDoc = {
        messageId,
        senderPhone,
        type,
        processedAt: Date.now(),
      };

      await docRef.set(record);
      return true;
    } catch (error) {
      console.error('⚠️ [IDEMPOTÊNCIA] Erro ao consultar processed_messages no Firestore:', error);
      return true;
    }
  }
}

export const idempotencyService = new IdempotencyService();
