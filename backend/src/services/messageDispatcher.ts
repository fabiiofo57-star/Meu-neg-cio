import { WhatsAppMessage } from '../types/whatsapp';
import { idempotencyService } from './idempotency';
import { pairingService } from './pairingService';
import { sessionService } from './sessionService';
import { geminiService, StructuredIntent } from './geminiService';
import { mediaService } from './mediaService';
import { salesService } from './businessRules/salesService';
import { expensesService } from './businessRules/expensesService';
import { productService } from './businessRules/productService';
import { customerService } from './businessRules/customerService';
import { financeService } from './businessRules/financeService';
import { pdfService } from './pdfService';
import { whatsappClient } from './whatsappClient';
import { db, firebaseService } from './firebase';
import { BusinessDoc } from '../types/models';
import { config } from '../config/env';

export class MessageDispatcher {
  /**
   * Ponto central de processamento de qualquer mensagem recebida no webhook.
   */
  async dispatchMessage(message: WhatsAppMessage): Promise<void> {
    const senderPhone = message.from;
    const messageId = message.id;

    // 1. Idempotência: rejeita mensagens duplicadas da Meta
    const isNew = await idempotencyService.checkAndLock(messageId, senderPhone, message.type);
    if (!isNew) {
      console.log(`[DISPATCHER] Mensagem ${messageId} já processada anteriormente. Ignorando.`);
      return;
    }

    // Marca como lida na API do WhatsApp
    await whatsappClient.markAsRead(messageId);

    // 2. Extração do conteúdo de texto ou código de pareamento
    const rawText = message.type === 'text' ? message.text?.body || '' : '';

    // Verifica se é código de pareamento explícito (ex: "MN-1234" ou "1234")
    const isPairingCode = /^(MN-)?\d{4}$/i.test(rawText.trim());
    if (isPairingCode) {
      const result = await pairingService.confirmPairingWithCode(senderPhone, rawText.trim());
      if (result.success) {
        await whatsappClient.sendTextMessage(
          senderPhone,
          `🎉 *WhatsApp Conectado com Sucesso!*\n\n` +
            `Sua conta foi associada ao negócio *${result.businessName}*.\n\n` +
            `Agora você pode gerenciar tudo por aqui através de *texto ou áudio*:\n` +
            `• Registrar vendas (ex: _"Venda pro João, 2 camisas de 50 reais, Pix"_)\n` +
            `• Cadastrar despesas (ex: _"Despesa de 150 reais de combustível"_)\n` +
            `• Consultar saldo e vendas (ex: _"Quanto vendi hoje?"_)\n` +
            `• Pedir comprovantes (ex: _"Me manda o comprovante da última venda"_)\n` +
            `• Pedir relatórios (ex: _"Gera meu relatório financeiro deste mês"_)\n\n` +
            `Como posso te ajudar hoje?`
        );
        return;
      } else {
        await whatsappClient.sendTextMessage(
          senderPhone,
          `⚠️ ${result.error || 'Código inválido. Verifique o código gerado no aplicativo Meu Negócio.'}`
        );
        return;
      }
    }

    // 3. Resolução Multi-Tenant: busca o UID vinculado a este telefone
    const mapping = await pairingService.getMappingByPhone(senderPhone);
    if (!mapping || mapping.status !== 'connected') {
      await whatsappClient.sendTextMessage(
        senderPhone,
        `👋 Olá! Este número de WhatsApp ainda não está conectado a nenhuma conta do *Meu Negócio*.\n\n` +
          `Para conectar é muito rápido:\n` +
          `1️⃣ Abra o aplicativo *Meu Negócio* no seu celular\n` +
          `2️⃣ Acesse o menu *Mais* > *WhatsApp*\n` +
          `3️⃣ Digite seu número de telefone e gere um código temporário\n` +
          `4️⃣ Envie o código gerado (ex: *MN-8492*) aqui nesta conversa.\n\n` +
          `Pronto! Seus dados ficarão seguros e sincronizados em tempo real.`
      );
      return;
    }

    const userId = mapping.userId;

    // Obtém informações do negócio do usuário
    let business: Partial<BusinessDoc> = { id: userId, name: 'Meu Negócio', category: 'Geral' };
    if (firebaseService.isReady()) {
      try {
        const bizSnap = await db().collection('users').doc(userId).collection('business').doc('info').get();
        if (bizSnap.exists) {
          business = bizSnap.data() as BusinessDoc;
        }
      } catch {
        // Usa valores padrão caso não consiga ler
      }
    }
    const businessId = business.id || userId;

    // 4. Verificação de Sessão Ativa de Confirmação (Two-Phase Action)
    const pendingSession = await sessionService.getPendingAction(userId);
    if (pendingSession) {
      if (sessionService.isAffirmative(rawText)) {
        await sessionService.clearPendingAction(userId);

        if (pendingSession.pendingAction === 'CREATE_SALE') {
          const { summary } = await salesService.createSale(userId, businessId, pendingSession.payload);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `${summary}\n\n💡 _Dica: Digite "Comprovante" caso queira receber o PDF desta venda._`
          );
          return;
        } else if (pendingSession.pendingAction === 'CREATE_EXPENSE') {
          const { summary } = await expensesService.createExpense(userId, businessId, pendingSession.payload);
          await whatsappClient.sendTextMessage(senderPhone, summary);
          return;
        }
      } else if (sessionService.isNegative(rawText)) {
        await sessionService.clearPendingAction(userId);
        await whatsappClient.sendTextMessage(senderPhone, '❌ Operação cancelada. Nenhum dado foi alterado.');
        return;
      }
    }

