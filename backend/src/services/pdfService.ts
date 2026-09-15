import PDFDocument from 'pdfkit';
import * as fs from 'fs';
import * as path from 'path';
import { randomUUID } from 'crypto';
import { BusinessDoc, SaleDoc, CustomerDoc, ProductDoc, ExpenseDoc } from '../types/models';

export class PdfService {
  private tempDir: string;

  constructor() {
    this.tempDir = path.join(process.cwd(), 'temp_documents');
    if (!fs.existsSync(this.tempDir)) {
      fs.mkdirSync(this.tempDir, { recursive: true });
    }
  }

  /**
   * Gera o PDF oficial "COMPROVANTE DE VENDA" com layout POS profissional de 80mm.
   * Não utiliza termos fiscais (não é NF/cupom fiscal).
   */
  async generateReceiptPdf(
    business: Partial<BusinessDoc>,
    sale: SaleDoc,
    customer?: CustomerDoc | null
  ): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const doc = new PDFDocument({
        size: [400, 750],
        margin: 20,
      });

      const buffers: Buffer[] = [];
      doc.on('data', (chunk) => buffers.push(chunk));
      doc.on('end', () => resolve(Buffer.concat(buffers)));
      doc.on('error', reject);

      // --- CABEÇALHO DO NEGÓCIO ---
      doc.fillColor('#0F172A').fontSize(16).font('Helvetica-Bold').text(business.name || 'Meu Negócio', { align: 'center' });

      if (business.category) {
        doc.fillColor('#64748B').fontSize(9).font('Helvetica').text(business.category, { align: 'center' });
      }

      if (business.phone || business.city) {
        const contactLine = [business.phone, business.city].filter(Boolean).join(' • ');
        doc.fillColor('#64748B').fontSize(9).font('Helvetica').text(contactLine, { align: 'center' });
      }

      if (business.cnpj) {
        doc.fillColor('#94A3B8').fontSize(8).font('Helvetica').text(`CNPJ: ${business.cnpj}`, { align: 'center' });
      }

      doc.moveDown(0.8);
      this.drawDottedLine(doc);
      doc.moveDown(0.8);

      // --- TÍTULO DO DOCUMENTO ---
      doc.fillColor('#059669').fontSize(12).font('Helvetica-Bold').text('COMPROVANTE DE VENDA', { align: 'center' });
      doc.fillColor('#64748B').fontSize(8).font('Helvetica').text(`Nº #${sale.id.slice(0, 8).toUpperCase()}`, { align: 'center' });

      doc.moveDown(0.5);

      // --- DATA E HORA ---
      const dateStr = new Date(sale.data).toLocaleDateString('pt-BR');
      doc.fillColor('#334155').fontSize(9).font('Helvetica').text(`Data: ${dateStr} às ${sale.hora || '--:--'}`);

      // --- CLIENTE ---
      const custName = sale.clienteNome || customer?.nome || 'Cliente Balcão';
      doc.fillColor('#334155').fontSize(9).font('Helvetica-Bold').text(`Cliente: ${custName}`);
      if (customer?.telefone) {
        doc.fillColor('#64748B').fontSize(8).font('Helvetica').text(`Telefone: ${customer.telefone}`);
      }

      doc.moveDown(0.5);
      this.drawDottedLine(doc);
      doc.moveDown(0.5);

      // --- TABELA DE ITENS ---
      doc.fillColor('#0F172A').fontSize(9).font('Helvetica-Bold');
      doc.text('QTD  PRODUTO', 20, doc.y, { continued: true });
      doc.text('TOTAL', { align: 'right' });
      doc.moveDown(0.3);

      for (const item of sale.itens || []) {
        doc.fillColor('#334155').fontSize(9).font('Helvetica');
        const itemLeft = `${item.quantidade}x  ${item.productName}`;
        const itemRight = `R$ ${item.subtotal.toFixed(2)}`;
        doc.text(itemLeft, 20, doc.y, { continued: true });
        doc.text(itemRight, { align: 'right' });
      }

      doc.moveDown(0.5);
      this.drawDottedLine(doc);
      doc.moveDown(0.5);

      // --- TOTAIS E PAGAMENTO ---
      if (sale.desconto && sale.desconto > 0) {
        doc.fillColor('#64748B').fontSize(9).font('Helvetica').text('Subtotal:', 20, doc.y, { continued: true });
        doc.text(`R$ ${(sale.valorTotal + sale.desconto).toFixed(2)}`, { align: 'right' });

        doc.fillColor('#DC2626').fontSize(9).font('Helvetica').text('Desconto:', 20, doc.y, { continued: true });
        doc.text(`- R$ ${sale.desconto.toFixed(2)}`, { align: 'right' });
      }

      doc.fillColor('#0F172A').fontSize(11).font('Helvetica-Bold').text('TOTAL:', 20, doc.y, { continued: true });
      doc.text(`R$ ${sale.valorTotal.toFixed(2)}`, { align: 'right' });

      doc.moveDown(0.4);
      doc.fillColor('#334155').fontSize(9).font('Helvetica').text('Forma de Pagamento:', 20, doc.y, { continued: true });
      doc.text(sale.formaDePagamento || 'Dinheiro', { align: 'right' });

