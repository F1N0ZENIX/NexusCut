# Protótipo NVIDIA BNR — isolado, experimental

Não é integração no editor: seleciona um WAV inteiro por seletor Android, não extrai áudio de vídeo, não insere resultado na timeline nem salva projeto. Use somente dados sintéticos e chave de teste até que build, segurança e dispositivo sejam verificados. Transcrição continua desativada.

## Funcionamento

Cadastre a chave NVIDIA no cofre existente. Na tela AI Gateway abra BNR, selecione WAV PCM 16-bit mono em 16 ou 48 kHz, de até 35.000.000 bytes e 360 segundos, e aceite o diálogo específico para cada envio. O app requer internet e transmite o arquivo inteiro diretamente para o preview NVIDIA via gRPC/TLS, sem servidor do NexusCut. O aviso não garante gratuidade: preview/trial pode mudar, consulte condições, créditos, privacidade e disponibilidade da NVIDIA. A chave é lida do Keystore, permanece em memória para a chamada e não é registrada intencionalmente; o tratamento global de falhas/logs do app não foi auditado.

A saída é validada como WAV e deve ter exatamente a mesma taxa e número de amostras que a entrada. Isso verifica duração e formato, **não comprova alinhamento temporal amostra a amostra**, qualidade nem preservação semântica. O original nunca é aberto para escrita. O resultado é novo arquivo no cache privado, sincronizado antes de disponibilizar o botão Compartilhar; o Android pode apagar o cache, então salve uma cópia por outro app imediatamente. Compartilhar é uma ação separada e o app escolhido recebe acesso ao resultado. Rotação ou saída cancelam operação; não há retomada após morte do processo. A leitura de um provedor de arquivos pode bloquear e não há garantia de interrupção imediata. Cancelar não recolhe dados já enviados nem garante que o provedor pare de processar. Fragmentos de saída são apagados em falhas normais/cancelamento, mas morte do processo pode deixar arquivo privado parcial no cache; não é exclusão segura. Resultados antigos podem permanecer em cache até o sistema limpar ou dados do app serem apagados.

## Fontes, contrato e licença

- [NVIDIA BNR Preview](https://build.nvidia.com/nvidia/bnr/api): endpoint `grpc.nvcf.nvidia.com:443`, function ID `c95bfe76-c553-4975-bc18-8ef9d6e2666b`, TLS e modo transacional, limites de WAV.
- [Documentação oficial](https://docs.nvidia.com/nim/maxine/bnr/latest/advanced-usage.html): gRPC bidirecional; transacional envia WAV em blocos. O modo streaming de float PCM não é implementado aqui.
- `app/src/main/proto/nvidia/ai4m/bnr/v1/bnr.proto` é cópia com finais de linha normalizados do [cliente NVIDIA](https://github.com/NVIDIA-Maxine/nim-clients/blob/d02b7d57541b8598ac4c057947b10b2e42ccb1ee/bnr/protos/proto/nvidia/ai4m/bnr/v1/bnr.proto), licença MIT e aviso mantidos no próprio arquivo.

## Antes de qualquer uso real

Execute `./gradlew assembleDebug testDebugUnitTest lintDebug`, `./gradlew connectedDebugAndroidTest` e inspecione o manifesto mesclado. Em Android 8 e recente, teste com WAVs sintéticos: rejeição por formato/tamanho/duração, sem chave, sem rede, credencial inválida, falha e resposta truncada; cancelar durante leitura/envio/resposta/escrita, sair e rotacionar; verificar original byte a byte; testar compartilhamento, URI de origem somente leitura e processos mortos. Confira compatibilidade do serviço ao vivo apenas com autorização, conta e dados não sensíveis. O teste fake não verifica TLS, conectividade, desempenho nem comportamento real da NVIDIA. Não use em produção sem auditoria adicional de privacidade, memória e concorrência.
