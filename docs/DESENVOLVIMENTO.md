# Listonatha — desenvolvimento

App Android para acompanhar animes e mangás com a conta do AniList, usado por mim e por amigos (cada um entra com a própria conta). Não é publicado na Play Store.

- Kotlin + Jetpack Compose (Material 3), MVVM simples
- Apollo Kotlin 4 para a API GraphQL do AniList; o schema fica em `app/src/main/graphql/schema.graphqls`
- Room como cache de tudo: lista, obras, agenda, detalhes, Descobrir, notas especiais, fila offline e rascunhos de review
- DataStore para as configurações, Coil 3 para capas (cache em disco de 300 MB), WorkManager para sincronização e avisos
- minSdk 26, targetSdk 36, interface em português do Brasil

## 1. Criar o cliente no AniList

1. Entre em <https://anilist.co/settings/developer> e clique em **Create New Client**.
2. **Name**: `Listonatha` (ou outro nome).
3. **Redirect URL**: `listaanimes://auth` (exatamente assim; é o deep link do app).
4. Salve e copie o **ID** do cliente (um número). O *secret* não é usado: o app faz login por *implicit grant*.

## 2. Colocar o client ID

Em `local.properties`, na raiz do projeto (o arquivo não vai para o Git):

```properties
sdk.dir=/caminho/para/android-sdk
ANILIST_CLIENT_ID=12345
```

O valor entra no `BuildConfig` na compilação. Sem ele o app compila, mas a tela de login avisa que falta o client ID e o botão fica desativado.

O token do AniList fica cifrado com AES-GCM, com a chave no Android Keystore. Ele vale 1 ano. Quando expira ou é recusado, o Início mostra "Sua sessão do AniList expirou" com o botão **Entrar**, e o cache continua no aparelho.

## 3. Compilar

Requisitos: JDK 17 ou 21 e Android SDK com platform 36.

```bash
./gradlew assembleRelease        # APK otimizado (R8): app/build/outputs/apk/release/Listonatha-1.0-release.apk (use este no celular)
./gradlew assembleDebug          # APK de debug: app/build/outputs/apk/debug/Listonatha-1.0-debug.apk (bem mais lento no Compose)
./gradlew testDebugUnitTest      # testes unitários
./gradlew :app:downloadAnilistApolloSchemaFromIntrospection   # atualiza o schema do AniList
```

O release é assinado com a chave de debug para instalar por cima sem complicação. Os dois APKs têm o mesmo pacote (`dev.viko.listaanimes`), então um substitui o outro.

## 4. Instalar no celular

**Via Tailscale (Taildrop)**
```bash
scripts/enviar-celular.sh                # compila o release e envia por Taildrop (nome do aparelho no Tailscale)
scripts/enviar-celular.sh nome-do-aparelho
```
No celular, abra o arquivo pela notificação do Tailscale e toque em *Instalar*.

**Via cabo (adb)**
1. No celular, ative *Opções do desenvolvedor* (toque 7 vezes em *Sobre o telefone > Número da versão*) e depois *Depuração USB*.
2. Conecte o cabo e aceite a chave no celular.
3. `adb install -r app/build/outputs/apk/release/Listonatha-1.0-release.apk`

**Copiando o arquivo**
1. Copie o APK (`Listonatha-1.0-release.apk`) para o celular (cabo, Drive, Telegram etc.) e abra pelo app *Arquivos*.
2. Quando o Android pedir, permita **instalar apps desconhecidos** para o app usado para abrir o APK.
3. Toque em *Instalar*. Se o Play Protect avisar, escolha *Instalar mesmo assim*.

Para atualizar, instale o APK novo por cima. Dados e configurações são mantidos.

## Como funciona

### Social e fórum
- Aba **Social**: feed *Seguindo* e *Global*, curtir, responder, postar status (markdown, `img(url)`, `@usuário`), apagar o que é seu.
- Perfis: atividade, lista (só leitura), favoritos, sobre, seguidores/seguindo e o botão Seguir. Busca de usuários na aba Social.
- **Fórum** (ícone na aba Social e "Fórum" no Detalhe de cada obra): tópicos por categoria, *Inscritos*, busca; ler, curtir, comentar e responder comentários (com a árvore de respostas).
- No Detalhe, a seção **Seguindo** mostra quem você segue e tem a obra na lista, com status e nota.
- Links do AniList dentro de textos (obra, usuário, atividade, tópico) abrem no próprio app.
- Notificações do AniList incluem as sociais (curtidas, respostas, menções, seguidores, fórum). O aviso no sistema pode ser ligado em Configurações.
- Ações sociais precisam de internet (não entram na fila offline).

