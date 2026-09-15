import { randomUUID } from 'crypto';
import { db } from '../firebase';
import { CustomerDoc } from '../../types/models';

export class CustomerService {
  /**
   * Lista todos os clientes cadastrados para o usuário.
   */
  async listCustomers(userId: string): Promise<CustomerDoc[]> {
    const snap = await db().collection('users').doc(userId).collection('customers').get();
    return snap.docs.map((d: FirebaseFirestore.QueryDocumentSnapshot) => d.data() as CustomerDoc);
  }

  /**
   * Busca um cliente por nome aproximado (case-insensitive).
   */
  async findCustomerByName(userId: string, name: string): Promise<CustomerDoc | null> {
    const customers = await this.listCustomers(userId);
    const search = name.trim().toLowerCase();

    // 1. Busca exata
    const exact = customers.find((c) => c.nome.trim().toLowerCase() === search);
    if (exact) return exact;

    // 2. Busca parcial
    const partial = customers.find((c) => {
      const cName = c.nome.toLowerCase();
      return cName.includes(search) || search.includes(cName);
    });

    return partial || null;
  }

  /**
   * Cadastra um novo cliente na coleção users/{userId}/customers/{customerId}.
   */
  async createCustomer(
    userId: string,
    businessId: string,
    data: {
      nome: string;
      telefone?: string;
      email?: string;
      endereco?: string;
      observacao?: string;
    }
  ): Promise<CustomerDoc> {
    const id = randomUUID();
    const now = Date.now();

    const customer: CustomerDoc = {
      id,
      ownerId: userId,
      businessId,
      nome: data.nome.trim(),
      telefone: data.telefone?.trim() || '',
      email: data.email?.trim() || '',
      endereco: data.endereco?.trim() || '',
      observacao: data.observacao?.trim() || 'Cadastrado via WhatsApp',
      dataDeCadastro: now,
      updatedAt: now,
    };

    await db().collection('users').doc(userId).collection('customers').doc(id).set(customer);

    return customer;
  }
}

export const customerService = new CustomerService();
