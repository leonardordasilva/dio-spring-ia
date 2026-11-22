# Orçamento por Voz com Spring AI

API REST de orçamento pessoal que entende **comandos de voz**. Você envia um áudio como
*"gastei 32 reais de Uber ontem"* ou *"quanto gastei com alimentação este mês?"* e a API:

1. recebe o arquivo de áudio;
2. transcreve o áudio em texto (Whisper);
3. usa um modelo de linguagem (ChatClient) para entender a intenção;
4. executa uma **função real** da aplicação via *Tool Calling* (criar ou consultar transações);
5. persiste os dados no banco;
6. gera a resposta final em texto e, se quiser, **em voz** (TTS).

Projeto criado para o desafio de Spring AI da DIO. O foco é mostrar como conectar IA a uma aplicação
real sem misturar responsabilidades: a IA decide *o que* fazer, o código da aplicação decide *como* e
*se* pode ser feito.

## Stack

- Java 21, Spring Boot 3.5, Spring AI 1.1 (OpenAI: chat, transcrição e voz)
- Spring Data JPA + H2 (arquivo local em `./data`)
- Bean Validation, Problem Details (RFC 9457), Actuator
- JUnit 5, Mockito, MockMvc (nenhum teste chama a OpenAI)

## Arquitetura

```
web/            Controllers REST, DTOs e tratamento global de erros
application/    Orquestração do fluxo de voz/texto (AssistantCommandService) e validação do áudio
ai/             Integração com IA: ChatClient (BudgetAssistant), ferramentas (BudgetTools),
                portas SpeechToTextClient / TextToSpeechClient e seus adaptadores Spring AI
service/        Regras de negócio: TransactionService, TransactionValidator, FinancialReportService
repository/     Spring Data JPA, consultas agregadas e filtros dinâmicos (Specifications)
domain/         Entidade Transaction e enums (tipo, categoria, canal)
audit/          Trilha de auditoria de cada comando enviado ao assistente
config/         ChatClient, relógio e propriedades de negócio (budget.*)
```

```
áudio ──► AudioValidator ──► SpeechToTextClient (Whisper) ──► BudgetAssistant (ChatClient)
                                                                   │  Tool Calling
                                                                   ▼
                                                     BudgetTools ──► TransactionService / FinancialReportService
                                                                   │                    │
                                                                   ▼                    ▼
                                             resposta ◄── TextToSpeechClient      TransactionValidator + JPA
                                                 │
                                                 └──► AuditService (command_audits)
```

Decisões importantes:

- **As ferramentas não têm regra de negócio.** `BudgetTools` só converte parâmetros e delega aos serviços.
  As mesmas regras valem para a IA e para o CRUD REST.
- **Validação antes de salvar.** `TransactionValidator` barra valor zero/negativo, mais de 2 casas decimais,
  valor acima do limite, data futura ou muito antiga, categoria incompatível com o tipo (ex.: "salário" como
  despesa) e **comandos duplicados** (a mesma transação repetida em até 2 minutos).
- **Erro de validação volta para o modelo**, como resultado estruturado (`sucesso=false` + `erros`), para que a
  IA explique o problema em vez de inventar um sucesso.
- **ToolContext** leva o canal (VOZ/TEXTO) e um registro das ferramentas executadas, sem enviar nada disso ao modelo.
- **Portas para IA de áudio** (`SpeechToTextClient`, `TextToSpeechClient`): trocar de provedor ou simular em teste
  não afeta o resto do código.
- **Auditoria** em transação própria: cada comando grava entrada, resposta, ferramentas, ids criados, duração e erro.

## Como rodar

Pré-requisitos: Java 21 e uma chave da OpenAI.

```bash
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

No Windows (PowerShell): `$env:OPENAI_API_KEY="sk-..."; .\mvnw.cmd spring-boot:run`

A API sobe em `http://localhost:8080`. Console do H2: `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:file:./data/orcamento`, usuário `sa`, sem senha).

### Configuração

