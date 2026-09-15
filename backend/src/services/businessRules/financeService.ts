import { db } from '../firebase';
import { SaleDoc, ExpenseDoc } from '../../types/models';

export class FinanceService {
  /**
   * Consulta vendas de hoje no Firestore.
   */
  async getSalesToday(userId: string): Promise<SaleDoc[]> {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);

    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .where('data', '>=', startOfDay.getTime())
      .get();

    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc);
  }

  /**
   * Consulta vendas deste mês no Firestore.
   */
  async getSalesThisMonth(userId: string): Promise<SaleDoc[]> {
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);

    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .where('data', '>=', startOfMonth.getTime())
      .get();

    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc);
  }

  /**
   * Consulta valores a receber (vendas pendentes / a prazo).
   */
  async getReceivables(userId: string): Promise<{ total: number; count: number; sales: SaleDoc[] }> {
    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .where('valorRestante', '>', 0.01)
      .get();

    const sales = snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc);
    const total = sales.reduce((acc: number, s: SaleDoc) => acc + (s.valorRestante || 0), 0);

    return { total, count: sales.length, sales };
  }

  /**
   * Calcula resumo financeiro completo do mês.
   */
  async getMonthlyFinancialSummary(userId: string): Promise<{
    salesTotal: number;
    salesCount: number;
    expensesTotal: number;
    expensesCount: number;
    profitEstimate: number;
    averageTicket: number;
  }> {
    const sales = await this.getSalesThisMonth(userId);
    const now = new Date();
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1, 0, 0, 0, 0);

    const expensesSnap = await db()
      .collection('users')
      .doc(userId)
      .collection('expenses')
      .where('data', '>=', startOfMonth.getTime())
      .get();

    const expenses = expensesSnap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as ExpenseDoc);

    const salesTotal = sales.reduce((acc: number, s: SaleDoc) => acc + (s.valorTotal || 0), 0);
    const salesCount = sales.length;
    const expensesTotal = expenses.reduce((acc: number, e: ExpenseDoc) => acc + (e.valor || 0), 0);
    const expensesCount = expenses.length;
    const profitEstimate = salesTotal - expensesTotal;
    const averageTicket = salesCount > 0 ? salesTotal / salesCount : 0;

    return {
      salesTotal,
      salesCount,
      expensesTotal,
      expensesCount,
      profitEstimate,
      averageTicket,
    };
  }

  /**
   * Calcula os produtos mais vendidos no mês.
   */
  async getTopSellingProducts(userId: string, limit = 5): Promise<Array<{ name: string; quantity: number; total: number }>> {
    const sales = await this.getSalesThisMonth(userId);
    const productStats = new Map<string, { quantity: number; total: number }>();

    for (const sale of sales) {
      for (const item of sale.itens || []) {
        const current = productStats.get(item.productName) || { quantity: 0, total: 0 };
        current.quantity += item.quantidade || 0;
        current.total += item.subtotal || 0;
        productStats.set(item.productName, current);
      }
    }

    return Array.from(productStats.entries())
      .map(([name, stats]) => ({ name, ...stats }))
      .sort((a, b) => b.quantity - a.quantity)
      .slice(0, limit);
  }

  /**
   * Consulta dívidas / pendências de um cliente específico ("Quanto o João está devendo?").
   */
  async getCustomerDebts(userId: string, customerName: string): Promise<{ totalDebt: number; sales: SaleDoc[] }> {
    const cleanSearch = customerName.toLowerCase().trim();
    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .where('valorRestante', '>', 0.01)
      .get();

    const allPending = snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc);
    const customerSales = allPending.filter((s: SaleDoc) =>
      (s.clienteNome || '').toLowerCase().includes(cleanSearch)
    );

    const totalDebt = customerSales.reduce((acc: number, s: SaleDoc) => acc + (s.valorRestante || 0), 0);
    return { totalDebt, sales: customerSales };
  }

  /**
   * Consulta as maiores vendas registradas ("Quais foram minhas maiores vendas?").
   */
  async getTopSales(userId: string, limit = 5): Promise<SaleDoc[]> {
    const snap = await db()
      .collection('users')
      .doc(userId)
      .collection('sales')
      .get();

    const sales = snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as SaleDoc);
    return sales.sort((a: SaleDoc, b: SaleDoc) => b.valorTotal - a.valorTotal).slice(0, limit);
  }
}

export const financeService = new FinanceService();
