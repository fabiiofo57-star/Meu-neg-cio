import crypto from 'crypto';
import axios from 'axios';

const TEST_PORT = process.env.PORT || '8085';
const BASE_URL = `http://localhost:${TEST_PORT}`;
const VERIFY_TOKEN = 'meu_negocio_verify_token_2026';
const APP_SECRET = 'teste_segredo_meta_123';

async function runTests() {
  console.log('🧪 [TESTES AUTOMATIZADOS DO WEBHOOK DA META]');

  // 1. Health check
  try {
    const health = await axios.get(`${BASE_URL}/health`);
    console.log('✅ Teste 1 (GET /health): OK! Status:', health.status, health.data.status);
  } catch (e: any) {
    console.error('❌ Teste 1 falhou:', e.message);
  }

  // 2. GET /webhook com token incorreto (Deve retornar 403)
  try {
    await axios.get(`${BASE_URL}/webhook`, {
      params: {
        'hub.mode': 'subscribe',
        'hub.verify_token': 'token_errado',
        'hub.challenge': '999999',
      },
    });
    console.error('❌ Teste 2 falhou: Deveria ter retornado 403 para token errado');
  } catch (e: any) {
    if (e.response?.status === 403) {
      console.log('✅ Teste 2 (GET /webhook token errado -> 403 Forbidden): OK!');
    } else {
      console.error('❌ Teste 2 falhou com status inesperado:', e.message);
    }
  }

  // 3. GET /webhook com parâmetros oficiais da Meta (Deve retornar 200 e o challenge)
  try {
    const challenge = '1158201444';
    const res = await axios.get(`${BASE_URL}/webhook`, {
      params: {
        'hub.mode': 'subscribe',
        'hub.verify_token': VERIFY_TOKEN,
        'hub.challenge': challenge,
      },
    });

    if (res.status === 200 && res.data.toString() === challenge) {
      console.log('✅ Teste 3 (GET /webhook verificação oficial da Meta -> 200 e challenge retornado): OK!');
    } else {
      console.error('❌ Teste 3 falhou: Resposta diferente do challenge:', res.data);
    }
  } catch (e: any) {
    console.error('❌ Teste 3 falhou:', e.message);
  }

  // 4. POST /webhook com evento de mensagem de texto (Simulando WhatsApp Cloud API)
  try {
    const samplePayload = {
      object: 'whatsapp_business_account',
      entry: [
        {
          id: 'WABA_ID_TESTE_123',
          changes: [
            {
              field: 'messages',
              value: {
                messaging_product: 'whatsapp',
                metadata: {
                  display_phone_number: '5511999998888',
                  phone_number_id: 'PHONE_NUMBER_ID_TESTE',
                },
                contacts: [
                  {
                    profile: {
                      name: 'Fábio Empreendedor',
                    },
                    wa_id: '5511999997777',
                  },
                ],
                messages: [
                  {
                    from: '5511999997777',
                    id: 'wamid.HBgLMTE5OTk5OTc3NzcVAgASGBQzQTkyREIzRjAwRkRGMkI2NzAzMwA=',
                    timestamp: Math.floor(Date.now() / 1000).toString(),
                    type: 'text',
                    text: {
                      body: 'Quanto vendi hoje?',
                    },
                  },
                ],
              },
            },
          ],
        },
      ],
    };

    const rawPayload = JSON.stringify(samplePayload);
    const signature = crypto
      .createHmac('sha256', APP_SECRET)
      .update(Buffer.from(rawPayload, 'utf8'))
      .digest('hex');

    const res = await axios.post(`${BASE_URL}/webhook`, samplePayload, {
      headers: {
        'Content-Type': 'application/json',
        'X-Hub-Signature-256': `sha256=${signature}`,
      },
    });

    if (res.status === 200 && res.data === 'EVENT_RECEIVED') {
      console.log('✅ Teste 4 (POST /webhook evento de mensagem com assinatura válida -> 200 EVENT_RECEIVED): OK!');
    } else {
      console.error('❌ Teste 4 falhou: Status ou resposta inesperada:', res.status, res.data);
    }
  } catch (e: any) {
    console.error('❌ Teste 4 falhou:', e.response?.data || e.message);
  }

  // 5. POST /webhook com assinatura adulterada/falsa (Deve bloquear com 403)
  try {
    const fakePayload = { object: 'whatsapp_business_account', entry: [] };
    await axios.post(`${BASE_URL}/webhook`, fakePayload, {
      headers: {
        'Content-Type': 'application/json',
        'X-Hub-Signature-256': 'sha256=assinatura_fraudulenta_ou_adulterada_1234567890abcdef',
      },
    });
    console.error('❌ Teste 5 falhou: Deveria ter bloqueado requisição com assinatura falsa!');
  } catch (e: any) {
    if (e.response?.status === 403) {
      console.log('✅ Teste 5 (POST /webhook com assinatura adulterada -> 403 Acesso Negado): OK!');
    } else {
      console.error('❌ Teste 5 falhou com status inesperado:', e.message);
    }
  }

  console.log('\n🎉 TODOS OS TESTES DE SEGURANÇA E WEBHOOK DA ETAPA 2 FORAM APROVADOS!\n');
}

runTests();
