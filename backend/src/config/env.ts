import dotenv from 'dotenv';

// Carrega variáveis do arquivo .env se estiver em ambiente local
dotenv.config();

export interface BackendConfig {
  port: number;
  nodeEnv: string;
  meta: {
    appId: string;
    appSecret: string;
    accessToken: string;
    verifyToken: string;
    phoneNumberId: string;
    wabaId: string;
    apiVersion: string;
  };
  server: {
    publicUrl: string;
  };
  firebase: {
    projectId: string;
    storageBucket: string;
  };
  gemini: {
    apiKey: string;
    model: string;
  };
}

function getEnv(key: string, fallbackKeys: string[] = [], defaultValue = ''): string {
  if (process.env[key]?.trim()) {
    return process.env[key]!.trim();
  }
  for (const alt of fallbackKeys) {
    if (process.env[alt]?.trim()) {
      return process.env[alt]!.trim();
    }
  }
  return defaultValue;
}

export const config: BackendConfig = {
  port: parseInt(getEnv('PORT', [], '8080'), 10),
  nodeEnv: getEnv('NODE_ENV', [], 'development'),
  meta: {
    appId: getEnv('META_APP_ID', ['APP_ID'], ''),
    appSecret: getEnv('META_APP_SECRET', [], ''),
    accessToken: getEnv('META_ACCESS_TOKEN', ['WHATSAPP_ACCESS_TOKEN'], ''),
    verifyToken: getEnv('META_VERIFY_TOKEN', ['META_WEBHOOK_VERIFY_TOKEN'], 'meu_negocio_verify_token_2026'),
    phoneNumberId: getEnv('META_PHONE_NUMBER_ID', ['WHATSAPP_PHONE_NUMBER_ID'], ''),
    wabaId: getEnv('META_WABA_ID', ['WHATSAPP_BUSINESS_ACCOUNT_ID'], ''),
    apiVersion: getEnv('META_API_VERSION', [], 'v21.0'),
  },
  server: {
    publicUrl: getEnv('PUBLIC_BACKEND_URL', ['BASE_PUBLIC_URL', 'HOST_URL'], ''),
  },
  firebase: {
    projectId: getEnv('FIREBASE_PROJECT_ID', [], 'meu-negocio-ffdd3'),
    storageBucket: getEnv('FIREBASE_STORAGE_BUCKET', [], ''),
  },
  gemini: {
    apiKey: getEnv('GEMINI_API_KEY', [], ''),
    model: getEnv('GEMINI_MODEL', [], 'gemini-3.6-flash'),
  },
};

/**
 * Registra no log o status de carregamento das variáveis (com segurança, mascarando segredos)
 */
export function logConfigurationStatus(): void {
  const mask = (value: string) => {
    if (!value) return '[NÃO CONFIGURADO]';
    if (value.length <= 6) return '******';
    return `${value.slice(0, 3)}...${value.slice(-3)}`;
  };

  console.log('---------------------------------------------------------');
  console.log('⚙️  STATUS DA CONFIGURAÇÃO DO BACKEND (META & FIREBASE):');
  console.log('---------------------------------------------------------');
  console.log(`• Porta do Servidor: ${config.port}`);
  console.log(`• Ambiente: ${config.nodeEnv}`);
  console.log(`• Meta App ID: ${config.meta.appId ? mask(config.meta.appId) : '[Aguardando]'}`);
  console.log(`• Meta App Secret: ${mask(config.meta.appSecret)}`);
  console.log(`• Meta Access Token: ${mask(config.meta.accessToken)}`);
  console.log(`• Meta Verify Token: ${mask(config.meta.verifyToken)}`);
  console.log(`• Meta Phone Number ID: ${mask(config.meta.phoneNumberId)}`);
  console.log(`• Meta WABA ID: ${mask(config.meta.wabaId)}`);
  console.log(`• Meta API Version: ${config.meta.apiVersion}`);
  console.log(`• URL Pública do Servidor: ${config.server.publicUrl || '[Auto-detect]'}`);
  console.log(`• Firebase Project ID: ${config.firebase.projectId}`);
  console.log(`• Gemini API Key: ${mask(config.gemini.apiKey)}`);
  console.log(`• Gemini Model: ${config.gemini.model}`);
  console.log('---------------------------------------------------------');
}