| Propriedade / variável | Padrão | Descrição |
|---|---|---|
| `OPENAI_API_KEY` | — | Chave da OpenAI (obrigatória) |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | Modelo de chat |
| `spring.ai.openai.audio.transcription.options.language` | `pt` | Idioma da transcrição |
| `spring.ai.openai.audio.speech.options.voice` | `alloy` | Voz da resposta |
| `budget.max-amount` | `1000000.00` | Valor máximo por transação |
| `budget.max-past-years` | `5` | Limite de lançamentos retroativos |
| `budget.duplicate-window` | `2m` | Janela para detectar comando repetido |
| `budget.audio.max-size` | `10MB` | Tamanho máximo do áudio |
| `AI_LOG_LEVEL` | `INFO` | Use `DEBUG` para logar prompts e respostas da IA |

## Endpoints

### Assistente

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/v1/assistant/voice` | Áudio (multipart `audio`) → JSON com transcrição e resposta. `?includeAudio=true` inclui o mp3 em Base64 |
| `POST` | `/api/v1/assistant/voice/speech` | Áudio → **mp3** com a resposta falada. Transcrição e texto nos cabeçalhos `X-Transcription` e `X-Reply` (URL-encoded) |
| `POST` | `/api/v1/assistant/text` | Mesmo fluxo a partir de texto `{"text": "..."}`. Ótimo para testar sem microfone |

Formatos de áudio aceitos: mp3, mp4, mpeg, mpga, m4a, wav, webm, ogg, flac.

```bash
# Comando de voz, resposta em JSON
curl -F "audio=@gastei-uber.mp3" http://localhost:8080/api/v1/assistant/voice
```

```json
{
  "transcription": "Gastei 32 reais de Uber ontem.",
  "reply": "Anotado: R$ 32,00 de Uber em transporte, no dia 21 de novembro.",
  "toolsUsed": ["registrarTransacao"],
  "createdTransactionIds": [1]
}
```

```bash
# Comando de voz, resposta falada (salva em resposta.mp3)
curl -F "audio=@quanto-gastei.m4a" -o resposta.mp3 -D - http://localhost:8080/api/v1/assistant/voice/speech

# Comando em texto
curl -H "Content-Type: application/json" \
     -d '{"text":"onde estou gastando mais este mês?"}' \
     http://localhost:8080/api/v1/assistant/text
```

Exemplos de comandos que o assistente entende:

- "Recebi 5 mil de salário hoje" → `registrarTransacao`
- "Paguei 1.200 de aluguel no dia primeiro" → `registrarTransacao` com data
- "Qual meu saldo este mês?" → `consultarSaldo`
- "Quanto gastei por categoria em outubro?" → `totaisPorCategoria`
- "Me dá um resumo de novembro" → `resumoMensal`
- "Quais foram minhas corridas de Uber?" → `listarTransacoes` com filtro de texto
- "Quais meus três maiores gastos do mês?" → `maioresGastos`

### Transações (CRUD sem IA)

| Método | Caminho | Descrição |
|---|---|---|
| `POST` | `/api/v1/transactions` | Cria transação (mesmas validações do assistente) |
| `GET` | `/api/v1/transactions` | Lista paginada. Filtros: `type`, `category`, `from`, `to`, `q` (texto). Ex.: `?type=DESPESA&from=2026-11-01&sort=amount,desc` |
| `GET` | `/api/v1/transactions/{id}` | Busca por id |
| `DELETE` | `/api/v1/transactions/{id}` | Remove |

```bash
curl -H "Content-Type: application/json" \
     -d '{"description":"Mercado","amount":120.50,"type":"DESPESA","category":"ALIMENTACAO","date":"2026-11-20"}' \
     http://localhost:8080/api/v1/transactions
