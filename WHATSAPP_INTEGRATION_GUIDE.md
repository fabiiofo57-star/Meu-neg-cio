# 📲 Guia Operacional Oficial — WhatsApp Business Platform ("Meu Negócio")

Este documento é o guia definitivo para ativação e operação da integração oficial do **Meu Negócio** com a **WhatsApp Business Platform / WhatsApp Cloud API** da Meta e o motor de IA **Google Gemini**.

---

## 🏛️ 1. Arquitetura da Solução

O WhatsApp funciona como uma **segunda interface completa de operação** para o sistema "Meu Negócio", compartilhando rigorosamente o mesmo banco de dados **Firestore** e o mesmo identificador principal: o **Firebase UID**.

```
[ Usuário / WhatsApp ]
          │
          ▼
[ Meta Cloud API (WhatsApp Business Platform) ]
          │ Webhook (HTTPS POST /webhook com HMAC-SHA256)
          ▼
[ Backend / Servidor Seguro (Node.js + Express + TypeScript) ]
   ├── Validação Criptográfica (X-Hub-Signature-256)
   ├── Camada de Idempotência (Atômica - Deduplicação de Mensagens)
   ├── Motor de IA (Google Gemini 3.6 Flash - Texto e Áudio)
   ├── Resolução Multi-Tenant de Identidade (Telefone -> Firebase UID)
   ├── Gerenciador de Sessão (Confirmação em Duas Fases / Two-Phase)
   ├── Regras de Negócio (Vendas, Despesas, Estoque, Clientes, Finanças)
   └── Gerador & Uploader de PDF (Comprovante de Venda POS e Relatórios)
          │
          ▼
[ Cloud Firestore (users/{uid}/...) ] ◄───► [ Aplicativo Android "Meu Negócio" ]
(Multi-tenant 100% isolado por UID)        (Sincronização em tempo real via Room + Firestore)
```

---

## 🔑 2. Variáveis de Ambiente e Credenciais da Meta

As credenciais devem ser configuradas exclusivamente no ambiente do servidor backend (arquivo `.env` ou variáveis de ambiente do Cloud Run / App Hosting). **Nenhum segredo da Meta reside no aplicativo Android.**

| Variável de Ambiente | Descrição | Onde Obter no Painel da Meta |
| :--- | :--- | :--- |
| `META_APP_ID` | Identificador do aplicativo no Meta Developers | **Painel Principal** do App na Meta |
| `META_APP_SECRET` | Chave secreta usada na validação HMAC-SHA256 | **Configurações do App > Básico** > *Chave Secreta do Aplicativo* |
| `META_ACCESS_TOKEN` | Token de Acesso Permanente (System User) com permissão `whatsapp_business_messaging` | **Meta Business Suite > Usuários do Sistema** |
| `META_VERIFY_TOKEN` | Token seguro definido por você para validar o webhook | Criado livremente por você e repetido na Meta |
| `META_PHONE_NUMBER_ID` | ID do número de telefone oficial do WhatsApp | **WhatsApp > Início Rápido / Configuração da API** |
| `META_WABA_ID` | ID da conta do WhatsApp Business | **WhatsApp > Configuração da API** > *ID da conta do WhatsApp Business* |
| `META_API_VERSION` | Versão da Meta Graph API (padrão: `v21.0`) | Padrão recomendado: `v21.0` |
| `PUBLIC_BACKEND_URL` | URL pública HTTPS do servidor backend | URL gerada após deploy (ex: Google Cloud Run) |
| `FIREBASE_PROJECT_ID` | ID do projeto Firebase | Configurações do Projeto no Console Firebase |
| `GEMINI_API_KEY` | Chave de API do Google Gemini | Google AI Studio (`ai.google.dev`) |
| `GEMINI_MODEL` | Modelo de IA para interpretação de texto e áudio | Padrão: `gemini-3.6-flash` |

---

## ⚙️ 3. Passo a Passo para Registro no Meta Developers

