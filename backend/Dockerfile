# =============================================================================
# DOCKERFILE MULTI-STAGE — MEU NEGÓCIO WHATSAPP BACKEND (CLOUD RUN)
# =============================================================================

# -----------------------------------------------------------------------------
# Estágio 1: Builder (Compilação do TypeScript)
# -----------------------------------------------------------------------------
FROM node:20-slim AS builder

WORKDIR /app

# Copia manifestos de dependências
COPY package*.json tsconfig.json ./
RUN npm ci

# Copia código-fonte e compila
COPY src/ ./src/
RUN npm run build

# -----------------------------------------------------------------------------
# Estágio 2: Runner (Imagem final ultraleve de produção)
# -----------------------------------------------------------------------------
FROM node:20-slim AS runner

WORKDIR /app

ENV NODE_ENV=production

# Instala apenas dependências de produção
COPY package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

# Copia artefatos compilados do estágio anterior
COPY --from=builder /app/dist ./dist

# Diretório para documentos temporários gerados em tempo de execução
RUN mkdir -p temp_documents

# O Google Cloud Run fornece a porta via variável de ambiente PORT (padrão 8080)
EXPOSE 8080

CMD ["npm", "start"]
