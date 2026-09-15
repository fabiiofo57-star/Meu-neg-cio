import { db, firebaseService } from './firebase';
import { WhatsAppMappingDoc, WhatsAppUserStatusDoc } from '../types/models';

/**
 * Serviço de Pareamento e Mapeamento de Usuários:
 * Conecta o telefone de WhatsApp ao Firebase UID com isolamento multi-tenant absoluto.
 */
export class PairingService {
  /**
   * Normaliza um número de telefone para apenas dígitos (ex: "+55 (11) 99999-8888" -> "5511999998888").
   */
  normalizePhone(phone: string): string {
    return phone.replace(/\D/g, '');
  }

  /**
   * Gera variantes comuns de telefones brasileiros (com ou sem o dígito 9).
   */
  getPhoneVariants(phone: string): string[] {
    const clean = this.normalizePhone(phone);
    const variants = [clean];

    // Se for um número de celular do Brasil com 13 dígitos (55 + DDD de 2 dígitos + 9 dígitos)
    if (clean.startsWith('55') && clean.length === 13 && clean[4] === '9') {
      const withoutNine = clean.slice(0, 4) + clean.slice(5);
      variants.push(withoutNine);
    } else if (clean.startsWith('55') && clean.length === 12) {
      const withNine = clean.slice(0, 4) + '9' + clean.slice(4);
      variants.push(withNine);
    }

    return variants;
  }

  /**
   * Resolve o Firebase UID a partir do telefone do remetente no WhatsApp.
   */
  async getMappingByPhone(senderPhone: string): Promise<WhatsAppMappingDoc | null> {
    if (!firebaseService.isReady()) return null;

    const variants = this.getPhoneVariants(senderPhone);

    for (const variant of variants) {
      try {
        const docSnap = await db().collection('whatsapp_mappings').doc(variant).get();
        if (docSnap.exists) {
          const data = docSnap.data() as WhatsAppMappingDoc;
          if (data && data.status === 'connected') {
            return data;
          }
        }
      } catch (error) {
        console.error(`Erro ao buscar mapeamento para telefone ${variant}:`, error);
      }
    }

    return null;
  }

  /**
   * Cria um código temporário de pareamento no perfil do usuário no Firestore.
   */
  async createPairingCode(userId: string, inputPhoneNumber: string): Promise<string> {
    const cleanPhone = this.normalizePhone(inputPhoneNumber);
    const randomSuffix = Math.floor(1000 + Math.random() * 9000); // 4 dígitos
    const pairingCode = `MN-${randomSuffix}`;
    const expiresAt = Date.now() + 10 * 60 * 1000; // 10 minutos

    if (firebaseService.isReady()) {
      const statusDoc: WhatsAppUserStatusDoc = {
        phoneNumber: cleanPhone,
        status: 'connecting',
        pairingCode,
        pairingExpiresAt: expiresAt,
        isPro: false,
        updatedAt: Date.now(),
      };

      await db()
        .collection('users')
        .doc(userId)
        .collection('whatsapp')
        .doc('info')
        .set(statusDoc, { merge: true });
    }

    return pairingCode;
  }

  /**
   * Valida o código enviado pelo usuário via WhatsApp e vincula o telefone ao Firebase UID.
   */
  async confirmPairingWithCode(
    senderPhone: string,
    rawCode: string
  ): Promise<{ success: boolean; userId?: string; businessName?: string; error?: string }> {
    if (!firebaseService.isReady()) {
      return { success: false, error: 'Banco de dados não disponível no momento.' };
    }

    const cleanPhone = this.normalizePhone(senderPhone);
    const normalizedCode = rawCode.trim().toUpperCase();
    const targetCode = normalizedCode.startsWith('MN-') ? normalizedCode : `MN-${normalizedCode}`;

    try {
      const querySnap = await db()
        .collectionGroup('whatsapp')
        .where('pairingCode', '==', targetCode)
        .limit(5)
        .get();

      if (querySnap.empty) {
        return {
          success: false,
          error: 'Código não encontrado ou já utilizado. Por favor, gere um novo código no aplicativo Meu Negócio.',
        };
      }

      for (const doc of querySnap.docs) {
        const info = doc.data() as WhatsAppUserStatusDoc;
        const parentUserRef = doc.ref.parent.parent;
        if (!parentUserRef) continue;

        const userId = parentUserRef.id;

        // Verifica expiração
        if (info.pairingExpiresAt && info.pairingExpiresAt < Date.now()) {
          return {
            success: false,
            error: 'Este código de conexão expirou (validade de 10 minutos). Por favor, gere um novo código no aplicativo.',
          };
        }

        let businessName = 'seu negócio';
        try {
          const bizSnap = await parentUserRef.collection('business').doc('info').get();
          if (bizSnap.exists) {
            businessName = bizSnap.data()?.name || businessName;
          }
        } catch {
          // Fallback silencioso
        }

        // 1. Cria o índice reverso em /whatsapp_mappings/{cleanPhone}
        const mapping: WhatsAppMappingDoc = {
          userId,
          phoneNumber: cleanPhone,
          status: 'connected',
          connectedAt: Date.now(),
          isPro: info.isPro || false,
        };

        await db().collection('whatsapp_mappings').doc(cleanPhone).set(mapping, { merge: true });

        // 2. Atualiza o status em /users/{userId}/whatsapp/info
        const updatedStatus: WhatsAppUserStatusDoc = {
          phoneNumber: cleanPhone,
          status: 'connected',
          pairingCode: null,
          pairingExpiresAt: null,
          connectedAt: Date.now(),
          isPro: info.isPro || false,
          updatedAt: Date.now(),
        };

        await doc.ref.set(updatedStatus, { merge: true });

        console.log(`✅ [PAREAMENTO] Telefone ${cleanPhone} associado com sucesso ao UID ${userId} (${businessName})`);
        return { success: true, userId, businessName };
      }

      return { success: false, error: 'Não foi possível validar o código de pareamento.' };
    } catch (error) {
      console.error('Erro ao processar pareamento com código:', error);
      return { success: false, error: 'Erro interno ao validar o pareamento.' };
    }
  }

  /**
   * Desconecta o WhatsApp de um usuário.
   */
  async disconnectUser(userId: string): Promise<boolean> {
    if (!firebaseService.isReady()) return false;

    try {
      const infoRef = db().collection('users').doc(userId).collection('whatsapp').doc('info');
      const infoSnap = await infoRef.get();

      if (infoSnap.exists) {
        const info = infoSnap.data() as WhatsAppUserStatusDoc;
        if (info.phoneNumber) {
          const cleanPhone = this.normalizePhone(info.phoneNumber);
          await db().collection('whatsapp_mappings').doc(cleanPhone).delete();
        }
      }

      await infoRef.set(
        {
          status: 'disconnected',
          phoneNumber: null,
          pairingCode: null,
          pairingExpiresAt: null,
          connectedAt: null,
          updatedAt: Date.now(),
        },
        { merge: true }
      );

      return true;
    } catch (error) {
      console.error(`Erro ao desconectar usuário ${userId}:`, error);
      return false;
    }
  }
}

export const pairingService = new PairingService();
