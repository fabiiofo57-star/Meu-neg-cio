package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.SalePaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class VoiceActionType {
    SALE,
    PRODUCT,
    EXPENSE,
    ADVICE,
    UNKNOWN
}

enum class VoicePaymentMethod(val label: String) {
    DINHEIRO("Dinheiro"),
    PIX("Pix"),
    CARTAO("Cartão"),
    A_PRAZO("A Prazo")
}

data class GeminiVoiceSaleItem(
    val productName: String,
    val quantity: Double = 1.0,
    val unit: String = "un",
    val unitPrice: Double = 0.0
)

data class GeminiVoiceAction(
    val actionType: VoiceActionType,
    val summary: String,
    val rawSpokenText: String = "",
    // Respostas gerais, dúvidas e ideias
    val adviceAnswer: String? = null,
    val spokenResponse: String? = null,
    // Venda
    val customerName: String? = null,
    val items: List<GeminiVoiceSaleItem> = emptyList(),
    val paymentMethod: VoicePaymentMethod = VoicePaymentMethod.DINHEIRO,
    val paymentStatus: SalePaymentStatus = SalePaymentStatus.PAGO,
    val totalAmount: Double? = null,
    val paidAmount: Double? = null,
    val daysUntilDue: Int? = null,
    // Produto
    val productName: String? = null,
    val productPrice: Double? = null,
    val productCostPrice: Double? = null,
    val productStock: Double? = null,
    val productCategory: String? = null,
    // Despesa
    val expenseDescription: String? = null,
    val expenseAmount: Double? = null,
    val expenseCategory: String? = null,
    val expensePaymentMethod: VoicePaymentMethod = VoicePaymentMethod.DINHEIRO
)

