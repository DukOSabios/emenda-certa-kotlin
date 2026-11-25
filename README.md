# Emenda Certa (Kotlin)

App Android que mostra os feriados nacionais do Brasil e diz se vale a pena emendar:
quantos dias de folga você ganha e quantos dias de férias precisa gastar.

> Feriado bom é o que cai numa terça ou quinta.

## O que ele faz

- **Perfil:** o usuário informa se é CLT, servidor público, estudante (escola ou faculdade) ou autônomo/MEI, em quais dias da semana trabalha ou tem aula, se a escala cobre feriados, se tem folga em Carnaval e Corpus Christi e quantos dias topa gastar por ano em emendas. O perfil abre na primeira vez e fica salvo no aparelho.
- **Calendário personalizado:** as emendas são recalculadas para a rotina de cada pessoa. Feriado que cai num dia em que ela já não trabalha aparece riscado ("Cai na sua folga"), e feriados vizinhos (Natal e Ano Novo, por exemplo) entram na conta.
- **Seu plano:** escolhe as emendas mais rentáveis que cabem no limite de dias do usuário e marca na lista e no calendário.
- **Duas visões:** lista e calendário mensal (vermelho: feriado; amarelo: dia que você gasta; cinza: folga que emenda).
- **Por modalidade:** o custo vira "dia de férias", "falta" ou "dia sem faturar", e a tela de cada feriado traz avisos do caso (CLT, servidor, estudante, autônomo).
- Abre com um texto corrido sobre o próximo feriado útil, com os números reais de hoje.
- Cada feriado abre como uma folhinha de parede, com a faixa dos dias, um palpite e uma dica.
- Tem estado de carregando, erro com "Tentar de novo" e reserva automática de API.

## Regra da emenda

Para cada feriado que cai num dia de trabalho ou aula, o app expande o bloco de folga em volta (fins de semana, dias fora da rotina e outros feriados) e testa pontes de 1 ou 2 dias úteis até a folga seguinte. Fica com a opção de maior nota.

| Resultado | Quando | Nota |
|---|---|---|
| Emenda de ouro | 1 dia gasto rende 4 ou mais dias seguidos | 10 |
| Já vem emendado | 0 dias gastos, 3 ou mais dias seguidos (8 se forem 4 ou mais) | 7 ou 8 |
| Emenda cara | 2 dias gastos (ou 1 dia que rende só 3) | 4 |
| Folga avulsa | 0 dias gastos, menos de 3 dias seguidos | 2 |
| Cai na sua folga | o feriado cai num dia em que você não trabalha | 0 |
| Dia normal pra você | ponto facultativo sem folga, ou escala que cobre feriados | 0 |

Exemplo para quem trabalha de segunda a sexta: terça vira "tire a segunda" (4 dias), quarta vira emenda cara (5 dias por 2 gastos), segunda e sexta já vêm emendadas.

## API pública

- Principal: [BrasilAPI](https://brasilapi.com.br/docs#tag/Feriados-Nacionais), `GET /api/feriados/v1/{ano}` (sem chave).
- Reserva: [Nager.Date](https://date.nager.at/Api), `GET /api/v3/PublicHolidays/{ano}/BR`.

## Decisões de design

A referência visual é a folhinha de parede, o calendário de arrancar de padaria e borracharia.

- **Cor com função:** vermelho é feriado, amarelo é marca-texto, preto é tinta. Não há degradê nem cor decorativa.
- **Tipografia:** Alfa Slab One só para números e títulos, Work Sans para o texto. Nenhuma fonte padrão do sistema.
- **Sem cartões:** a lista é plana, separada por filetes. O único objeto com borda é a folhinha.
- **Alinhado à esquerda:** o texto corrido e os títulos seguem a margem esquerda.
- **Texto específico:** a abertura e o placar usam datas e contagens reais (por exemplo, "Natal, daqui a 30 dias.").

## Tecnologias

Kotlin, Jetpack Compose, ViewModel + StateFlow, Coroutines, `HttpURLConnection` + `org.json`
(sem bibliotecas extras de rede), `SharedPreferences` para o perfil e `java.time`.

## Como rodar

1. Abra o **Android Studio** (Ladybug ou mais novo, de preferência) e escolha **Open**.
2. Selecione a pasta deste projeto (a que contém `settings.gradle.kts`).
3. Espere o **Gradle Sync** terminar (precisa de internet na primeira vez).
4. Em `Telas.kt`, troque `AUTOR` pelo seu nome.
5. Escolha um emulador ou um celular com depuração USB e clique em **Run**.

Não precisa criar projeto, copiar arquivos nem mexer no manifest: já está tudo configurado
(package `com.emendacerta`, Minimum SDK 26, permissão de internet, dependências do Compose).

## Estrutura

```
build.gradle.kts, settings.gradle.kts, gradle/   configuração do Gradle
app/src/main/AndroidManifest.xml                 permissão de internet e tela inicial
app/src/main/java/com/emendacerta/
  Logica.kt       perfil, regra da emenda, plano do ano, textos de cada feriado, textos de abertura
  Api.kt          chamada às APIs, ViewModel e perfil salvo no aparelho
  Telas.kt        visual (lista, calendário mensal, perfil e folhinha)
  MainActivity.kt
app/src/main/res/font/   Alfa Slab One e Work Sans
fonts-licenses/          licenças das fontes (SIL Open Font License)
```

Feito por Seu Nome.
