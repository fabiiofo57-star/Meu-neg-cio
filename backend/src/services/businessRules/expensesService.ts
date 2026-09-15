import { randomUUID } from 'crypto';
import { db } from '../firebase';
import { ExpenseDoc } from '../../types/models';

export class ExpensesService {
  /**
   * Registra uma despesa na coleção users/{userId}/expenses/{expenseId}.
   */
  async createExpense(
    userId: string,
    businessId: string,
    data: {
      descricao: string;
      valor: number;
      categoria?: string;
      observacao?: string;
      data?: number;
    }
  ): Promise<{ expense: ExpenseDoc; summary: string }> {
    const id = randomUUID();
    const now = data.data || Date.now();

    const expense: ExpenseDoc = {
      id,
      ownerId: userId,
      businessId,
      descricao: data.descricao.trim(),
      categoria: data.categoria?.trim() || 'Outros',
      valor: data.valor,
      data: now,
      observacao: data.observacao?.trim() || 'Registrada via WhatsApp',
      dataDeCriacao: now,
      updatedAt: now,
    };

    await db().collection('users').doc(userId).collection('expenses').doc(id).set(expense);

    const summary = `💸 *Despesa Registrada!*\n\n` +
      `📝 *Descrição:* ${expense.descricao}\n` +
      `🏷️ *Categoria:* ${expense.categoria}\n` +
      `💰 *Valor:* R$ ${expense.valor.toFixed(2)}\n` +
      `📅 *Data:* ${new Date(now).toLocaleDateString('pt-BR')}`;

    return { expense, summary };
  }

  /**
   * Retorna despesas do dia atual.
   */
  async getExpensesToday(userId: string): Promise<ExpenseDoc[]> {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);

    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('expenses')
      .where('data', '>=', startOfDay.getTime())
      .get();

    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as ExpenseDoc);
  }

  /**
   * Retorna despesas do mês corrente.
   */
  async getExpensesThisMonth(userId: string): Promise<ExpenseDoc[]> {
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);

    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('expenses')
      .where('data', '>=', startOfMonth.getTime())
      .get();

    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as ExpenseDoc);
  }
}

export const expensesService = new ExpensesService();
