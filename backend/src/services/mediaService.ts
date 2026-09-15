import axios from 'axios';
import { config } from '../config/env';

export class MediaService {
  /**
   * Baixa um arquivo de mídia (áudio/documento) a partir do media_id da WhatsApp Cloud API.
   *
   * @param mediaId ID da mídia retornado no payload do webhook
   */
  async downloadMedia(mediaId: string): Promise<{ buffer: Buffer; mimeType: string } | null> {
    if (!config.meta.accessToken) {
      console.warn('⚠️ [MEDIA SERVICE] Meta Access Token não configurado. Não é possível baixar mídia real.');
      return null;
    }

    try {
      // 1. Consulta metadados da mídia para obter a URL temporária de download
      const metaUrl = `https://graph.facebook.com/${config.meta.apiVersion}/${mediaId}`;
      const metaResponse = await axios.get(metaUrl, {
        headers: {
          Authorization: `Bearer ${config.meta.accessToken}`,
        },
        timeout: 10000,
      });

      const downloadUrl = metaResponse.data?.url;
      const mimeType = metaResponse.data?.mime_type || 'audio/ogg';

      if (!downloadUrl) {
        console.error('URL de download de mídia não encontrada na resposta da Meta');
        return null;
      }

      // 2. Faz o download seguro do binário
      const mediaResponse = await axios.get(downloadUrl, {
        headers: {
          Authorization: `Bearer ${config.meta.accessToken}`,
          'User-Agent': 'curl/7.64.1',
        },
        responseType: 'arraybuffer',
        timeout: 15000,
      });

      const buffer = Buffer.from(mediaResponse.data);
      return { buffer, mimeType };
    } catch (error) {
      console.error(`Erro ao baixar mídia ${mediaId} da Meta:`, (error as Error).message);
      return null;
    }
  }
}

export const mediaService = new MediaService();