### Idiomas
- Interface em português do Brasil, inglês, espanhol, francês, alemão, italiano, russo, japonês e chinês simplificado (*Configurações > Aparência > Idioma*; "Sistema" segue o aparelho). Trocar reinicia o app.
- Os textos ficam no código em português (são as chaves) e as traduções em `app/src/main/assets/i18n/<idioma>.json`. Texto sem tradução aparece em português.
- Sinopses e descrições de personagens têm o botão **Traduzir**: tradução no aparelho (ML Kit). O modelo do idioma é baixado na primeira vez; o texto não sai do celular.

### Contas, conteúdo e perfil
- **Várias contas** (*Configurações > Contas*): trocar, adicionar e remover. Os tokens das outras contas ficam cifrados como o da conta ativa. Trocar baixa a lista da conta escolhida; com alterações não enviadas, o app envia antes (sem internet, não troca).
- **Conteúdo e filtros**: 18+ escondido por padrão; capas e banners 18+ desfocados (ligado por padrão); gêneros e tags bloqueados somem das buscas, do Descobrir e das recomendações (filtro feito no próprio AniList com `genre_not_in`/`tag_not_in`).
- **Personagens favoritos**: coração na ficha do personagem; seção no perfil, com escolha de mostrar ou não e de quais mostrar.
- **Bio** em Você, com edição (vai para o AniList).
- **Sortear** (dado no Descobrir e em Filtros): uma obra popular fora da sua lista, respeitando os filtros.
- **Sobre**: versão, data do build e licenças. Os links de código-fonte e de apoio aparecem se `REPO_URL` e `DONATE_URL` estiverem no `local.properties`.

