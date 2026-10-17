## Kugo Tunes 2.4.0

**Download:** `Kugo-Tunes.apk` (below, under *Assets*). Open it on your Android phone to install or update.

### What's new in 2.4.0
- Version update to 2.4.0 (Android versionCode 41).
- Added an optional Cobalt-compatible social extraction API setting; it requires a compatible API instance and is not a guarantee that every platform link will work.
- Facebook: share links (/share/p/, /share/v/ ...) are followed to the video they point to; clearer errors (post without video, private/login, blocked, network)
- Instagram Reels: links with extra parameters (?dlrf=...) are normalized; more ways to read the public Reel page; clearer errors
- TikTok: short links retried with a second browser profile; the error now says whether access was blocked or the page format changed
- (Experimental platforms: not confirmed against live links, they may still fail)
- YouTube: the original audio track is now preferred over automatic AI dubs; when a single video has several tracks and the original can't be identified, you choose the track in the queue
- Back arrow and Android back button now work after a download finishes (all platforms), and *Download Another* starts a new download without restarting the app
- New (experimental): Instagram Reels and Facebook videos, public links only. Stories and private content are not supported; availability, quality and watermark-free files depend on what the public page provides and cannot be guaranteed
- TikTok: more robust link handling and clearer error messages (fixes could not be confirmed against live links)
- Português (Brasil) layout fixes in Preferences (update switch and the About section)
- YouTube covers: falls back to other thumbnail sizes when the largest one doesn't exist

> Releases keep the same signing key, so this installs over the previous version.

### Novidades da versão 2.4.0 (pt-BR)
- Versão atualizada para 2.4.0 (Android versionCode 41).
- Adicionada configuração opcional de API compatível com Cobalt para extração social; requer uma instância compatível e não garante que todos os links funcionem.
- Facebook: links de compartilhamento (/share/p/, /share/v/ ...) são seguidos até o vídeo; erros mais claros (publicação sem vídeo, privado/login, bloqueio, rede)
- Reels do Instagram: links com parâmetros extras (?dlrf=...) são normalizados; mais formas de ler a página pública do Reel; erros mais claros
- TikTok: links curtos são tentados com um segundo perfil de navegador; o erro agora diz se o acesso foi bloqueado ou se o formato da página mudou
- (Plataformas experimentais: não confirmadas com links reais, ainda podem falhar)
- YouTube: a faixa de áudio original agora é preferida em vez de dublagens automáticas por IA; quando um vídeo tem várias faixas e a original não pode ser identificada, você escolhe a faixa na fila
- A seta de voltar e o botão voltar do Android agora funcionam após o download (todas as plataformas), e *Baixar outro* inicia um novo download sem reiniciar o app
- Novo (experimental): Reels do Instagram e vídeos do Facebook, somente links públicos. Stories e conteúdo privado não são suportados; disponibilidade, qualidade e arquivos sem marca d'água dependem do que a página pública fornece e não podem ser garantidos
- TikTok: tratamento de links mais robusto e mensagens de erro mais claras (as correções não puderam ser confirmadas com links reais)
- Correções de layout em português (Brasil) nas Preferências (botão de atualização automática e seção Sobre)
- Capas do YouTube: usa outros tamanhos de miniatura quando a maior não existe

> A assinatura é a mesma das versões anteriores, então a atualização instala por cima.