1. **Acesse o Portal**: Entre em [developers.facebook.com](https://developers.facebook.com/) e acesse seu aplicativo do tipo *Empresa*.
2. **Adicione o Produto WhatsApp**: No menu lateral, adicione e configure o produto **WhatsApp**.
3. **Configure o Webhook**:
   - URL de Retorno de Chamada: `https://SEU_DOMINIO/webhook`
   - Token de Verificação: O mesmo valor configurado em `META_VERIFY_TOKEN`.
   - Clique em **"Verificar e Salvar"** (o backend responde imediatamente com o desafio recebido da Meta).
4. **Assine os Campos do Webhook**:
   - Em *Campos do Webhook*, clique em **Gerenciar**.
   - Marque o campo **`messages`** como Ativo.
5. **Associe o Número Oficial**:
   - Em *WhatsApp > Configuração da API*, adicione e verifique seu número de telefone oficial via SMS ou ligação telefônica.

---

## 🔗 4. Como Conectar sua Conta do "Meu Negócio" (Pareamento)

Para associar seu número de WhatsApp ao seu negócio cadastrado no aplicativo Android:

1. Abra o aplicativo **Meu Negócio** no Android.
2. Acesse o menu **Mais > WhatsApp**.
3. Digite seu número de telefone (com DDD) e toque em **"Gerar Código de Conexão"**.
4. O app gera um código temporário de 4 dígitos (ex: `MN-8492`) gravado com segurança sob `users/{uid}/whatsapp/info`.
5. Abra o WhatsApp no seu celular e envie esse código (`MN-8492`) para o número oficial do seu negócio.
6. O backend valida o código, cria o vínculo seguro em `/whatsapp_mappings/{telefone}` e envia uma mensagem de confirmação instantânea!

---

## 💬 5. Comandos Operacionais Reais Suportados (Texto e Áudio)

Você pode enviar tanto mensagens escritas quanto mensagens de voz. O motor Gemini transcreve e interpreta a intenção do áudio mantendo as mesmas regras de negócio:

### 🛍️ Vendas
- *"Registra uma venda para João, 3 camisas de 50 reais, Pix."*
- *"Venda pro Carlos: 2 peças de 80 reais, dinheiro"*
- *"Maria comprou 1 calça de 150 reais e vai pagar na sexta"* (Registra venda a prazo com conta a receber)

### 👥 Clientes
- *"Cadastre Carlos como cliente."*
- *"Cadastrar Maria, telefone 11988887777, endereço Rua das Flores 123"*
- *"Qual o telefone do Carlos?"*

### 🏷️ Produtos
- *"Cadastre camisa azul por 80 reais."*
- *"Cadastre calça jeans por 120 reais com estoque inicial de 15 unidades."*

### 📦 Estoque
- *"Tenho quantas camisas no estoque?"*
- *"Quais produtos estão acabando?"* (Lista produtos que atingiram o estoque mínimo)
- *"Chegaram 10 unidades da camisa azul"* (Dá entrada de estoque automática)
- *"Lista meus produtos"*

### 💸 Despesas
- *"Registra uma despesa de 300 reais de combustível."*
- *"Paguei 180 reais de conta de luz hoje"*
- *"Despesa de 450 reais de aluguel"*

### 📊 Financeiro e Consultas
- *"Quanto vendi este mês?"*
- *"Quanto vendi hoje?"*
- *"Quanto gastei este mês?"*
- *"Quanto tenho para receber?"*
- *"Qual meu lucro estimado?"*
- *"Quanto o João está devendo?"*
- *"Quais foram minhas maiores vendas?"*

### 📄 Comprovantes e Relatórios em PDF
- *"Me manda o comprovante da última venda."*
  - Gera o cupom fiscal/comprovante POS formatado em PDF e envia diretamente no WhatsApp pelo upload oficial de mídia da Meta!
- *"Gera meu relatório financeiro deste mês."*
  - Gera o balanço financeiro completo em PDF (Total de Vendas, Despesas, Lucro Estimado, Ticket Médio e Demonstrativo) e envia diretamente para você no WhatsApp.

---

## 🛡️ 6. Segurança e Fluxo de Confirmação em Duas Fases

Para operações sensíveis de escrita financeira (vendas de alto valor e despesas):
1. O backend formata um resumo com os valores identificados.
2. Pergunta: `👉 Posso registrar? (Responda "Sim" para confirmar ou "Não" para cancelar)`
3. Apenas após a resposta afirmativa (`"Sim"`, `"Pode"`, `"Confirma"`), a transação é gravada no Firestore e o estoque é atualizado.
4. Respostas negativas (`"Não"`, `"Cancela"`) abortam a operação sem alterar nenhum dado.

---

## 🧪 7. Verificação dos Testes de Integração

O backend conta com uma suíte de testes automatizada que valida todos os cenários (assinatura HMAC, idempotência, pareamento, Gemini, regras de negócio, multi-tenant e geração de PDFs).

Para executar os testes a qualquer momento:
```bash
npm --prefix backend run build
node backend/dist/testEndToEnd.js
```
*Status: 35 testes automatizados aprovados com 100% de sucesso.*