```

Tipos: `RECEITA`, `DESPESA`. Categorias: `ALIMENTACAO`, `TRANSPORTE`, `MORADIA`, `SAUDE`, `EDUCACAO`, `LAZER`,
`CONTAS`, `COMPRAS` (só despesa), `SALARIO`, `FREELANCE` (só receita), `INVESTIMENTOS`, `OUTROS` (ambos).
Sem `date`, usa a data de hoje.

### Relatórios

Sem datas, todos usam o mês atual.

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/api/v1/reports/balance?from=&to=` | Receitas, despesas e saldo |
| `GET` | `/api/v1/reports/by-category?type=DESPESA&from=&to=` | Total e percentual por categoria |
| `GET` | `/api/v1/reports/monthly?month=2026-11` | Resumo do mês e categoria com maior gasto |
| `GET` | `/api/v1/reports/top?type=DESPESA&limit=5` | Maiores transações do período |

### Auditoria

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/api/v1/audits` | Histórico de comandos: canal, entrada, resposta, ferramentas usadas, ids criados, sucesso/erro e duração |

### Erros

Todas as falhas seguem o formato Problem Details:

```json
{
  "type": "https://orcamento-voz.dio/problems/transacao-invalida",
  "title": "Transação inválida",
  "status": 422,
  "detail": "A transação viola regras de negócio",
  "errors": ["A categoria SALARIO não é compatível com o tipo DESPESA"]
}
```

| Status | Quando |
|---|---|
| 400 | Campos inválidos, parâmetro com formato errado, áudio ausente ou em formato não suportado |
| 404 | Transação não encontrada |
| 413 | Áudio maior que o limite |
| 422 | Regra de negócio violada (valor, data, categoria, duplicidade) |
| 502 | Erro no provedor de IA, ou voz não gerada (nesse caso `reply` traz o texto da resposta) |
| 503 | Provedor de IA temporariamente indisponível (rate limit, instabilidade) |

## Testes

```bash
./mvnw test
```

Nenhum teste precisa de chave nem de internet. O modelo de chat é simulado por `FakeModel`, que reproduz o
*loop* de Tool Calling: executa as ferramentas que o Spring AI registrou no prompt (com o mesmo `ToolContext`)
e devolve um texto final. Assim os testes cobrem a ligação real ChatClient → ferramentas → serviços → banco.

| Teste | O que cobre |
|---|---|
| `TransactionValidatorTest` | Regras de validação antes de salvar |
| `FinancialReportServiceTest` | Saldo, totais por categoria, resumo mensal, maiores gastos |
| `BudgetAssistantTest` | Prompt de sistema, registro das ferramentas, criação e consulta via Tool Calling, erro devolvido ao modelo |
| `AssistantCommandServiceTest` | Orquestração voz/texto, validação do áudio, auditoria de sucesso e falha, falha de TTS |
| `AssistantControllerTest`, `TransactionControllerTest` | Contrato HTTP e mapeamento de erros |
| `VoiceCommandFlowIntegrationTest` | Fluxo completo: upload → transcrição → IA → banco → voz → auditoria |

## Próximos passos

- Memória de conversa (`ChatMemory` + `conversationId`) para perguntas de acompanhamento ("e no mês passado?").
- Confirmação antes de gravar valores altos ("Confirma R$ 3.000 de aluguel?").
- Orçamentos por categoria com alertas ("você já usou 80% do limite de lazer").
- Edição e exclusão por voz ("apaga o último lançamento").
- Autenticação e transações por usuário.
- Banco PostgreSQL com migrações Flyway.

## Nova ideia de assistente na mesma base: contas a pagar por voz

A mesma arquitetura (áudio → transcrição → ChatClient → Tool Calling → persistência → voz → auditoria) serve para
um **assistente de contas a pagar**:

- *"Lembra de pagar a conta de luz de 180 reais dia 10"* → `agendarConta(descricao, valor, vencimento)`
- *"O que vence esta semana?"* → `listarContasAVencer(inicio, fim)`
- *"Já paguei a internet"* → `marcarComoPaga(id)`, que também cria a despesa no orçamento
- Um job agendado gera um resumo em áudio das contas do dia.

Só mudam o domínio (`Bill`, status), as ferramentas e o prompt de sistema. `SpeechToTextClient`,
`TextToSpeechClient`, `AssistantCommandService`, auditoria e tratamento de erros são reaproveitados.
