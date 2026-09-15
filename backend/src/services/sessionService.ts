import { db, firebaseService } from './firebase';
import { WhatsAppSessionDoc } from '../types/models';

/**
 * Serviço de Sessão de Confirmação:
 * Gerencia o estado conversacional de confirmação em duas etapas (Two-Phase Action Confirmation)
 * para operações sensíveis como registro de vendas e despesas.
 */
export class SessionService {
  private memorySessions = new Map<string, WhatsAppSessionDoc>();

  /**
   * Salva uma ação pendente de confirmação com validade de 10 minutos.
   */
  async setPendingAction(
    userId: string,
    action: 'CREATE_SALE' | 'CREATE_EXPENSE',
    payload: any,
    summary: string
  ): Promise<void> {
    const session: WhatsAppSessionDoc = {
      userId,
      pendingAction: action,
      payload,
      summary,
      createdAt: Date.now(),
      expiresAt: Date.now() + 10 * 60 * 1000, // 10 minutos
    };

    this.memorySessions.set(userId, session);

    if (firebaseService.isReady()) {
      try {
        await db()
          .collection('users')
          .doc(userId)
          .collection('whatsapp_session')
          .doc('current')
          .set(session);
      } catch (error) {
        console.error('Erro ao salvar sessão pendente no Firestore:', error);
      }
    }
  }

  /**
   * Obtém a ação pendente ativa do usuário. Se expirou, limpa e retorna null.
   */
  async getPendingAction(userId: string): Promise<WhatsAppSessionDoc | null> {
    let session = this.memorySessions.get(userId);

    if (!session && firebaseService.isReady()) {
      try {
        const snap = await db()
          .collection('users')
          .doc(userId)
          .collection('whatsapp_session')
          .doc('current')
          .get();

        if (snap.exists) {
          session = snap.data() as WhatsAppSessionDoc;
        }
      } catch (error) {
        console.error('Erro ao buscar sessão no Firestore:', error);
      }
    }

    if (!session) return null;

    // Verifica expiração (10 minutos)
    if (Date.now() > session.expiresAt) {
      await this.clearPendingAction(userId);
      return null;
    }

    return session;
  }

  /**
   * Limpa a ação pendente após confirmação ou cancelamento.
   */
  async clearPendingAction(userId: string): Promise<void> {
    this.memorySessions.delete(userId);

    if (firebaseService.isReady()) {
      try {
        await db()
          .collection('users')
          .doc(userId)
          .collection('whatsapp_session')
          .doc('current')
          .delete();
      } catch (error) {
        console.error('Erro ao limpar sessão no Firestore:', error);
      }
    }
  }

  /**
   * Identifica se a resposta do usuário é uma confirmação afirmativa natural em português.
   */
  isAffirmative(text: string): boolean {
    const normalized = text.trim().toLowerCase().replace(/[!.?,]/g, '');
    const affirmativeKeywords = [
      'sim',
      's',
      'pode',
      'pode registrar',
      'pode salvar',
      'confirmo',
      'confirmado',
      'confirmar',
      'ok',
      'positivo',
      'manda ver',
      'isso',
      'isso mesmo',
      'correto',
      'beleza',
      'show',
      'bora',
      'combinado',
    ];

    return affirmativeKeywords.includes(normalized);
  }

  /**
   * Identifica se a resposta do usuário é uma negação ou cancelamento.
   */
  isNegative(text: string): boolean {
    const normalized = text.trim().toLowerCase().replace(/[!.?,]/g, '');
    const negativeKeywords = [
      'nao',
      'não',
      'n',
      'cancela',
      'cancelar',
      'não registra',
      'nao registra',
      'deixa pra la',
      'deixa pra lá',
      'esquece',
      'errado',
      'desistir',
    ];

    return negativeKeywords.includes(normalized);
  }
}

export const sessionService = new SessionService();
