/**
 * Suíte Completa de Testes de Integração e Isolamento Multi-Tenant do WhatsApp
 */
process.env.NODE_ENV = 'test';
process.env.USE_MOCK_FIRESTORE = 'true';

import { firebaseService, db } from './services/firebase';
// Ativa o banco em memória isolado para suíte de testes
firebaseService.useMock();

import { pairingService } from './services/pairingService';
import { idempotencyService } from './services/idempotency';
import { sessionService } from './services/sessionService';
import { geminiService } from './services/geminiService';
import { salesService } from './services/businessRules/salesService';
import { expensesService } from './services/businessRules/expensesService';
import { customerService } from './services/businessRules/customerService';
import { productService } from './services/businessRules/productService';
import { financeService } from './services/businessRules/financeService';
import { pdfService } from './services/pdfService';
import { validateSignature } from './services/webhookValidator';
import * as crypto from 'crypto';

let testsPassed = 0;
let testsFailed = 0;

function assert(condition: boolean, description: string) {
  if (condition) {
    console.log(`  ✅ [PASS] ${description}`);
    testsPassed++;
  } else {
    console.error(`  ❌ [FAIL] ${description}`);
    testsFailed++;
  }
}

async function runAllTests() {
  console.log('\n=============================================================');
  console.log('🧪 INICIANDO SUÍTE COMPLETA DE TESTES: WHATSAPP CLOUD API');
  console.log('=============================================================\n');

  // 1. TESTE DE ASSINATURA E SEGURANÇA
  console.log('1️⃣ Testes de Assinatura Webhook (HMAC-SHA256):');
  const secret = 'segredo_teste_123';
  const body = JSON.stringify({ object: 'whatsapp_business_account' });
  const rawBody = Buffer.from(body, 'utf-8');
  const validSignature = 'sha256=' + crypto.createHmac('sha256', secret).update(rawBody).digest('hex');

  assert(validateSignature(rawBody, validSignature, secret), 'Assinatura HMAC-SHA256 válida aceita');
  assert(!validateSignature(rawBody, 'sha256=invalido', secret), 'Assinatura forjada ou incorreta rejeitada');
  assert(!validateSignature(rawBody, undefined, secret), 'Ausência de assinatura rejeitada');

  // 2. TESTE DE IDEMPOTÊNCIA
  console.log('\n2️⃣ Testes de Idempotência:');
  const msgId = `wamid.test_${Date.now()}`;
  const firstCheck = await idempotencyService.checkAndLock(msgId, '5511999999999', 'text');
  const secondCheck = await idempotencyService.checkAndLock(msgId, '5511999999999', 'text');
  assert(firstCheck === true, 'Primeira chegada da mensagem é aceita');
  assert(secondCheck === false, 'Segunda chegada da mesma mensagem é descartada como duplicata');

  // 3. TESTE DE PAREAMENTO DE NÚMERO AO FIREBASE UID
  console.log('\n3️⃣ Testes de Pareamento e Resolução de UID:');
  const userA_UID = 'uid_empreendedor_alpha';
  const userA_Phone = '5511988881111';
  const userB_UID = 'uid_empreendedor_beta';
  const userB_Phone = '5511977772222';

  const cleanPhoneA = pairingService.normalizePhone(userA_Phone);
  assert(cleanPhoneA === '5511988881111', 'Normalização de telefone remove caracteres especiais');

  const codeA = await pairingService.createPairingCode(userA_UID, userA_Phone);
  assert(codeA.startsWith('MN-'), `Código gerado com sucesso no formato oficial: ${codeA}`);

  // Validação do pareamento pelo webhook
  const confirmResult = await pairingService.confirmPairingWithCode(userA_Phone, codeA);
  assert(confirmResult.success === true, 'Código de pareamento validado com sucesso');
  assert(confirmResult.userId === userA_UID, `Telefone ${userA_Phone} associado ao UID correto (${userA_UID})`);

  // Resolução do UID a partir do telefone
  const mappingA = await pairingService.getMappingByPhone(userA_Phone);
  assert(mappingA?.userId === userA_UID, 'getMappingByPhone resolve o UID correto');

  const mappingUnlinked = await pairingService.getMappingByPhone('5511000000000');
  assert(mappingUnlinked === null, 'Telefone não vinculado retorna null');

  // 4. TESTE DE IA E INTERPRETAÇÃO DE INTENÇÕES
  console.log('\n4️⃣ Testes de Interpretação em Linguagem Natural:');
  const saleIntent = await geminiService.interpretMessage('Registra uma venda pro Carlos, 2 camisas de 50 reais, Pix');
  assert(saleIntent.intent === 'CREATE_SALE', 'Identificou intenção de venda CREATE_SALE');
  assert(saleIntent.payload?.customerName === 'Carlos', 'Extraiu cliente Carlos');
  assert(saleIntent.payload?.paymentMethod === 'Pix', 'Extraiu método Pix');

  const expenseIntent = await geminiService.interpretMessage('Despesa de 250 reais de combustível');
  assert(expenseIntent.intent === 'CREATE_EXPENSE', 'Identificou intenção de despesa CREATE_EXPENSE');
  assert(expenseIntent.payload?.valor === 250, 'Extraiu valor de despesa R$ 250,00');

  const queryIntent = await geminiService.interpretMessage('Quanto vendi hoje?');
  assert(queryIntent.intent === 'QUERY_FINANCIALS', 'Identificou consulta financeira QUERY_FINANCIALS');

  // 5. TESTE DE SESSÃO E CONFIRMAÇÃO (TWO-PHASE)
  console.log('\n5️⃣ Testes de Confirmação e Cancelamento em Duas Fases:');
  assert(sessionService.isAffirmative('Sim'), 'Reconhece "Sim"');
  assert(sessionService.isAffirmative('Pode registrar'), 'Reconhece "Pode registrar"');
  assert(sessionService.isAffirmative('manda ver'), 'Reconhece "manda ver"');
  assert(sessionService.isNegative('Não'), 'Reconhece "Não"');
  assert(sessionService.isNegative('cancela'), 'Reconhece "cancela"');

  await sessionService.setPendingAction(userA_UID, 'CREATE_SALE', { test: 1 }, 'Resumo de teste');
  const session = await sessionService.getPendingAction(userA_UID);
  assert(session?.pendingAction === 'CREATE_SALE', 'Ação pendente salva na sessão do usuário');
  await sessionService.clearPendingAction(userA_UID);
  const cleared = await sessionService.getPendingAction(userA_UID);
  assert(cleared === null, 'Sessão limpa após conclusão da ação');

  // 6. TESTE DE OPERAÇÕES DE NEGÓCIO E ESTOQUE
  console.log('\n6️⃣ Testes de Regras de Negócio e Estoque:');
  const productA = await productService.createProduct(userA_UID, 'biz_a', {
    nome: 'Camisa Polo Azul',
    precoVenda: 89.9,
    custo: 35.0,
    estoqueInicial: 10,
  });
  assert(productA.estoqueAtual === 10, 'Produto A criado com estoque inicial 10');

  const { sale: createdSale } = await salesService.createSale(userA_UID, 'biz_a', {
    customerName: 'Roberto Lima',
    items: [{ productName: 'Camisa Polo Azul', quantity: 2, unitPrice: 89.9 }],
    paymentMethod: 'Pix',
  });
  assert(createdSale.valorTotal === 179.8, 'Venda de 2 unidades calculou valor total correto');

  const updatedProductA = await productService.findProductByName(userA_UID, 'Camisa Polo Azul');
  assert(updatedProductA?.estoqueAtual === 8, 'Baixa automática no estoque de 10 para 8 unidades');

  // Venda a prazo para Roberto para testar contas a receber e dívidas
  await salesService.createSale(userA_UID, 'biz_a', {
    customerName: 'Roberto Lima',
    items: [{ productName: 'Camisa Polo Azul', quantity: 1, unitPrice: 89.9 }],
    paymentMethod: 'A Prazo',
    isPaid: false,
  });

  const { totalDebt } = await financeService.getCustomerDebts(userA_UID, 'Roberto');
  assert(totalDebt === 89.9, 'Consulta de débitos por cliente (Roberto está devendo R$ 89,90)');

  const topSales = await financeService.getTopSales(userA_UID, 5);
  assert(topSales.length === 2 && topSales[0].valorTotal === 179.8, 'Consulta de maiores vendas ordenou corretamente');

  const stockQueryProduct = await productService.findProductByName(userA_UID, 'camisa');
  assert(stockQueryProduct !== null && stockQueryProduct.estoqueAtual === 7, 'Consulta de estoque por nome aproximado encontrou o produto');

  // 7. TESTE DE ISOLAMENTO MULTI-TENANT RIGOROSO (USUÁRIO A vs USUÁRIO B)
  console.log('\n7️⃣ Teste de Isolamento Multi-Tenant Rigoroso:');
  const userBProducts = await productService.listProducts(userB_UID);
  assert(userBProducts.length === 0, 'Usuário B possui 0 produtos (não enxerga produtos do Usuário A)');

  const userBSales = await financeService.getSalesThisMonth(userB_UID);
  assert(userBSales.length === 0, 'Usuário B possui 0 vendas (não enxerga vendas do Usuário A)');

  const userASales = await financeService.getSalesThisMonth(userA_UID);
  assert(userASales.length === 2, 'Usuário A enxerga apenas suas próprias vendas (2 vendas)');

  // 8. TESTE DE GERAÇÃO DE PDF DO COMPROVANTE DE VENDA
  console.log('\n8️⃣ Testes de Geração do Comprovante de Venda em PDF:');
  const receiptBuffer = await pdfService.generateReceiptPdf(
    { name: 'Loja Alpha Fashion', phone: '(11) 98888-1111' },
    createdSale
  );
  assert(Buffer.isBuffer(receiptBuffer) && receiptBuffer.length > 500, `PDF do Comprovante gerado com sucesso (${receiptBuffer.length} bytes)`);

  const reportBuffer = await pdfService.generateFinancialReportPdf(
    { name: 'Loja Alpha Fashion' },
    'RELATÓRIO FINANCEIRO',
    { salesTotal: 5000, salesCount: 20, expensesTotal: 1200, expensesCount: 5, profitEstimate: 3800, averageTicket: 250 },
    [createdSale],
    []
  );
  assert(Buffer.isBuffer(reportBuffer) && reportBuffer.length > 500, `PDF do Relatório gerado com sucesso (${reportBuffer.length} bytes)`);

  console.log('\n=============================================================');
  console.log(`🎉 TESTES CONCLUÍDOS: ${testsPassed} PASSARAM | ${testsFailed} FALHARAM`);
  console.log('=============================================================\n');

  if (testsFailed > 0) {
    process.exit(1);
  }
}

runAllTests().catch((err) => {
  console.error('Erro na execução dos testes:', err);
  process.exit(1);
});
