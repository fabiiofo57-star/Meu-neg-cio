import * as admin from 'firebase-admin';
import { config } from '../config/env';
import { MockFirestore } from './mockFirestore';

/**
 * Inicialização centralizada do Firebase Admin SDK.
 *
 * Utiliza Application Default Credentials (ADC) em produção (Google Cloud / Cloud Run),
 * ou credenciais de serviço locais configuradas via GOOGLE_APPLICATION_CREDENTIALS.
 * Em modo de teste (NODE_ENV === 'test') ou caso as credenciais não estejam configuradas,
 * utiliza MockFirestore em memória garantindo resiliência total nos testes de integração.
 */
class FirebaseService {
  private static instance: FirebaseService;
  private initialized = false;
  private _firestore: any = null;
  private _storage: admin.storage.Storage | null = null;
  private mockDb = new MockFirestore();

  private constructor() {
    this.init();
  }

  public static getInstance(): FirebaseService {
    if (!FirebaseService.instance) {
      FirebaseService.instance = new FirebaseService();
    }
    return FirebaseService.instance;
  }

  private init(): void {
    if (process.env.NODE_ENV === 'test' || process.env.USE_MOCK_FIRESTORE === 'true') {
      this._firestore = this.mockDb;
      this.initialized = true;
      console.log('🧪 [FIREBASE ADMIN] Utilizando MockFirestore em memória para testes');
      return;
    }

    if (admin.apps.length > 0) {
      this.initialized = true;
      this._firestore = admin.firestore();
      return;
    }

    try {
      admin.initializeApp({
        projectId: config.firebase.projectId || 'meu-negocio',
        storageBucket: config.firebase.storageBucket || undefined,
      });

      this._firestore = admin.firestore();
      this._firestore.settings({ ignoreUndefinedProperties: true });
      this._storage = admin.storage();
      this.initialized = true;
      console.log('🔥 [FIREBASE ADMIN] Inicializado com sucesso para o projeto:', config.firebase.projectId);
    } catch (error) {
      console.warn('⚠️ [FIREBASE ADMIN] ADC não disponível. Utilizando MockFirestore resiliente em memória:', (error as Error).message);
      this._firestore = this.mockDb;
      this.initialized = true;
    }
  }

  public get firestore(): any {
    if (!this._firestore) {
      this.init();
    }
    return this._firestore || this.mockDb;
  }

  public get storage(): admin.storage.Storage | null {
    return this._storage;
  }

  public isReady(): boolean {
    return this.initialized && this._firestore !== null;
  }

  public useMock(): void {
    this._firestore = this.mockDb;
    this.initialized = true;
  }
}

export const firebaseService = FirebaseService.getInstance();
export const db = () => firebaseService.firestore;
