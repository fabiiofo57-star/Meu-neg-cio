#!/usr/bin/env bash
# =============================================================================
# SCRIPT DE DEPLOY NO GOOGLE CLOUD RUN — MEU NEGÓCIO (WHATSAPP CLOUD API)
# =============================================================================
# Execute este script no Google Cloud Shell:
# cd backend && chmod +x deploy.sh && ./deploy.sh
# =============================================================================

set -e

PROJECT_ID="meu-negocio-ffdd3"
SERVICE_NAME="meu-negocio-whatsapp"
REGION="us-central1"

echo "=========================================================="
echo "🚀 INICIANDO DEPLOY NO GOOGLE CLOUD RUN"
echo "Projeto : $PROJECT_ID"
echo "Serviço : $SERVICE_NAME"
echo "Região  : $REGION"
echo "=========================================================="

# 1. Configura o projeto ativo no gcloud
echo "📌 Definindo projeto ativo: $PROJECT_ID..."
gcloud config set project "$PROJECT_ID"

# 2. Garante que as APIs necessárias estejam ativas
echo "🔧 Verificando e habilitando APIs necessárias (Cloud Run e Cloud Build)..."
gcloud services enable run.googleapis.com cloudbuild.googleapis.com --project="$PROJECT_ID"

# 3. Executa o build do container e deploy no Cloud Run
echo "📦 Enviando código-fonte, compilando container e publicando..."
gcloud run deploy "$SERVICE_NAME" \
  --source . \
  --region "$REGION" \
  --project "$PROJECT_ID" \
  --allow-unauthenticated \
  --set-env-vars="NODE_ENV=production,FIREBASE_PROJECT_ID=$PROJECT_ID,META_VERIFY_TOKEN=meu_negocio_verify_token_2026,META_API_VERSION=v21.0,GEMINI_MODEL=gemini-3.6-flash"

# 4. Obtém a URL gerada pelo Cloud Run
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" --platform managed --region "$REGION" --project "$PROJECT_ID" --format 'value(status.url)')
WEBHOOK_URL="${SERVICE_URL}/webhook"

echo ""
echo "=========================================================="
echo "🎉 DEPLOY CONCLUÍDO COM SUCESSO!"
echo "=========================================================="
echo ""
echo "📍 URL PÚBLICA DO BACKEND:"
echo "   $SERVICE_URL"
echo ""
echo "🔗 URL DO WEBHOOK PARA O META DEVELOPERS:"
echo "   $WEBHOOK_URL"
echo ""
echo "🔑 VERIFY TOKEN:"
echo "   meu_negocio_verify_token_2026"
echo ""
echo "=========================================================="
echo "Próximo passo: configure os segredos da Meta (META_APP_SECRET,"
echo "META_ACCESS_TOKEN, META_PHONE_NUMBER_ID, META_WABA_ID e"
echo "GEMINI_API_KEY) no Cloud Run Console ou com:"
echo "gcloud run services update $SERVICE_NAME --region $REGION --update-env-vars META_APP_SECRET=...,META_ACCESS_TOKEN=...,META_PHONE_NUMBER_ID=...,META_WABA_ID=...,GEMINI_API_KEY=...,PUBLIC_BACKEND_URL=$SERVICE_URL"
echo "=========================================================="
