## Kugo Tunes 2.5.0

### English
- Bumped Android versionCode to 42 and versionName to 2.5.0.
- Removed the optional Cobalt-compatible API setting and all Cobalt-specific code.
- Added the bundled yt-dlp Android runtime as the primary extractor for public TikTok, Instagram, Facebook and Kwai links, retaining the existing public-page parser as a fallback.
- Spotify now asks only for a Spotify playlist link and uses MP3/M4A format chips instead of the legacy MP3 switch.
- YouTube video flow now offers MP3/MP4 format selection; YouTube Music keeps MP3/M4A.

### Português (Brasil)
- Atualizado o versionCode Android para 42 e versionName para 2.5.0.
- Removida a configuração e todo o código específico do Cobalt.
- Adicionado o runtime Android do yt-dlp como extrator principal para links públicos do TikTok, Instagram, Facebook e Kwai, mantendo o extrator de página pública existente como alternativa.
- Spotify agora pede somente o link de uma playlist do Spotify e usa seletores MP3/M4A no lugar da chave antiga de conversão.
- A etapa de vídeo do YouTube agora oferece seleção MP3/MP4; o YouTube Music mantém MP3/M4A.

### Test status / Estado dos testes
- Source and configuration edits applied.
- Real platform downloads and Android release build must be tested in GitHub Actions and on a device before claiming success.