class GeminiVoiceAssistant(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Model per system skill rules for basic text tasks: gemini-3.5-flash
    private val modelName = "gemini-3.5-flash"

    suspend fun parseVoiceInput(spokenText: String): GeminiVoiceAction = withContext(Dispatchers.IO) {
        val trimmed = spokenText.trim()
        if (trimmed.isBlank()) {
            return@withContext GeminiVoiceAction(
                actionType = VoiceActionType.UNKNOWN,
                summary = "Nenhuma fala detectada.",
                rawSpokenText = spokenText
            )
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }

        val isValidKey = apiKey.isNotBlank() &&
                !apiKey.contains("MY_GEMINI_API_KEY", ignoreCase = true) &&
                !apiKey.contains("PLACEHOLDER", ignoreCase = true)

        if (isValidKey) {
            try {
                val actionFromGemini = callGeminiApi(trimmed, apiKey)
                if (actionFromGemini != null && actionFromGemini.actionType != VoiceActionType.UNKNOWN) {
                    return@withContext actionFromGemini.copy(rawSpokenText = trimmed)
                }
            } catch (e: Exception) {
                Log.w("GeminiVoiceAssistant", "Gemini API call failed, falling back to local NLP parser", e)
            }
        }

        // Fallback robusto e instantâneo em Português para garantir que funcione em qualquer situação
        parseWithLocalNlp(trimmed)
    }

    private fun callGeminiApi(spokenText: String, apiKey: String): GeminiVoiceAction? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val prompt = """
            Você é 'Sua assistente pessoal de negócios', uma IA especialista em comércio, vendas, finanças e estratégias para micro e pequenos empreendedores no Brasil.
            O usuário enviou uma mensagem por voz ou texto: "$spokenText"

            CLASSIFICAÇÃO DA INTENÇÃO:
            1. 'ADVICE': Se for uma PERGUNTA, PEDIDO DE IDEIAS, DICAS DE VENDAS, MARKETING, ESTRATÉGIAS, GESTÃO OU CONVERSA GERAL.
               - Exemplos: "como atrair clientes?", "me dá 3 ideias de promoções", "como calcular meu lucro?", "bom dia", "como evitar fiado?".
               - Forneça em 'adviceAnswer' uma resposta rica, prática, estruturada em tópicos curtos e muito útil para o comerciante.
               - Forneça em 'spokenResponse' uma fala natural, simpática e objetiva de 1 a 3 frases para ser falada em voz alta.
               - Defina 'actionType': "ADVICE".
            2. 'SALE': Se for um registro de venda de produto/serviço.
               - Separe rigorosamente: 'customerName' (apenas nome do cliente), 'productName' (apenas nome do item), 'items', 'totalAmount', 'paymentMethod'.
               - Em 'spokenResponse', gere uma frase como: "Entendido! Venda de [qtd] [produto] para [cliente] no valor de [valor]. Deseja confirmar?".
            3. 'PRODUCT': Se for cadastro de produto no estoque.
               - Em 'spokenResponse', confirme o cadastro do item e preço.
            4. 'EXPENSE': Se for lançamento de despesa ou gasto.
               - Em 'spokenResponse', confirme a despesa registrada.

            REGRA CRÍTICA DE SEPARAÇÃO PARA VENDAS:
            - 'productName': NUNCA misture o nome do cliente, nem peso (ex: "3 kg"), nem preço no nome do produto.
            - 'customerName': Isole nomes e títulos como "Seu Zé", "Dona Maria", "Carlos".

            Responda APENAS em JSON estrito sem markdown:
            {
              "actionType": "SALE" | "PRODUCT" | "EXPENSE" | "ADVICE" | "UNKNOWN",
              "summary": "Resumo amigável",
              "spokenResponse": "Frase natural e agradável para a assistente falar em voz alta ao usuário",
              "adviceAnswer": "Resposta completa com ideias práticas, conselhos ou explicação direta quando for ADVICE",
              "customerName": "Nome do cliente limpo ou null",
              "items": [
                { "productName": "Apenas o nome da mercadoria", "quantity": 1.0, "unit": "un", "unitPrice": 0.0 }
              ],
              "paymentMethod": "DINHEIRO" | "PIX" | "CARTAO" | "A_PRAZO",
              "paymentStatus": "PAGO" | "PENDENTE",
              "totalAmount": 0.0,
              "paidAmount": 0.0,
              "daysUntilDue": 30,
              "productName": "Nome do produto ou null",
              "productPrice": 0.0,
              "productCostPrice": 0.0,
              "productStock": 0.0,
              "productCategory": "Categoria ou null",
              "expenseDescription": "Descrição da despesa ou null",
              "expenseAmount": 0.0,
              "expenseCategory": "Categoria ou null",
              "expensePaymentMethod": "DINHEIRO" | "PIX" | "CARTAO"
            }
        """.trimIndent()

        val requestBodyJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", partsArray)
                }
                put(contentObj)
            }
            put("contents", contentsArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("GeminiVoiceAssistant", "Gemini API HTTP ${response.code}: ${response.message}")
                return null
            }

            val bodyString = response.body?.string() ?: return null
            val root = JSONObject(bodyString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            var textResult = parts.getJSONObject(0).optString("text", "")
            textResult = textResult.replace("```json", "").replace("```", "").trim()

            val json = JSONObject(textResult)
            return parseActionJson(json, spokenText)
        }
    }

    private fun parseActionJson(json: JSONObject, rawSpoken: String): GeminiVoiceAction {
        val actionTypeStr = json.optString("actionType", "UNKNOWN").uppercase()
        val actionType = try {
            VoiceActionType.valueOf(actionTypeStr)
        } catch (_: Exception) {
            VoiceActionType.UNKNOWN
        }

        val summary = json.optString("summary", "Ação identificada pela IA")
        val customerName = json.optString("customerName").takeIf { it.isNotBlank() && it != "null" }

        val itemsList = mutableListOf<GeminiVoiceSaleItem>()
        val itemsArray = json.optJSONArray("items")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                var pName = itemObj.optString("productName", "Produto").trim()
                // Garantir que o nome do cliente não fique grudado no nome do produto
                if (customerName != null && pName.contains(customerName, ignoreCase = true)) {
                    pName = pName.replace(customerName, "", ignoreCase = true).trim()
                }
                // Remover resíduos comuns de títulos ou preposições se houver
                pName = pName.replace(Regex("(?i)\\b(seu|dona|dr|dra|sr|sra|tio|tia)\\s+[a-zA-ZáàâãéèêíïóôõöúçñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑ]+"), "").trim()
                pName = pName.replace(Regex("(?i)\\b(\\d+([.,]\\d+)?)\\s*(kg|quilos?|unidades?|un|reais|real|r\\$)?\\b"), "").trim()
                if (pName.isBlank()) pName = "Mercadoria"

                val qty = itemObj.optDouble("quantity", 1.0)
                val unit = itemObj.optString("unit", "un")
                val price = itemObj.optDouble("unitPrice", 0.0)
                itemsList.add(GeminiVoiceSaleItem(productName = pName.replaceFirstChar { it.uppercase() }, quantity = qty, unit = unit, unitPrice = price))
            }
        }

        val payMethodStr = json.optString("paymentMethod", "DINHEIRO").uppercase()
        val paymentMethod = when {
            payMethodStr.contains("PIX") -> VoicePaymentMethod.PIX
            payMethodStr.contains("CART") -> VoicePaymentMethod.CARTAO
            payMethodStr.contains("PRAZO") || payMethodStr.contains("FIADO") -> VoicePaymentMethod.A_PRAZO
            else -> VoicePaymentMethod.DINHEIRO
        }

        val payStatusStr = json.optString("paymentStatus", "PAGO").uppercase()
        val paymentStatus = if (payStatusStr.contains("PENDENTE") || paymentMethod == VoicePaymentMethod.A_PRAZO) {
            SalePaymentStatus.PENDENTE
        } else {
            SalePaymentStatus.PAGO
        }

        val totalAmount = if (json.has("totalAmount") && !json.isNull("totalAmount")) json.optDouble("totalAmount") else null
        val paidAmount = if (json.has("paidAmount") && !json.isNull("paidAmount")) json.optDouble("paidAmount") else null
        val daysUntilDue = if (json.has("daysUntilDue") && !json.isNull("daysUntilDue")) json.optInt("daysUntilDue") else null

        // Produto
        val productName = json.optString("productName").takeIf { it.isNotBlank() && it != "null" }
        val productPrice = if (json.has("productPrice") && !json.isNull("productPrice")) json.optDouble("productPrice") else null
        val productCostPrice = if (json.has("productCostPrice") && !json.isNull("productCostPrice")) json.optDouble("productCostPrice") else null
        val productStock = if (json.has("productStock") && !json.isNull("productStock")) json.optDouble("productStock") else null
        val productCategory = json.optString("productCategory").takeIf { it.isNotBlank() && it != "null" }

        // Despesa
        val expenseDesc = json.optString("expenseDescription").takeIf { it.isNotBlank() && it != "null" }
        val expenseAmount = if (json.has("expenseAmount") && !json.isNull("expenseAmount")) json.optDouble("expenseAmount") else null
        val expenseCat = json.optString("expenseCategory").takeIf { it.isNotBlank() && it != "null" }

        val adviceAnswer = json.optString("adviceAnswer").takeIf { it.isNotBlank() && it != "null" }
        val spokenResponse = json.optString("spokenResponse").takeIf { it.isNotBlank() && it != "null" }

        return GeminiVoiceAction(
            actionType = actionType,
            summary = summary,
            rawSpokenText = rawSpoken,
            adviceAnswer = adviceAnswer,
            spokenResponse = spokenResponse ?: summary,
            customerName = customerName,
            items = itemsList,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            totalAmount = totalAmount,
            paidAmount = paidAmount,
            daysUntilDue = daysUntilDue,
            productName = productName,
            productPrice = productPrice,
            productCostPrice = productCostPrice,
            productStock = productStock,
            productCategory = productCategory,
            expenseDescription = expenseDesc,
            expenseAmount = expenseAmount,
            expenseCategory = expenseCat
        )
    }

    /**
     * Parser inteligente em português quando a chave não estiver configurada ou estiver offline.
     */
    fun parseWithLocalNlp(text: String): GeminiVoiceAction {
        val lower = text.lowercase()

        // 0. Verificar se é Pergunta, Pedido de Ideias, Dica ou Gestão (ADVICE)
        val isAdviceOrQuestion = lower.contains("ideia") || lower.contains("ideias") ||
                lower.contains("dica") || lower.contains("dicas") ||
                lower.contains("como") || lower.contains("ajuda") ||
                lower.contains("atrair") || lower.contains("vender mais") ||
                lower.contains("aumentar") || lower.contains("melhorar") ||
                lower.contains("promoção") || lower.contains("promocao") ||
                lower.contains("lucro") || lower.contains("preço") || lower.contains("preco") ||
                lower.contains("fiado") || lower.contains("conselho") || lower.contains("sugest") ||
                lower.contains("estratégia") || lower.contains("estrategia") ||
                lower.contains("o que fazer") || lower.contains("o que posso") ||
                lower.contains("qual o melhor") || lower.contains("qual a melhor") ||
                lower.contains("bom dia") || lower.contains("boa tarde") || lower.contains("olá") || lower.contains("ola") ||
                lower.contains("?")

        if (isAdviceOrQuestion) {
            val (summary, answer, spoken) = generateLocalAdvice(text, lower)
            return GeminiVoiceAction(
                actionType = VoiceActionType.ADVICE,
                summary = summary,
                rawSpokenText = text,
                adviceAnswer = answer,
                spokenResponse = spoken
            )
        }

        // 1. Verificar se é Despesa
        if (lower.contains("despesa") || lower.contains("gastei") || lower.contains("paguei conta") || lower.contains("pagar conta") || lower.contains("custo")) {
            val amount = extractAmount(lower) ?: 0.0
            var description = text
                .replace(Regex("(?i)(lançar|registrar|cadastrar|adicionar|nova|uma)?\\s*(despesa|gastei|gasto de)\\s*(de)?"), "")
                .replace(Regex("(?i)\\b(\\d+([.,]\\d+)?)\\s*(reais|real|r\\$)?\\b"), "")
                .replace(Regex("(?i)(no|com|em)?\\s*(dinheiro|pix|cartão|cartao)"), "")
                .trim()
                .trim(',', '.', '-', ' ')

            if (description.isBlank()) description = "Despesa diversa"

            val method = when {
                lower.contains("pix") -> VoicePaymentMethod.PIX
                lower.contains("cart") -> VoicePaymentMethod.CARTAO
                else -> VoicePaymentMethod.DINHEIRO
            }

            val category = when {
                lower.contains("luz") || lower.contains("energia") || lower.contains("água") || lower.contains("agua") || lower.contains("internet") -> "Contas Fixas"
                lower.contains("almoço") || lower.contains("almoco") || lower.contains("comida") || lower.contains("lanche") -> "Alimentação"
                lower.contains("gasolina") || lower.contains("transporte") || lower.contains("uber") -> "Transporte"
                lower.contains("fornecedor") || lower.contains("mercadoria") || lower.contains("estoque") -> "Fornecedores"
                else -> "Geral"
            }

            val formattedDesc = description.replaceFirstChar { it.uppercase() }
            val amountStr = "R$ ${"%.2f".format(amount)}"
            val summary = "Despesa: $formattedDesc de $amountStr ($category)"
            val spoken = "Despesa de $formattedDesc no valor de $amountStr pronta para registrar."

            return GeminiVoiceAction(
                actionType = VoiceActionType.EXPENSE,
                summary = summary,
                rawSpokenText = text,
                spokenResponse = spoken,
                expenseDescription = formattedDesc,
                expenseAmount = amount,
                expenseCategory = category,
                expensePaymentMethod = method
            )
        }

        // 2. Verificar se é Novo Produto
        if (lower.contains("produto") || lower.contains("cadastr") || lower.contains("item novo") || lower.contains("adicionar produto")) {
            val price = extractAmount(lower) ?: 0.0
            val stock = extractQuantity(lower, default = 10.0)

            var name = text
                .replace(Regex("(?i)(cadastrar|cadastra|novo|adicionar)?\\s*produto\\s*"), "")
                .replace(Regex("(?i)(valor|preço|preco|por|de)?\\s*\\b(\\d+([.,]\\d+)?)\\s*(reais|real|r\\$)?\\b"), "")
                .replace(Regex("(?i)(estoque|quantidade|qtd)?\\s*\\b(\\d+)\\s*(unidades|un)?\\b"), "")
                .trim()
                .trim(',', '.', '-', ' ')

            if (name.isBlank()) name = "Novo Produto"
            val formattedName = name.replaceFirstChar { it.uppercase() }
            val priceStr = "R$ ${"%.2f".format(price)}"
            val summary = "Produto: $formattedName por $priceStr (Estoque: ${stock.toInt()})"
            val spoken = "Produto $formattedName no valor de $priceStr com ${stock.toInt()} unidades pronto para cadastrar."

            return GeminiVoiceAction(
                actionType = VoiceActionType.PRODUCT,
                summary = summary,
                rawSpokenText = text,
                spokenResponse = spoken,
                productName = formattedName,
                productPrice = price,
                productCostPrice = if (price > 0) (price * 0.6) else 0.0,
                productStock = stock,
                productCategory = "Geral"
            )
        }

        // 3. Padrão Venda
        var workingText = text

        // Extrair forma de pagamento
        val isAPrazo = lower.contains("prazo") || lower.contains("fiado") || lower.contains("a prazo")
        val method = when {
            isAPrazo -> VoicePaymentMethod.A_PRAZO
            lower.contains("pix") -> VoicePaymentMethod.PIX
            lower.contains("cart") -> VoicePaymentMethod.CARTAO
            else -> VoicePaymentMethod.DINHEIRO
        }

        // Remover termos de pagamento para não poluir o nome do produto
        workingText = workingText.replace(Regex("(?i)\\b(no|em|com|via)?\\s*(dinheiro|pix|cartão|cartao|a prazo|fiado|à vista|a vista)\\b"), " ")

        // 1. EXTRAIR CLIENTE E REMOVER DO TEXTO (ex: "seu Zé", "dona Maria", "pro Seu Zé", "para o Zé")
        val (customer, textAfterCust) = extractCustomerAndStrip(workingText)
        workingText = textAfterCust

        // 2. EXTRAIR VALOR/PREÇO E REMOVER DO TEXTO (ex: "15 reais", "por 20 reais", "20 conto")
        val (amount, textAfterAmount) = extractAmountAndStrip(workingText)
        workingText = textAfterAmount

        // 3. EXTRAIR QUANTIDADE E UNIDADE (ex: "3 kg", "3 quilos", "2 caixas", "5 un")
        val (quantity, unit, textAfterQty) = extractQuantityAndStrip(workingText)
        workingText = textAfterQty

        // 4. NOME DO PRODUTO: o que sobrou é EXCLUSIVAMENTE o item vendido
        var productName = workingText
            .replace(Regex("(?i)\\b(vendi|venda de|venda|vende|registra|registrar|anota|anotar|lançar|lança|passa|passar)\\b"), " ")
            .replace(Regex("(?i)\\b(por|de|valor|total|para|pro|pra|ao|cliente|foi)\\b"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .trim(',', '.', '-', ' ')

        if (productName.isBlank()) {
            productName = "Mercadoria"
        } else {
            productName = productName.replaceFirstChar { it.uppercase() }
        }

        val totalAmount = amount ?: 0.0
        val unitPrice = if (totalAmount > 0.0 && quantity > 0.0) {
            totalAmount / quantity
        } else {
            0.0
        }

        val finalUnit = if (unit.isNotBlank()) unit else "un"

        val item = GeminiVoiceSaleItem(
            productName = productName,
            quantity = quantity,
            unit = finalUnit,
            unitPrice = unitPrice
        )

        val status = if (isAPrazo) SalePaymentStatus.PENDENTE else SalePaymentStatus.PAGO
        val qtyLabel = if (quantity % 1.0 == 0.0) "${quantity.toInt()} $finalUnit" else "$quantity $finalUnit"
        val customerLabel = if (!customer.isNullOrBlank()) " | Cliente: $customer" else ""
        val priceLabel = if (totalAmount > 0.0) " | Valor: R$ ${"%.2f".format(totalAmount)}" else ""
        val summary = if (isAPrazo) {
            "Venda a Prazo: $qtyLabel de $productName$customerLabel$priceLabel"
        } else {
            "Venda: $qtyLabel de $productName$customerLabel$priceLabel (${method.label})"
        }

        // Se nada de concreto foi identificado como venda (apenas conversa casual), transformar em ADVICE
        if (productName == "Mercadoria" && totalAmount == 0.0 && customer == null) {
            val (advSummary, advAnswer, advSpoken) = generateLocalAdvice(text, lower)
            return GeminiVoiceAction(
                actionType = VoiceActionType.ADVICE,
                summary = advSummary,
                rawSpokenText = text,
                adviceAnswer = advAnswer,
                spokenResponse = advSpoken
            )
        }

        val spoken = if (!customer.isNullOrBlank()) {
            "Entendido! Venda de $qtyLabel de $productName para $customer${if (totalAmount > 0) " no valor de R$ ${"%.2f".format(totalAmount)}" else ""}. O cliente e o item foram separados."
        } else {
            "Venda de $qtyLabel de $productName${if (totalAmount > 0) " no valor de R$ ${"%.2f".format(totalAmount)}" else ""} identificada com sucesso."
        }

        return GeminiVoiceAction(
            actionType = VoiceActionType.SALE,
            summary = summary,
            rawSpokenText = text,
            spokenResponse = spoken,
            customerName = customer,
            items = listOf(item),
            paymentMethod = method,
            paymentStatus = status,
            totalAmount = if (totalAmount > 0.0) totalAmount else null,
            paidAmount = if (isAPrazo) 0.0 else (if (totalAmount > 0.0) totalAmount else null),
            daysUntilDue = if (isAPrazo) 30 else null
        )
    }

    private fun generateLocalAdvice(rawText: String, lower: String): Triple<String, String, String> {
        return when {
            lower.contains("promoc") || lower.contains("promoção") || lower.contains("oferta") || lower.contains("vender mais") -> {
                val summary = "Ideias de Promoções e Vendas Rápidas"
                val answer = """
                    Aqui estão 3 ideias práticas para aquecer suas vendas hoje:
                    
                    1. Combo "Compre Junto e Economize": Junte um item de alta saída com um de menor giro oferecendo um pequeno desconto no conjunto.
                    2. Desconto Relâmpago no WhatsApp: Publique no status às 11h ou 17h: "Apenas hoje: os 5 primeiros ganham 10% de desconto ou brinde".
                    3. Cartão Fidelidade Simples: "A cada 10 compras acima de R$ 20, ganhe um produto ou R$ 15 de crédito". Clientes adoram acumular benefícios!
                """.trimIndent()
                val spoken = "Separei 3 ideias práticas de promoções: combos promocionais, ofertas relâmpago no WhatsApp e fidelidade para seus clientes!"
                Triple(summary, answer, spoken)
            }

            lower.contains("fiado") || lower.contains("cobrar") || lower.contains("calote") -> {
                val summary = "Estratégia para Reduzir o Fiado"
                val answer = """
                    Como proteger seu caixa sem perder clientes:
                    
                    1. Incentivo no Pix/Dinheiro: Dê 5% de desconto para pagamento imediato. O cliente sente vantagem na hora.
                    2. Limite Máximo de Crédito: Estabeleça um teto (ex: máximo R$ 100) e nova compra a prazo só após quitar a anterior.
                    3. Data Fixa no Registro: Ao vender a prazo, combine o dia exato (ex: dia do pagamento ou dia 05) e anote aqui no aplicativo com data de vencimento.
                """.trimIndent()
                val spoken = "Para reduzir o fiado, dê desconto no Pix, estabeleça um valor limite por cliente e sempre combine uma data fixa de vencimento."
                Triple(summary, answer, spoken)
            }

            lower.contains("lucro") || lower.contains("preco") || lower.contains("preço") || lower.contains("calcular") -> {
                val summary = "Como Formar Preço e Proteger seu Lucro"
                val answer = """
                    Fórmula simples de precificação para comércio:
                    
                    1. Custo Real da Mercadoria: Inclua frete, embalagem e sacola no custo unitário.
                    2. Taxas e Despesas: Lembre-se da taxa da maquininha ou imposto (em média 5% a 10%).
                    3. Margem Limpa: Garanta que sobre entre 25% a 40% de lucro líquido para reinvestir e pagar seu pró-labore.
                    Dica de ouro: Nunca pague despesas pessoais da sua casa com o dinheiro do caixa do negócio!
                """.trimIndent()
                val spoken = "Para calcular o preço com lucro seguro, some o custo da mercadoria, frete, embalagem e taxas da maquininha, garantindo sua margem limpa."
                Triple(summary, answer, spoken)
            }

            lower.contains("atrair") || lower.contains("cliente") -> {
                val summary = "Como Atrair Novos Clientes"
                val answer = """
                    Dicas para movimentar sua clientela:
                    
                    1. Indicação Premiada: Fale para seus clientes fiéis: "Indique um amigo e ambos ganham um agrado na próxima compra".
                    2. Presença Visual no Bairro: Mantenha a vitrine limpa e tire fotos reais e nítidas dos produtos para postar nas redes.
                    3. Atendimento com Nome: Chame os clientes pelo nome e anote preferências aqui no cadastro de clientes para surpreendê-los!
                """.trimIndent()
                val spoken = "Para atrair clientes, use fotos reais com boa luz, promova indicações entre amigos e atenda sempre chamando pelo nome!"
                Triple(summary, answer, spoken)
            }

            else -> {
                val summary = "Consultoria Rápida de Negócios"
                val answer = """
                    Estou aqui para ajudar seu comércio a prosperar!
                    
                    Você pode me pedir:
                    • Ideias para divulgar e atrair clientes no seu bairro
                    • Dicas de combos e promoções para datas especiais
                    • Como organizar despesas e separar o dinheiro pessoal da empresa
                    • Ou simplesmente ditar uma venda, novo produto ou despesa por voz!
                """.trimIndent()
                val spoken = "Olá! Sou sua assistente pessoal de negócios. Pode me pedir dicas de vendas, ideias de promoções ou ditar seus registros do dia a dia!"
                Triple(summary, answer, spoken)
            }
        }
    }

    private fun extractCustomerAndStrip(text: String): Pair<String?, String> {
        val forbidden = setOf("pix", "dinheiro", "cartao", "cartão", "prazo", "fiado", "reais", "real", "produto", "venda", "despesa", "conta", "kg", "quilo", "quilos", "un", "unidades")

        // 1. Títulos de respeito comuns no Brasil (ex: "seu Zé", "dona Maria", "tio João", "dr Paulo", "senhor Carlos")
        val titlePattern = Pattern.compile("(?i)(?:(?:para\\s+o|para\\s+a|para|pro|pra|ao|cliente)\\s+)?\\b(seu|dona|dr|dra|doutor|doutora|sr|sra|senhor|senhora|tio|tia)\\s+([a-zA-ZáàâãéèêíïóôõöúçñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑ]+)")
        val titleMatcher = titlePattern.matcher(text)
        if (titleMatcher.find()) {
            val title = titleMatcher.group(1)?.replaceFirstChar { it.uppercase() } ?: ""
            val name = titleMatcher.group(2)?.replaceFirstChar { it.uppercase() } ?: ""
            if (name.lowercase() !in forbidden) {
                val fullCustomer = "$title $name".trim()
                val stripped = text.substring(0, titleMatcher.start()) + " " + text.substring(titleMatcher.end())
                return Pair(fullCustomer, stripped.trim())
            }
        }

        // 2. Preposição explícita: "pro Marcos", "para a Beatriz", "ao João", "cliente Carlos"
        val prepPattern = Pattern.compile("(?i)\\b(?:para\\s+o|para\\s+a|para|pro|pra|ao|cliente)\\s+([a-zA-ZáàâãéèêíïóôõöúçñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑ]+(?:\\s+[a-zA-ZáàâãéèêíïóôõöúçñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑ]+)?)")
        val prepMatcher = prepPattern.matcher(text)
        if (prepMatcher.find()) {
            val nameRaw = prepMatcher.group(1)?.trim() ?: ""
            val firstWord = nameRaw.split(" ").firstOrNull()?.lowercase() ?: ""
            if (nameRaw.isNotBlank() && firstWord !in forbidden) {
                val formattedName = nameRaw.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                val stripped = text.substring(0, prepMatcher.start()) + " " + text.substring(prepMatcher.end())
                return Pair(formattedName, stripped.trim())
            }
        }

        return Pair(null, text)
    }

    private fun extractAmountAndStrip(text: String): Pair<Double?, String> {
        val pattern = Pattern.compile("(?i)(?:por|de|valor|total)?\\s*(?:r\\$\\s*)?(\\d+([.,]\\d{1,2})?)\\s*(?:reais|real|r\\$|conto)\\b")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(',', '.')
            val parsed = numStr?.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                val stripped = text.substring(0, matcher.start()) + " " + text.substring(matcher.end())
                return Pair(parsed, stripped.trim())
            }
        }

        val endingPattern = Pattern.compile("(?i)(?:por|total)\\s+(\\d+([.,]\\d{1,2})?)\\s*$")
        val endingMatcher = endingPattern.matcher(text)
        if (endingMatcher.find()) {
            val numStr = endingMatcher.group(1)?.replace(',', '.')
            val parsed = numStr?.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                val stripped = text.substring(0, endingMatcher.start()) + " " + text.substring(endingMatcher.end())
                return Pair(parsed, stripped.trim())
            }
        }

        return Pair(null, text)
    }

    private data class QtyResult(val quantity: Double, val unit: String, val strippedText: String)

    private fun extractQuantityAndStrip(text: String): QtyResult {
        val pattern = Pattern.compile("(?i)\\b(\\d+([.,]\\d{1,2})?)\\s*(kg|quilos?|kilos?|g|gramas?|litros?|l|unidades?|un|pacotes?|caixas?|fardos?|latas?|garrafas?|x|peças?|pecas?)?\\b")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(',', '.')
            val parsed = numStr?.toDoubleOrNull()
            val unitRaw = matcher.group(3)?.lowercase() ?: "un"
            val normalizedUnit = when {
                unitRaw.startsWith("kg") || unitRaw.startsWith("quil") || unitRaw.startsWith("kil") -> "kg"
                unitRaw.startsWith("g") && !unitRaw.startsWith("garraf") -> "g"
                unitRaw.startsWith("l") && !unitRaw.startsWith("lat") -> "litros"
                unitRaw.startsWith("cx") || unitRaw.startsWith("caix") -> "caixas"
                unitRaw.startsWith("fard") -> "fardos"
                unitRaw.startsWith("pacot") -> "pacotes"
                unitRaw.startsWith("garraf") -> "garrafas"
                unitRaw.startsWith("lat") -> "latas"
                else -> "un"
            }
            if (parsed != null && parsed > 0.0) {
                var stripped = text.substring(0, matcher.start()) + " " + text.substring(matcher.end())
                stripped = stripped.replace(Regex("(?i)^\\s*de\\s+"), "")
                stripped = stripped.replace(Regex("(?i)\\s+de\\s+"), " ")
                return QtyResult(parsed, normalizedUnit, stripped.trim())
            }
        }
        return QtyResult(1.0, "un", text)
    }

    private fun extractAmount(text: String): Double? {
        val pattern = Pattern.compile("(?i)(\\d+([.,]\\d{1,2})?)\\s*(reais|real|r\\$)?")
        val matcher = pattern.matcher(text)
        var lastVal: Double? = null
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(',', '.')
            val parsed = numStr?.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                lastVal = parsed
            }
        }
        return lastVal
    }

    private fun extractQuantity(text: String, default: Double): Double {
        val pattern = Pattern.compile("(?i)\\b(\\d+)\\s*(unidades|un|x|peças|pecas|itens)?\\b")
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val numStr = matcher.group(1)
            val parsed = numStr?.toDoubleOrNull()
            if (parsed != null && parsed in 1.0..999.0) {
                return parsed
            }
        }
        return default
    }

    private fun extractCustomer(text: String): String? {
        val (customer, _) = extractCustomerAndStrip(text)
        return customer
    }
}