      doc.fillColor('#334155').fontSize(9).font('Helvetica').text('Status:', 20, doc.y, { continued: true });
      const statusLabel = sale.statusDoPagamento === 'pago' ? 'Pago' : 'Pendente / A Prazo';
      doc.fillColor(sale.statusDoPagamento === 'pago' ? '#059669' : '#D97706').font('Helvetica-Bold').text(statusLabel, { align: 'right' });

      if (sale.valorRestante > 0) {
        doc.fillColor('#DC2626').fontSize(9).font('Helvetica-Bold').text('Valor Restante:', 20, doc.y, { continued: true });
        doc.text(`R$ ${sale.valorRestante.toFixed(2)}`, { align: 'right' });
      }

      if (sale.dataDeVencimento && sale.dataDeVencimento > 0) {
        const dueStr = new Date(sale.dataDeVencimento).toLocaleDateString('pt-BR');
        doc.fillColor('#64748B').fontSize(8).font('Helvetica').text(`Vencimento: ${dueStr}`, { align: 'right' });
      }

      if (sale.observacao) {
        doc.moveDown(0.5);
        doc.fillColor('#64748B').fontSize(8).font('Helvetica-Oblique').text(`Obs: ${sale.observacao}`, { align: 'left' });
      }

      // --- RODAPÉ ---
      doc.moveDown(1.5);
      this.drawDottedLine(doc);
      doc.moveDown(0.8);
      doc.fillColor('#059669').fontSize(9).font('Helvetica-Bold').text('Obrigado pela preferência!', { align: 'center' });
      doc.fillColor('#94A3B8').fontSize(7).font('Helvetica').text('Este documento é um comprovante de venda não fiscal emitido pelo Meu Negócio.', { align: 'center' });

      doc.end();
    });
  }

  /**
   * Gera PDF "RELATÓRIO FINANCEIRO".
   */
  async generateFinancialReportPdf(
    business: Partial<BusinessDoc>,
    title: string,
    stats: {
      salesTotal: number;
      salesCount: number;
      expensesTotal: number;
      expensesCount: number;
      profitEstimate: number;
      averageTicket: number;
    },
    sales: SaleDoc[],
    expenses: ExpenseDoc[]
  ): Promise<Buffer> {
    return new Promise((resolve, reject) => {
      const doc = new PDFDocument({ size: 'A4', margin: 40 });
      const buffers: Buffer[] = [];
      doc.on('data', (chunk) => buffers.push(chunk));
      doc.on('end', () => resolve(Buffer.concat(buffers)));
      doc.on('error', reject);

      // Cabeçalho
      doc.fillColor('#0F172A').fontSize(18).font('Helvetica-Bold').text(business.name || 'Meu Negócio');
      doc.fillColor('#059669').fontSize(14).font('Helvetica-Bold').text(title);
      doc.fillColor('#64748B').fontSize(9).font('Helvetica').text(`Gerado em ${new Date().toLocaleDateString('pt-BR')} via WhatsApp`);
      doc.moveDown(1);

      // Resumo em Caixas
      doc.rect(40, doc.y, 515, 70).fillAndStroke('#F8FAFC', '#E2E8F0');
      const boxY = doc.y + 12;

      doc.fillColor('#059669').fontSize(12).font('Helvetica-Bold').text(`Total de Vendas: R$ ${stats.salesTotal.toFixed(2)} (${stats.salesCount} vendas)`, 55, boxY);
      doc.fillColor('#DC2626').fontSize(12).font('Helvetica-Bold').text(`Total Despesas: R$ ${stats.expensesTotal.toFixed(2)} (${stats.expensesCount} despesas)`, 55, boxY + 18);
      doc.fillColor('#0284C7').fontSize(12).font('Helvetica-Bold').text(`Lucro Estimado: R$ ${stats.profitEstimate.toFixed(2)} | Ticket Médio: R$ ${stats.averageTicket.toFixed(2)}`, 55, boxY + 36);

      doc.y = boxY + 70;
      doc.moveDown(1);

      // Lista de últimas vendas
      doc.fillColor('#0F172A').fontSize(12).font('Helvetica-Bold').text('Vendas do Período');
      doc.moveDown(0.5);

      for (const s of sales.slice(0, 15)) {
        const dStr = new Date(s.data).toLocaleDateString('pt-BR');
        doc.fillColor('#334155').fontSize(9).font('Helvetica').text(`${dStr} • ${s.clienteNome || 'Cliente Balcão'} • ${s.formaDePagamento}`, 40, doc.y, { continued: true });
        doc.text(`R$ ${s.valorTotal.toFixed(2)}`, { align: 'right' });
      }

      doc.end();
    });
  }

  /**
   * Salva o buffer em disco temporário para ser baixado pela API do WhatsApp.
   */
  async saveTempFile(buffer: Buffer, prefix = 'doc'): Promise<{ filePath: string; filename: string }> {
    const filename = `${prefix}_${randomUUID().slice(0, 8)}.pdf`;
    const filePath = path.join(this.tempDir, filename);
    await fs.promises.writeFile(filePath, buffer);
    return { filePath, filename };
  }

  private drawDottedLine(doc: typeof PDFDocument): void {
    const currentY = doc.y;
    doc
      .save()
      .strokeColor('#CBD5E1')
      .lineWidth(0.8)
      .dash(3, { space: 3 })
      .moveTo(20, currentY)
      .lineTo(380, currentY)
      .stroke()
      .restore();
  }
}

export const pdfService = new PdfService();
