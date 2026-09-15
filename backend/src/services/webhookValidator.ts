import crypto from 'crypto';
import { Request, Response, NextFunction } from 'express';
import { config } from '../config/env';

/**
 * Validação pura de assinatura HMAC-SHA256 (tempo constante).
 */
export function validateSignature(rawBody: Buffer, signatureHeader: string | undefined, secret: string): boolean {
  if (!signatureHeader || !secret) return false;

  const [algorithm, signatureHash] = signatureHeader.split('=');
  if (algorithm !== 'sha256' || !signatureHash) return false;

  try {
    const expectedHash = crypto.createHmac('sha256', secret).update(rawBody).digest('hex');
    const signatureBuffer = Buffer.from(signatureHash, 'utf8');
    const expectedBuffer = Buffer.from(expectedHash, 'utf8');

    if (signatureBuffer.length !== expectedBuffer.length) return false;
    return crypto.timingSafeEqual(signatureBuffer, expectedBuffer);
  } catch {
    return false;
  }
}

/**
 * Middleware para validar a assinatura de segurança da Meta (X-Hub-Signature-256).
 *
 * Todas as requisições enviadas pela Meta contêm o cabeçalho 'X-Hub-Signature-256',
 * contendo o hash HMAC-SHA256 do corpo bruto da requisição, assinado com o META_APP_SECRET.
 *
 * Isso garante que nenhuma requisição fraudulenta ou de terceiros seja processada pelo webhook.
 */
export function validateMetaSignature(
  req: Request & { rawBody?: Buffer },
  res: Response,
  next: NextFunction
): void {
  const signatureHeader = req.headers['x-hub-signature-256'] as string | undefined;

  // Se o App Secret não estiver configurado (ex: início do desenvolvimento)
  if (!config.meta.appSecret) {
    console.warn('[SEGURANÇA WEBHOOK] META_APP_SECRET não está definido. Ignorando validação estrita temporariamente.');
    return next();
  }

  if (!signatureHeader) {
    console.error('[SEGURANÇA WEBHOOK] Cabeçalho X-Hub-Signature-256 ausente na requisição POST.');
    res.status(401).json({
      error: 'Assinatura do webhook ausente',
      message: 'O cabeçalho X-Hub-Signature-256 é obrigatório para autenticar requisições da Meta.',
    });
    return;
  }

  // O formato esperado do cabeçalho é: "sha256=<hash>"
  const [algorithm, signatureHash] = signatureHeader.split('=');
  if (algorithm !== 'sha256' || !signatureHash) {
    console.error(`[SEGURANÇA WEBHOOK] Formato de assinatura inválido: ${signatureHeader}`);
    res.status(401).json({
      error: 'Assinatura inválida',
      message: 'Formato esperado: sha256=<hash_hexadecimal>',
    });
    return;
  }

  // Recupera o payload bruto original
  const rawBody = req.rawBody;
  if (!rawBody || rawBody.length === 0) {
    console.error('[SEGURANÇA WEBHOOK] Corpo bruto da requisição (rawBody) não encontrado.');
    res.status(400).json({
      error: 'Corpo vazio',
      message: 'Não foi possível ler o corpo bruto para verificação de assinatura.',
    });
    return;
  }

  try {
    // Calcula o hash esperado com a chave secreta
    const expectedHash = crypto
      .createHmac('sha256', config.meta.appSecret)
      .update(rawBody)
      .digest('hex');

    // Comparação de tempo constante (previne timing attacks)
    const signatureBuffer = Buffer.from(signatureHash, 'utf8');
    const expectedBuffer = Buffer.from(expectedHash, 'utf8');

    if (
      signatureBuffer.length !== expectedBuffer.length ||
      !crypto.timingSafeEqual(signatureBuffer, expectedBuffer)
    ) {
      console.error('[SEGURANÇA WEBHOOK] Falha na validação da assinatura HMAC-SHA256!');
      console.error(`Recebido: ${signatureHash}`);
      console.error(`Esperado: ${expectedHash}`);
      res.status(403).json({
        error: 'Acesso negado',
        message: 'A assinatura criptográfica fornecida não confere com o App Secret configurado.',
      });
      return;
    }

    // Assinatura válida com sucesso!
    next();
  } catch (error) {
    console.error('[SEGURANÇA WEBHOOK] Erro ao processar validação da assinatura:', error);
    res.status(500).json({ error: 'Erro interno na validação de segurança' });
  }
}
