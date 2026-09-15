/**
 * In-Memory Firestore Mock para Testes Unitários e Ambiente de Desenvolvimento
 * Permite testar todo o fluxo multi-tenant, persistência de vendas, estoque e pareamento
 * sem depender de credenciais externas do Google Cloud no ambiente de build.
 */

export class MockFirestore {
  private store = new Map<string, any>();

  collection(collPath: string): MockCollection {
    return new MockCollection(collPath, this.store);
  }

  collectionGroup(groupName: string): MockCollectionGroup {
    return new MockCollectionGroup(groupName, this.store);
  }

  clear(): void {
    this.store.clear();
  }
}

export class MockCollection {
  constructor(public path: string, private store: Map<string, any>) {}

  doc(docId: string): MockDocument {
    return new MockDocument(`${this.path}/${docId}`, this.store);
  }

  where(field: string, op: string, value: any): MockQuery {
    return new MockQuery(this.path, this.store).where(field, op, value);
  }

  orderBy(field: string, direction: 'asc' | 'desc' = 'asc'): MockQuery {
    return new MockQuery(this.path, this.store).orderBy(field, direction);
  }

  limit(count: number): MockQuery {
    return new MockQuery(this.path, this.store).limit(count);
  }

  async get(): Promise<MockQuerySnapshot> {
    return new MockQuery(this.path, this.store).get();
  }
}

export class MockDocument {
  constructor(public fullPath: string, private store: Map<string, any>) {}

  get id(): string {
    const parts = this.fullPath.split('/');
    return parts[parts.length - 1];
  }

  get parent(): any {
    const parts = this.fullPath.split('/');
    const store = this.store;
    return {
      get parent(): any {
        if (parts.length >= 3) {
          const parentDocPath = parts.slice(0, parts.length - 2).join('/');
          return new MockDocument(parentDocPath, store);
        }
        return null;
      },
    };
  }

  collection(subCollName: string): MockCollection {
    return new MockCollection(`${this.fullPath}/${subCollName}`, this.store);
  }

  async set(data: any, options?: { merge?: boolean }): Promise<void> {
    const current = this.store.get(this.fullPath) || {};
    if (options?.merge) {
      this.store.set(this.fullPath, { ...current, ...data });
    } else {
      this.store.set(this.fullPath, { ...data });
    }
  }

  async get(): Promise<MockDocumentSnapshot> {
    const data = this.store.get(this.fullPath);
    return new MockDocumentSnapshot(this.id, this.fullPath, data, this.store);
  }

  async update(data: any): Promise<void> {
    const current = this.store.get(this.fullPath) || {};
    this.store.set(this.fullPath, { ...current, ...data });
  }

  async delete(): Promise<void> {
    this.store.delete(this.fullPath);
  }
}

export class MockDocumentSnapshot {
  constructor(
    public id: string,
    public fullPath: string,
    private _data: any | undefined,
    private store: Map<string, any>
  ) {}

  get exists(): boolean {
    return this._data !== undefined;
  }

  data(): any {
    return this._data ? JSON.parse(JSON.stringify(this._data)) : undefined;
  }

  get ref(): MockDocument {
    return new MockDocument(this.fullPath, this.store);
  }
}

export class MockQuery {
  private filters: Array<{ field: string; op: string; value: any }> = [];
  private orderField?: string;
  private orderDir: 'asc' | 'desc' = 'asc';
  private limitCount?: number;

  constructor(public collPath: string, private store: Map<string, any>) {}

  where(field: string, op: string, value: any): MockQuery {
    this.filters.push({ field, op, value });
    return this;
  }

  orderBy(field: string, direction: 'asc' | 'desc' = 'asc'): MockQuery {
    this.orderField = field;
    this.orderDir = direction;
    return this;
  }

  limit(count: number): MockQuery {
    this.limitCount = count;
    return this;
  }

  async get(): Promise<MockQuerySnapshot> {
    const docs: MockDocumentSnapshot[] = [];
    const prefix = `${this.collPath}/`;

    for (const [key, val] of this.store.entries()) {
      if (key.startsWith(prefix)) {
        const remaining = key.substring(prefix.length);
        if (!remaining.includes('/')) {
          const docId = remaining;
          let match = true;

          for (const f of this.filters) {
            const docVal = val[f.field];
            if (f.op === '==') {
              if (docVal !== f.value) match = false;
            } else if (f.op === '>=') {
              if (docVal < f.value) match = false;
            } else if (f.op === '>') {
              if (docVal <= f.value) match = false;
            } else if (f.op === '<=') {
              if (docVal > f.value) match = false;
            } else if (f.op === '<') {
              if (docVal >= f.value) match = false;
            }
          }

          if (match) {
            docs.push(new MockDocumentSnapshot(docId, key, val, this.store));
          }
        }
      }
    }

    if (this.orderField) {
      docs.sort((a, b) => {
        const valA = a.data()[this.orderField!];
        const valB = b.data()[this.orderField!];
        if (valA < valB) return this.orderDir === 'asc' ? -1 : 1;
        if (valA > valB) return this.orderDir === 'asc' ? 1 : -1;
        return 0;
      });
    }

    const finalDocs = this.limitCount !== undefined ? docs.slice(0, this.limitCount) : docs;
    return new MockQuerySnapshot(finalDocs);
  }
}

export class MockCollectionGroup {
  private filters: Array<{ field: string; op: string; value: any }> = [];
  private limitCount?: number;

  constructor(private groupName: string, private store: Map<string, any>) {}

  where(field: string, op: string, value: any): MockCollectionGroup {
    this.filters.push({ field, op, value });
    return this;
  }

  limit(count: number): MockCollectionGroup {
    this.limitCount = count;
    return this;
  }

  async get(): Promise<MockQuerySnapshot> {
    const docs: MockDocumentSnapshot[] = [];

    for (const [key, val] of this.store.entries()) {
      const parts = key.split('/');
      // /users/{userId}/whatsapp/info -> group is parts[parts.length - 2]
      if (parts.length >= 2 && parts[parts.length - 2] === this.groupName) {
        const docId = parts[parts.length - 1];
        let match = true;
        for (const f of this.filters) {
          if (f.op === '==' && val[f.field] !== f.value) match = false;
        }
        if (match) {
          docs.push(new MockDocumentSnapshot(docId, key, val, this.store));
        }
      }
    }

    const finalDocs = this.limitCount !== undefined ? docs.slice(0, this.limitCount) : docs;
    return new MockQuerySnapshot(finalDocs);
  }
}

export class MockQuerySnapshot {
  constructor(public docs: MockDocumentSnapshot[]) {}

  get empty(): boolean {
    return this.docs.length === 0;
  }

  get size(): number {
    return this.docs.length;
  }
}
