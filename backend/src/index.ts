import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import * as path from 'path';
import { config, logConfigurationStatus } from './config/env';
import { webhookRouter } from './routes/webhook';

const app = express();

// Middleware de CORS
app.use(cors());

// Middleware para capturar o corpo bruto (rawBody) da requisição
// CRUCIAL: A validação da assinatura HMAC-SHA256 da Meta requer os bytes brutos exatos recebidos
app.use(
  express.json({
    verify: (req: Request & { rawBody?: Buffer }, _res: Response, buf: Buffer) => {
      req.rawBody = buf;
    },
  })
);

// URL-encoded para formulários simples se necessário
app.use(express.urlencoded({ extended: true }));

// Rota de Health Check
app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({
    status: 'online',
    app: 'meu-negocio-whatsapp-backend',
    environment: config.nodeEnv,
    timestamp: new Date().toISOString(),
    endpoints: {
      webhook_verification: 'GET /webhook',
      webhook_events: 'POST /webhook',
      health_check: 'GET /health',
    },
  });
});

// Raiz de boas-vindas / status
app.get('/', (_req: Request, res: Response) => {
  res.status(200).send('🚀 Backend do Meu Negócio para WhatsApp Cloud API ativo e operando.');
});

// Rotas do Webhook da Meta
app.use('/webhook', webhookRouter);

// Servir documentos temporários gerados (Comprovantes de Venda e Relatórios em PDF)
app.use('/temp_documents', express.static(path.join(process.cwd(), 'temp_documents')));

// Middleware para rotas não encontradas (404)
app.use((req: Request, res: Response) => {
  res.status(404).json({
    error: 'Rota não encontrada',
    path: req.originalUrl,
    method: req.method,
  });
});

// Middleware global de tratamento de erros
app.use((err: Error, _req: Request, res: Response, _next: NextFunction) => {
  console.error('💥 [ERRO INTERNO NO SERVIDOR]:', err);
  res.status(500).json({
    error: 'Erro interno do servidor',
    message: config.nodeEnv === 'development' ? err.message : 'Ocorreu um erro ao processar sua requisição.',
  });
});

// Inicia o servidor HTTP
const server = app.listen(config.port, () => {
  logConfigurationStatus();
  console.log(`\n🚀 [SERVIDOR PRONTO] Ouvindo na porta ${config.port}`);
  console.log(`👉 Webhook URL para cadastrar na Meta: https://SEU_DOMINIO/webhook`);
  console.log(`👉 Token de verificação: ${config.meta.verifyToken}\n`);
});

// Encerramento gracioso
const shutdown = (signal: string) => {
  console.log(`\n🛑 [SHUTDOWN] Recebido sinal ${signal}. Encerrando servidor...`);
  server.close(() => {
    console.log('✅ [SHUTDOWN] Servidor encerrado com sucesso.');
    process.exit(0);
  });
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

export default app;
