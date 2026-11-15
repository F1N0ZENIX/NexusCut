# Gateway de IA — experimental, não publicar

A tela utilitária em Configurações > AI gateway / Legendas por IA permite cadastro local de chaves e acesso ao protótipo isolado BNR descrito em [nvidia-bnr-prototype.md](nvidia-bnr-prototype.md). Não implementa o conceito visual aprovado nem integração à linha do tempo. Groq, NVIDIA e Z.ai podem ser cadastrados, substituídos ou excluídos. Não há Gemini.

## Bloqueio de segurança: transcrição indisponível

O envio de mídia para transcrição, a escolha de arquivo para transcrição e o salvamento de SRT foram **removidos do fluxo**, assim como a implementação HTTP antiga. O protótipo BNR tem seletor e envio próprios, separados. O conversor SRT isolado permanece, mas não recebe respostas de rede. Portanto, esta revisão não gera nem salva legendas e não envia arquivos à Groq. A medida elimina o envio sem controle e a perda do resultado no seletor/rotação desativando a funcionalidade, não é uma implementação de cancelamento nem de retenção concluída.

Para reintroduzir a transcrição antes de qualquer publicação, é necessário projetar e testar controle de uma única operação com cancelamento efetivo e visível ao sair, prevenção de envios concorrentes, continuidade/estado observável em rotação, retenção do SRT durante a recriação e o seletor de salvar, além de consentimento explícito para cada envio. Deve-se explicar que cancelamento não recolhe bytes já recebidos pelo provedor nem garante interrupção instantânea de I/O bloqueado. Testar com servidor controlado: rotação, botão voltar/sair, seletor, operações bloqueadas, falhas e cancelamento nas várias fases. Não prometer recuperação após morte do processo: ela pode encerrar o envio e perder um resultado não salvo; salvar no destino escolhido e confirmar sucesso antes de descartar o resultado. Uma futura limpeza do cache não pode apagar arquivos de operações ativas.

## Cache legado e chaves

Na inicialização do processo, o app tenta apagar somente arquivos regulares `ai-upload-*.tmp` diretamente no cache privado, formato usado pela versão anterior. É uma limpeza de melhor esforço, sem registrar nomes, conteúdo ou erros; se não puder apagar, tenta novamente na próxima inicialização. Ela não é apagamento seguro, não cobre outros locais ou um processo que nunca mais inicia e não garante remoção após morte do processo. A antiga rota de upload de transcrição está desativada. O BNR usa memória e arquivos em outro diretório de cache.

As chaves usam AES-GCM com chave Android Keystore não exportável, ciphertext em `noBackupFilesDir` e AAD por provedor. O Android 8 usa backup `.bak` no AtomicFile: o cadastro é detectado e `openRead` pode restaurá-lo; `.bin`, `.bin.bak` e `.bin.new` impedem criar silenciosamente nova chave mestra se a anterior sumir. Exclusão tenta apagar as três variantes. Se o Keystore for perdido ou invalidado, pode ser necessário excluir os cadastros antigos e registrar de novo. A janela da activity **e a janela separada do diálogo de edição da chave** usam FLAG_SECURE; o campo também não salva estado e é limpo ao fechar o diálogo. Isso não protege contra teclado malicioso, processo/dispositivo comprometido nem captura por câmera. A chave passa pela memória; não há garantia de hardware ou biometria. O tratamento global de falhas e logs do app original não foi auditado integralmente.

## Internet e atualizações

Esta branch volta a declarar `INTERNET` para o protótipo NVIDIA após confirmação do usuário. Conferir o manifesto mesclado do APK; isso **não** é promessa de que o app é offline. O botão existente de atualização abre `https://github.com/tharunbirla/LibreCuts/releases/latest` via `ACTION_VIEW` em outro app/navegador; não é um verificador ou instalador automático dentro do gateway. O navegador precisa de rede para carregar a página e aponta para o upstream LibreCuts, não para releases do fork NexusCut. Não presumir que o app verifica, baixa ou instala atualizações automaticamente.

## Validação obrigatória

Não publicar sem JDK 17, SDK Android 34 e Gradle wrapper 8.9: executar `./gradlew assembleDebug testDebugUnitTest lintDebug` e `./gradlew connectedDebugAndroidTest`. Testar em Android 8 e versões mais recentes: simular `.bak` sem arquivo base, perda de chave mestra, exclusão, limpeza após encerramento abrupto, rotação e screenshot/gravação da activity e do diálogo de chave; inspecionar manifesto mesclado, privacidade do teclado e logs. Os testes instrumentados usam dados fictícios. A funcionalidade e compilação não foram validadas em dispositivo nesta revisão. O fluxo de IA deve continuar desativado até implementação segura e validação de ponta a ponta.

> Experimental: use apenas chaves fictícias até que a compilação e os testes de segurança sejam validados.

O CI executa build debug, lint e testes unitarios.