    // 5. Interpretação de Mensagem (Áudio ou Texto)
    let intentResult: StructuredIntent;

    if (message.type === 'audio' && message.audio) {
      console.log(`[DISPATCHER] Processando áudio (media_id: ${message.audio.id}) de ${senderPhone}`);
      const media = await mediaService.downloadMedia(message.audio.id);
      if (!media) {
        await whatsappClient.sendTextMessage(
          senderPhone,
          'Desculpe, não consegui baixar o áudio no momento. Por favor, tente enviar por texto ou tente novamente.'
        );
        return;
      }
      intentResult = await geminiService.interpretAudio(media.buffer, media.mimeType);
    } else if (message.type === 'text') {
      intentResult = await geminiService.interpretMessage(rawText);
    } else {
      await whatsappClient.sendTextMessage(
        senderPhone,
        'No momento consigo processar mensagens de *texto* e *áudio*. Envie uma mensagem como "Quanto vendi hoje?" ou registre uma venda!'
      );
      return;
    }

    // 6. Execução da Intenção Estruturada
    await this.executeIntent(userId, businessId, business, senderPhone, intentResult);
  }

  /**
   * Executa a regra de negócio apropriada com base na intenção identificada.
   */
  private async executeIntent(
    userId: string,
    businessId: string,
    business: Partial<BusinessDoc>,
    senderPhone: string,
    intentResult: StructuredIntent
  ): Promise<void> {
    const { intent, payload, needsConfirmation, confirmationSummary } = intentResult;

    switch (intent) {
      case 'CREATE_SALE': {
        if (needsConfirmation && confirmationSummary) {
          await sessionService.setPendingAction(userId, 'CREATE_SALE', payload, confirmationSummary);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📝 *Confirmar Venda:*\n\n${confirmationSummary}\n\n👉 *Posso registrar?* (Responda *"Sim"* para confirmar ou *"Não"* para cancelar)`
          );
        } else {
          const { summary } = await salesService.createSale(userId, businessId, payload);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `${summary}\n\n💡 _Dica: Digite "Comprovante" para gerar o PDF da venda._`
          );
        }
        break;
      }

      case 'CREATE_EXPENSE': {
        if (needsConfirmation && confirmationSummary) {
          await sessionService.setPendingAction(userId, 'CREATE_EXPENSE', payload, confirmationSummary);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📝 *Confirmar Despesa:*\n\n${confirmationSummary}\n\n👉 *Posso registrar?* (Responda *"Sim"* ou *"Não"*)`
          );
        } else {
          const { summary } = await expensesService.createExpense(userId, businessId, payload);
          await whatsappClient.sendTextMessage(senderPhone, summary);
        }
        break;
      }

      case 'CREATE_CUSTOMER': {
        const customer = await customerService.createCustomer(userId, businessId, payload);
        await whatsappClient.sendTextMessage(
          senderPhone,
          `✅ Cliente *${customer.nome}* cadastrado com sucesso!\n` +
            (customer.telefone ? `📞 Telefone: ${customer.telefone}\n` : '') +
            `Os dados já estão disponíveis no seu aplicativo.`
        );
        break;
      }

      case 'CREATE_PRODUCT': {
        const product = await productService.createProduct(userId, businessId, payload);
        await whatsappClient.sendTextMessage(
          senderPhone,
          `✅ Produto *${product.nome}* cadastrado com sucesso!\n` +
            `💰 Preço de Venda: R$ ${product.precoVenda.toFixed(2)}\n` +
            `📦 Estoque Inicial: ${product.estoqueAtual}`
        );
        break;
      }

      case 'UPDATE_STOCK': {
        const product = await productService.findProductByName(userId, payload.productName);
        if (!product) {
          await whatsappClient.sendTextMessage(
            senderPhone,
            `⚠️ Não encontrei nenhum produto chamado *"${payload.productName}"* para alterar o estoque.`
          );
          return;
        }

        const delta = payload.deltaQuantity || 0;
        const res = await productService.updateStock(
          userId,
          businessId,
          product.id,
          delta,
          payload.reason || 'Ajuste via WhatsApp'
        );

        if (res) {
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📦 Estoque atualizado!\n*${res.product.nome}* agora tem *${res.product.estoqueAtual}* unidades em estoque.`
          );
        }
        break;
      }

      case 'QUERY_FINANCIALS': {
        const metric = payload?.metric || 'sales';
        const period = payload?.period || 'month';

        if (metric === 'receivables') {
          const { total, count } = await financeService.getReceivables(userId);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📊 *Contas a Receber:*\n\n` +
              `Você tem *R$ ${total.toFixed(2)}* a receber distribuídos em *${count}* venda(s) a prazo ou pendentes.`
          );
        } else if (metric === 'top_products') {
          const top = await financeService.getTopSellingProducts(userId);
          if (top.length === 0) {
            await whatsappClient.sendTextMessage(senderPhone, 'Não há vendas registradas no período para calcular os mais vendidos.');
          } else {
            const list = top.map((t, idx) => `${idx + 1}. *${t.name}* — ${t.quantity} un (R$ ${t.total.toFixed(2)})`).join('\n');
            await whatsappClient.sendTextMessage(senderPhone, `🏆 *Produtos Mais Vendidos deste Mês:*\n\n${list}`);
          }
        } else if (metric === 'profit') {
          const summary = await financeService.getMonthlyFinancialSummary(userId);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📈 *Lucro Estimado do Mês:*\n\n` +
              `💰 Vendas: R$ ${summary.salesTotal.toFixed(2)}\n` +
              `💸 Despesas: R$ ${summary.expensesTotal.toFixed(2)}\n` +
              `💵 *Lucro Líquido Estimado:* R$ ${summary.profitEstimate.toFixed(2)}`
          );
        } else if (metric === 'expenses') {
          const expenses = await expensesService.getExpensesThisMonth(userId);
          const totalExp = expenses.reduce((acc, e) => acc + e.valor, 0);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `💸 *Despesas deste Mês:*\n\n` +
              `Você registrou *${expenses.length}* despesa(s) totalizando *R$ ${totalExp.toFixed(2)}*.`
          );
        } else if (period === 'today') {
          const salesToday = await financeService.getSalesToday(userId);
          const totalToday = salesToday.reduce((acc, s) => acc + s.valorTotal, 0);
          const expensesToday = await expensesService.getExpensesToday(userId);
          const totalExpToday = expensesToday.reduce((acc, e) => acc + e.valor, 0);

          await whatsappClient.sendTextMessage(
            senderPhone,
            `📅 *Resumo Financeiro de Hoje:*\n\n` +
              `💰 *Vendas:* R$ ${totalToday.toFixed(2)} (${salesToday.length} vendas)\n` +
              `💸 *Despesas:* R$ ${totalExpToday.toFixed(2)} (${expensesToday.length} despesas)\n` +
              `📈 *Saldo do Dia:* R$ ${(totalToday - totalExpToday).toFixed(2)}`
          );
        } else {
          // Mês corrente
          const summary = await financeService.getMonthlyFinancialSummary(userId);
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📅 *Resumo Financeiro deste Mês:*\n\n` +
              `💰 *Total de Vendas:* R$ ${summary.salesTotal.toFixed(2)} (${summary.salesCount} vendas)\n` +
              `💸 *Total de Despesas:* R$ ${summary.expensesTotal.toFixed(2)} (${summary.expensesCount} despesas)\n` +
              `📈 *Lucro Estimado:* R$ ${summary.profitEstimate.toFixed(2)}\n` +
              `🎯 *Ticket Médio:* R$ ${summary.averageTicket.toFixed(2)}`
          );
        }
        break;
      }

      case 'QUERY_DEBT': {
        const customerName = payload?.customerName || '';
        const { totalDebt, sales } = await financeService.getCustomerDebts(userId, customerName);

        if (totalDebt > 0) {
          const list = sales
            .slice(0, 5)
            .map((s) => `• Venda #${s.id.slice(0, 6)}: pendente R$ ${(s.valorRestante || 0).toFixed(2)}`)
            .join('\n');

          await whatsappClient.sendTextMessage(
            senderPhone,
            `👤 *Pendências de ${customerName || 'Cliente'}:*\n\n` +
              `Total em aberto: *R$ ${totalDebt.toFixed(2)}* em *${sales.length}* venda(s) a prazo.\n\n` +
              `${list}`
          );
        } else {
          await whatsappClient.sendTextMessage(
            senderPhone,
            `✅ *${customerName || 'O cliente'}* não possui débitos pendentes no momento. Todas as vendas estão quitadas!`
          );
        }
        break;
      }

      case 'QUERY_TOP_SALES': {
        const topSales = await financeService.getTopSales(userId, 5);
        if (topSales.length === 0) {
          await whatsappClient.sendTextMessage(senderPhone, 'Nenhuma venda encontrada para listar as maiores.');
        } else {
          const list = topSales
            .map(
              (s, idx) =>
                `${idx + 1}. *R$ ${s.valorTotal.toFixed(2)}* — ${s.clienteNome || 'Cliente'} (${s.formaDePagamento})`
            )
            .join('\n');

          await whatsappClient.sendTextMessage(
            senderPhone,
            `🏆 *Suas Maiores Vendas Registradas:*\n\n${list}`
          );
        }
        break;
      }

      case 'QUERY_STOCK': {
        const prodName = payload?.productName || '';
        const product = await productService.findProductByName(userId, prodName);

        if (product) {
          await whatsappClient.sendTextMessage(
            senderPhone,
            `📦 *Estoque do Produto:*\n\n` +
              `• *Produto:* ${product.nome}\n` +
              `• *Quantidade em Estoque:* ${product.estoqueAtual} unidades\n` +
              `• *Preço de Venda:* R$ ${product.precoVenda.toFixed(2)}\n` +
              `• *Estoque Mínimo:* ${product.estoqueMinimo || 0}`
          );
        } else {
          await whatsappClient.sendTextMessage(
            senderPhone,
            `🔍 Não encontrei nenhum produto correspondente a *"${prodName}"* no seu catálogo.`
          );
        }
        break;
      }

      case 'QUERY_PRODUCTS': {
        if (payload?.lowStockOnly) {
          const lowStock = await productService.getLowStockProducts(userId);
          if (lowStock.length === 0) {
            await whatsappClient.sendTextMessage(senderPhone, '✅ Excelente! Nenhum produto está com estoque baixo no momento.');
          } else {
            const list = lowStock.map((p) => `• *${p.nome}*: apenas ${p.estoqueAtual} un (mín: ${p.estoqueMinimo})`).join('\n');
            await whatsappClient.sendTextMessage(senderPhone, `⚠️ *Produtos com Estoque Baixo:*\n\n${list}`);
          }
        } else {
          const products = await productService.listProducts(userId);
          if (products.length === 0) {
            await whatsappClient.sendTextMessage(senderPhone, 'Você ainda não possui produtos cadastrados no Meu Negócio.');
          } else {
            const list = products.slice(0, 15).map((p) => `• *${p.nome}* — R$ ${p.precoVenda.toFixed(2)} (Estoque: ${p.estoqueAtual})`).join('\n');
            await whatsappClient.sendTextMessage(
              senderPhone,
              `📦 *Seus Produtos (${products.length}):*\n\n${list}` +
                (products.length > 15 ? '\n_...e outros no aplicativo._' : '')
            );
          }
        }
        break;
      }

      case 'QUERY_CUSTOMERS': {
        if (payload?.customerName) {
          const customer = await customerService.findCustomerByName(userId, payload.customerName);
          if (customer) {
            await whatsappClient.sendTextMessage(
              senderPhone,
              `👤 *Cliente Encontrado:*\n\n` +
                `*Nome:* ${customer.nome}\n` +
                `*Telefone:* ${customer.telefone || 'Não cadastrado'}\n` +
                (customer.endereco ? `*Endereço:* ${customer.endereco}\n` : '')
            );
          } else {
            await whatsappClient.sendTextMessage(senderPhone, `Não encontrei nenhum cliente chamado "${payload.customerName}".`);
          }
        } else {
          const customers = await customerService.listCustomers(userId);
          if (customers.length === 0) {
            await whatsappClient.sendTextMessage(senderPhone, 'Você ainda não possui clientes cadastrados.');
          } else {
            const list = customers.slice(0, 10).map((c) => `• *${c.nome}* (${c.telefone || 'sem telefone'})`).join('\n');
            await whatsappClient.sendTextMessage(senderPhone, `👥 *Seus Clientes (${customers.length}):*\n\n${list}`);
          }
        }
        break;
      }

      case 'GENERATE_RECEIPT': {
        let sale = null;
        if (payload?.saleId && payload.saleId !== 'last') {
          sale = await salesService.getSaleById(userId, payload.saleId);
        }
        if (!sale) {
          sale = await salesService.getLastSale(userId);
        }

        if (!sale) {
          await whatsappClient.sendTextMessage(senderPhone, 'Nenhuma venda encontrada para gerar comprovante.');
          return;
        }

        const customer = sale.clienteId ? await customerService.findCustomerByName(userId, sale.clienteNome || '') : null;
        const pdfBuffer = await pdfService.generateReceiptPdf(business, sale, customer);
        const { filename } = await pdfService.saveTempFile(pdfBuffer, 'comprovante');

        // Envia o comprovante via WhatsApp usando upload oficial de buffer ou URL pública
        const filenameLabel = `Comprovante_Venda_${sale.id.slice(0, 8).toUpperCase()}.pdf`;
        const publicUrl = config.server.publicUrl ? `${config.server.publicUrl}/temp_documents/${filename}` : undefined;

        await whatsappClient.sendDocument(senderPhone, {
          fileBuffer: pdfBuffer,
          documentUrl: publicUrl,
          filename: filenameLabel,
          caption: `📄 Comprovante da Venda #${sale.id.slice(0, 8).toUpperCase()} - ${sale.clienteNome || 'Cliente Balcão'}`,
        });
        break;
      }

      case 'GENERATE_REPORT': {
        const stats = await financeService.getMonthlyFinancialSummary(userId);
        const sales = await financeService.getSalesThisMonth(userId);
        const expenses = await expensesService.getExpensesThisMonth(userId);

        const pdfBuffer = await pdfService.generateFinancialReportPdf(
          business,
          'RELATÓRIO FINANCEIRO DO MÊS',
          stats,
          sales,
          expenses
        );
        const { filename } = await pdfService.saveTempFile(pdfBuffer, 'relatorio');
        const filenameLabel = `Relatorio_Financeiro_${new Date().toISOString().slice(0, 7)}.pdf`;
        const publicUrl = config.server.publicUrl ? `${config.server.publicUrl}/temp_documents/${filename}` : undefined;

        await whatsappClient.sendDocument(senderPhone, {
          fileBuffer: pdfBuffer,
          documentUrl: publicUrl,
          filename: filenameLabel,
          caption: `📊 Relatório Financeiro Consolidado - ${business.name || 'Meu Negócio'}`,
        });
        break;
      }

      case 'GREETING':
      case 'HELP': {
        await whatsappClient.sendTextMessage(
          senderPhone,
          `👋 Olá! Estou pronto para ajudar no gerenciamento do *${business.name || 'Meu Negócio'}*.\n\n` +
            `Você pode mandar mensagens de *texto* ou *áudio* como:\n\n` +
            `🛍️ *Vendas:*\n` +
            `_"Venda pro Carlos: 3 camisas a 50 reais, Pix"_\n` +
            `_"João comprou 2 peças e vai pagar sexta"_\n\n` +
            `💸 *Despesas:*\n` +
            `_"Registra uma despesa de 200 reais de combustível"_\n\n` +
            `📊 *Consultas e Relatórios:*\n` +
            `_"Quanto vendi hoje?"_\n` +
            `_"Quanto tenho para receber?"_\n` +
            `_"Me manda o comprovante da última venda"_\n` +
            `_"Gera meu relatório financeiro deste mês"_\n\n` +
            `📦 *Estoque e Clientes:*\n` +
            `_"Quais produtos estão acabando?"_\n` +
            `_"Cadastre Maria, telefone 11988887777"_`
        );
        break;
      }

      default: {
        await whatsappClient.sendTextMessage(
          senderPhone,
          `Não consegui entender completamente o comando.\n\n` +
            `Você pode registrar uma venda dizendo, por exemplo:\n` +
            `_"Venda para Maria, 1 produto X por 80 reais à vista"_\n\n` +
            `Ou digite *"Ajuda"* para ver os comandos disponíveis!`
        );
        break;
      }
    }
  }
}

export const messageDispatcher = new MessageDispatcher();
