# Como contribuir

Obrigado por querer ajudar o Listonatha. Toda contribuição conta: relatar um erro, sugerir uma ideia, melhorar uma tradução ou mandar código.

## Relatar um erro

1. Veja se já existe uma [issue](https://github.com/Vicente-PIlonetto/Listonatha/issues) sobre o mesmo problema. Se existir, comente nela com o que acontece com você.
2. Se não existir, abra uma nova com o modelo **Relatar um erro**.
3. Conte o que você fez, o que esperava e o que aconteceu. Prints ou um vídeo curto ajudam muito.
4. Informe a versão do app (*Configurações > Sobre*), o modelo do celular e a versão do Android.

Nunca coloque senhas, tokens ou dados pessoais numa issue.

## Sugerir uma ideia

Abra uma issue com o modelo **Sugerir uma ideia** e explique o problema que ela resolve no seu dia a dia. Uma imagem ou exemplo de outro app também ajuda.

## Traduções

Os textos ficam em `app/src/main/assets/i18n/`, um arquivo por idioma. A chave é o texto em português e o valor é a tradução. Mantenha os marcadores `{0}`, `{1}` como estão e os espaços no começo ou no fim, quando houver.

## Código

O passo a passo para compilar está no [guia de desenvolvimento](docs/DESENVOLVIMENTO.md).

1. Faça um fork e crie um branch a partir da `main` (ex.: `corrige-agenda`).
2. Mantenha cada pull request focado em uma coisa só.
3. Rode os testes antes de enviar: `./gradlew testDebugUnitTest`.
4. Abra o pull request contra a `main` explicando o que mudou e por quê. A `main` é protegida: toda mudança entra por pull request.

### Estilo

- Kotlin com Jetpack Compose. Siga o estilo dos arquivos vizinhos.
- Comentários e textos da interface em português do Brasil.
- Textos curtos e diretos, sem exclamações e sem tom de propaganda. Mensagens de erro dizem o que aconteceu e, se der, o que fazer.
- Todo texto novo da interface passa por `t("...")` e ganha tradução nos arquivos de idioma.
- Animações devem ser leves: prefira mexer em `graphicsLayer` e evite redesenhar telas inteiras.

## Conduta

Seja gentil e respeitoso. Críticas são sobre o código e as ideias, nunca sobre as pessoas.
