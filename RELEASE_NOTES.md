# Kugo Tunes 2.5.1

## O que mudou desde a 2.5.0

- Incrementada a versão Android para `2.5.1` (`versionCode` 43), sem reutilizar a tag de lançamento anterior.
- Corrigido o workflow de CI: usa versões estáveis documentadas das ações de checkout e configuração do Java, e configuração dedicada do Gradle.
- O processo de compilação grava a saída completa em `BUILD_LOG.txt` e informa o código de saída no resumo do GitHub Actions.
- Se a compilação falhar, os logs e relatórios disponíveis são publicados como artefato do workflow para facilitar o diagnóstico; nenhum APK ou release é declarado como concluído nesse caso.
- Quando a compilação passa e o commit tem uma mensagem exatamente no formato `vX.Y.Z`, o release inclui o APK, `BUILD_LOG.txt` e `RELEASE_BUILD_INFO.txt`, para que o usuário possa baixar e ler os logs/informações da compilação.
- Mantidas as alterações introduzidas na 2.5.0: remoção da integração Cobalt, extrator yt-dlp integrado para links sociais públicos, ajuste do campo do Spotify e opções de formato, e padronização dos cartões da tela inicial.

## Validação

O log anexado é a saída real da compilação executada pelo GitHub Actions. Um log de compilação bem-sucedida não significa que todos os links de Instagram, TikTok, Facebook ou Kwai foram testados; esses testes de integração precisam ser realizados separadamente no dispositivo.