### Aparência e atalhos
- **Ícone do app**: segue a cor de destaque ou uma cor fixa, com fundo claro ou escuro (*Aparência > Ícone do app*). Troca ao sair do app (são `activity-alias` no manifesto).
- **Agenda**: aba *Semana* e aba *Calendário* (mês inteiro, a capa do primeiro episódio pinta o dia; toque num dia para ver a lista).
- **Personagens**: toque num personagem no Detalhe para ver a ficha (dados, descrição com spoilers escondidos, onde aparece, voz japonesa).
- Gêneros e tags do Detalhe abrem a busca filtrada; *Filtros* (ícone ao lado de Descobrir) tem atalhos de nichos (Isekai, Iyashikei, Villainess…), que no AniList são tags.
- **Onde assistir** abre o app do serviço (Crunchyroll, Netflix…) se estiver instalado; senão abre a Play Store.
- Ao marcar **Completo** pelo toque longo, o app pergunta a nota (dá para pular; desligável em *Listas e títulos*).
- Foto de perfil e banner da galeria ficam só no app: o AniList não aceita envio de imagens por apps, então há um atalho para a página de configurações do site.
- **Abertura**: no primeiro carregamento, o logo do splash ganha vida (onda nas barras, ponto pulando) e se expande revelando o app. O splash do sistema segue o tema escolhido.
- **Perfil personalizável** (Você > Personalizar): cor do perfil (as 7 do AniList, que podem ir para o site, ou degradês especiais do app), banner (do AniList, da galeria ou de um anime da lista) e fundo animado (aurora, pétalas, estrelas, ondas, bolhas, brasas). Banner e fundo ficam só no app. Perfis de amigos usam a cor que eles escolheram no AniList.
- **Nota pela grade** (opcional, em Configurações > Listas e títulos).
- Configurações separadas por categoria.
- Tema sistema/claro/escuro/**preto (OLED)**, **Material You** (Android 12+; no MIUI as cores do papel de parede podem não vir) e escolha da **cor de destaque** (terracota, azul, verde, rosa, roxo, âmbar, vermelho).
- **Grade ou lista**: botão no topo do Início, da Lista e das listagens do Descobrir (vale para todas).
- **Toque longo em qualquer capa** abre o status rápido: marcar como Completo (já vi), Quero ver, Vendo etc., sem abrir a obra.
- Cada status tem um ícone próprio.
- Cada obra pinta a tela com a cor da capa: +1, barra de progresso e o Detalhe inteiro (fundo, banner desfocado). Status têm cores próprias.
- Descobrir com carrossel grande que avança sozinho; Anime/Mangá trocam deslizando o dedo.
- Animações: listas entram em cascata, carregamento com esqueleto brilhando, a capa "voa" da lista para o Detalhe, telas deslizam com mola, o +1 e os números de progresso rolam, banner com parallax, curtir "pula".

### Sincronização e modo offline
- O AniList é a fonte da verdade. Ao entrar (e ao puxar para baixo no Início, na Lista e em Você), o app envia a fila e baixa as listas completas de anime e mangá (`MediaListCollection`, em blocos de 500).
- Toda alteração (progresso, status, nota, datas, repetições, anotação, privado) é gravada no Room e entra numa fila. Cada item leva o estado completo da entrada, e os itens são enviados **na ordem em que foram feitos**. Vários "+1" seguidos na mesma obra viram um envio só. Sem conexão, o WorkManager envia quando a rede volta.
- Enquanto uma obra tem alteração pendente, a sincronização não sobrescreve a versão local.
- Favorito precisa de conexão, porque o AniList só oferece "alternar".

### Limite de requisições
O cliente lê `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` e `Retry-After` em cada resposta. Com poucas requisições restantes, espaça as próximas no ritmo do limite. Num 429, espera o tempo do `Retry-After` e tenta de novo (até 3 vezes). Consultas são agrupadas: as 6 fileiras do Descobrir de anime saem numa requisição só, as recomendações das suas 6 maiores notas também, e a agenda vem em páginas de 50. Em novembro de 2026 o AniList estava respondendo com limite de **30/min**.

### Notas
Toda nota é guardada como inteiro 0–100 (o `scoreRaw`, que é o valor enviado). Cada sistema é só uma forma de mostrar e digitar:

| Sistema | Grava | Mostra um valor do AniList |
|---|---|---|
| 0–100 | o número | o número |
| 0–10 com decimal | 7,5 → 75 | 75 → 7,5 |
| 0–10 | 7 → 70 | arredonda (72 → 7) |
| 5 estrelas | 3,5 → 70 | arredonda para meia estrela |
| 3 carinhas | 35 / 60 / 85 | a mais próxima |
| 0–7 e 7 carinhas | meio da faixa (7 = 97 … 1 = 10) | pela faixa base |
| Letras | tabela F−− … A++ | a letra mais próxima (72 → B−−) |

- Em empates de "mais próximo", vale o valor menor. Assim 84 vira B++ e 39 vira E++, o que bate com as faixas base.
- **Notas especiais** (S … SSS+++, só no sistema de letras) ficam na tabela `special_score` com a posição exata 1–12. O AniList recebe o valor comprimido (S/S+ = 95 … SSS++/SSS+++ = 100). Se a nota mudar no site e não bater mais, a especial é descartada na próxima sincronização.
- Sem nota especial guardada, 95–96 aparece como S, 97–98 como SS e 99–100 como SSS.
- Ordenar por nota usa `nota × 100 + posição especial`, então SSS+++ fica acima de tudo.

### Reviews
Regras conferidas no schema do AniList (`SaveReview`, novembro de 2026): resumo de **20 a 120** caracteres, texto com **no mínimo 2600** (o schema não define máximo), nota **0–100** e campo `private`. O botão *Publicar* só fica ativo dentro dessas regras. O rascunho é salvo no Room meio segundo depois de cada alteração.

### Notificações
Todas começam desligadas. Tipos: episódio novo, obra relacionada, mudança em obra da lista e social. A permissão (Android 13+) só é pedida quando você liga a primeira.
- **Episódio novo**: a cada 6 h (só com internet), um worker sincroniza e busca no `airingSchedule` os episódios das próximas 24 h dos animes em *Vendo/Revendo*. Depois agenda um trabalho para o horário de cada episódio. Episódios com até 30 min de diferença saem numa notificação só.
- **Obra relacionada adicionada** e **Mudança em obra da lista**: vêm das notificações do AniList, filtradas para os tipos não sociais.
- Xiaomi/Redmi: em *Configurações > Apps > ListaAnimes > Economia de bateria*, escolha *Sem restrições* para os avisos chegarem na hora.

### Backup
*Você > Configurações > Backup* exporta um JSON (pelo seletor de arquivos do Android) com o que só existe no app: notas especiais, sistemas de nota, meus serviços, rascunhos de review e configurações. Importar substitui esses dados.

## Testar no emulador
O APK de **debug** tem um modo *Visitante* na tela de login: digite um usuário público do AniList para abrir a lista dele só para leitura, sem login. O release não tem isso.

## Estrutura

```
app/src/main/graphql/            schema + operações GraphQL
app/src/main/java/dev/viko/listaanimes/
  domain/                        modelos, regras de progresso, review, texto
  domain/score/                  sistemas de nota, letras, notas especiais
  data/api/                      cliente Apollo/OkHttp e limite de requisições
  data/auth/                     login OAuth e token no Keystore
  data/db/                       Room (entidades e DAOs)
  data/repo/                     repositórios e mapeamento da API
  data/sync/                     fila offline, workers, notificações
  data/settings/, data/backup/   DataStore e backup JSON
  ui/                            tema, componentes e telas
app/src/test/                    testes unitários
```

## Créditos
Dados do [AniList](https://anilist.co). O app não é afiliado ao AniList. Fontes IBM Plex Sans e IBM Plex Mono (SIL Open Font License 1.1).
