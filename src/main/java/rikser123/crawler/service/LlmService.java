package rikser123.crawler.service;

import feign.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.llm.LlmRequestDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.feign.LlmClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {
  private final LlmClient llmClient;

  @Value("${llm.token}")
  private String llmToken;

  @Value("${llm.summary_model}")
  private String llmSummaryModel;

  @Value("${llm.analysis_model}")
  private String llmAnalysisModel;

  @Value("${llm.synthesis_model}")
  private String llmSynthesisModel;

  @Value("${llm.summary-timeout}")
  private Integer summaryTimeout;

  private final Request.Options llmOptions;

  @Retryable(
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 5000)
  )
  public String getSummary(List<String> chunks) {
    var summaryOptions = new Request.Options(Duration.ofSeconds(5), Duration.ofSeconds(summaryTimeout), false);
    var prompt = String.format("""
      Ты — экстрактор фактов и аргументов. Из текста ниже извлеки атомарные утверждения и их контекст.

      === СХЕМА ===
      {
        "source_url": "URL источника (скопировать дословно из конца текста)",
        "topic": "тема текста, 1 предложение",
        "summary": "сжатый пересказ, 1–2 предложения",
        "claims": [
          {
            "text": "атомарное утверждение (одна мысль)",
            "type": "fact | statistic | date | forecast | opinion | definition",
            "entities": ["организации, персоны, продукты"],
            "date": "дата события или публикации",
            "assumptions": ["допущения, на которых держится утверждение"],
            "confidence": "явно | предположительно | оценочно"
          }
        ]
      }

      === ЧТО ИЗВЛЕКАТЬ ===
      Только утверждения, которые влияют на ответ пользователю:
      - конкретные факты с числами, датами, именами
      - статистика и метрики
      - прогнозы и оценки с указанием, кто их даёт
      - прямые цитаты ключевых фигур (в поле text, если это важно)
      - определения, если они раскрывают суть темы
      - противоречия между авторами (оформи как два отдельных claim)

      === ЧТО НЕ ИЗВЛЕКАТЬ ===
      - описание структуры статьи («в этой статье мы рассмотрим»)
      - общеизвестные факты без контекста («Биткоин — это криптовалюта»)
      - рекламные формулировки, призывы, ссылки на другие материалы
      - сведения об авторе статьи (если автор не является субъектом темы)
      - повторы одной и той же мысли разными словами — оставляй один claim
      - вводные конструкции и «воду»

      === ПРАВИЛА ===
      1. Целевой объём: 10–25 claims. Если в тексте только 5 значимых
         утверждений — верни 5, не добивай до 25 ради числа.
      2. Каждый claim атомарен. «Вырос на 20%% и планирует IPO» → два claim.
      3. Для каждого forecast ОБЯЗАТЕЛЬНО заполни assumptions.
         Если в тексте допущение не указано явно — выведи его сам
         и пометь [выведено].
      4. Сохраняй все цифры, даты, имена дословно. Не перефразируй числа.
      5. Если два утверждения противоречат друг другу — оставь оба,
         но пометь в text: «[противоречие с ...]».
      6. Никакого текста вне JSON. Без markdown-обёрток.

      Текст:
      %s
    """, String.join("\n\n---\n\n", chunks));

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.1)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .responseFormat(new LlmRequestDto.ResponseFormat("json_object"))
      .build();

    return fetchModelRequest(llmRequest, summaryOptions);
  }

  public String getClusters(String userQuery, List<SearchResponseDtoWithContent> searchDtos) {
    var passages = searchDtos.stream()
      .map(dto -> dto.getSearchResponse().getPassages() + "\\n источник " + dto.getSearchResponse().getUrl())
      .toList();

    var prompt = String.format("""
      Ты — классификатор. Запрос пользователя: %s

      На входе — JSON-массив кратких текстов для каждого источника.
      Сгруппируй их в 3–7 смысловых кластеров по темам.
  
      === СХЕМА ===
      {
        "clusters": [
          {
            "name": "название кластера (2–4 слова)",
            "essence": "суть кластера, 1–2 предложения",
            "sources": ["массив source_url"],
            "key_claims": ["3–5 самых важных утверждений кластера"],
          }
        ]    
      }
  
      === ПРАВИЛА ===
      1. Каждый source_url попадает ровно в один кластер.
      2. Кластеры должны отражать СМЫСЛ, а не типы источников.
      3. Если источник не подходит ни к одному кластеру — создай кластер «Прочее».
      4. key_claims — самые цитируемые/важные утверждения, не пересказ.
      5. Верни только JSON.
      6. source_url в конце пассажа отмечен как источник.
  
      Входной массив:
      %s
      """, userQuery, String.join("\n\n---\n\n", passages));

    log.info("getClusters");

    var llmRequest = LlmRequestDto.builder().
       model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.1)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .responseFormat(new LlmRequestDto.ResponseFormat("json_object"))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getAnalysisCluster(String userQuery,String cluster, List<SearchResponseDtoWithContent> contents) {
    var summaries = contents.stream().map(content -> content.getContent()).toList();
    var prompt = String.format("""
     Ты — аналитик. Запрос пользователя: %s

    Вот кластер источников на одну тему. Твоя задача — НЕ пересказать,
    а проанализировать, при чем, на кластер не обращай внимания, анализируй именно текст источников и на основании текстов источников давай ответы

    === ОТВЕТЬ НА ВОПРОСЫ ===
    1. ФАКТЫ. Что установлено как факт? С какими источниками?
    2. ПРОГНОЗЫ. Что прогнозируется? На каких допущениях?
       Если допущение не указано — выведи его и пометь [выведено].
    3. ПРОТИВОРЕЧИЯ. Кто с кем спорит и почему? Не «есть разногласия»,
       а конкретно: позиция A (источники) vs позиция B (источники).
    4. МЕХАНИЗМЫ. Какие причинно-следственные связи описаны?
       Где механизм объяснён, где только корреляция?
    5. СЛАБЫЕ МЕСТА. Какие утверждения держатся на ненадёжных источниках?
       Где авторы противоречат сами себе?
    6. ЧЕГО НЕ ХВАТАЕТ. Какие вопросы остались без ответа?
    7. ГЛАВНОЕ. Что самое важное в этом кластере? 2–3 предложения.

    === ПРАВИЛА ===
    1. Каждое утверждение подтверждай source_url.
    2. Не выдумывай факты, которых нет во входе.
    3. Если данных для вопроса нет — напиши «нет данных».
    4. Формат — markdown. Структура — по вопросам.
    5. Подробно анализируй источники, а не описание кластера, готовь ответ на осноовании источников.

    Кластер:
    %s
    Источники:
    %s
    """, userQuery, cluster, String.join("\n\n---\n\n", summaries));

    log.info("getAnalysisCluster");


    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.3)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getClustersSynthesis(String userQuery, List<String> clusters) {
    var prompt = String.format("""
         Ты — синтетик. Запрос пользователя: %s
    
        У тебя есть анализы по N кластерам. Твоя задача — найти то, что
        НЕ ВИДНО в отдельных кластерах. Это самое важное в анализе.
    
        === ОТВЕТЬ НА ВОПРОСЫ ===
        1. ОБЩИЕ ДОПУЩЕНИЯ. Какие допущения лежат в основе сразу нескольких
           кластеров? Если допущение неверно — какие выводы рушатся?
           Это ключевой вопрос. Ищи скрытые предпосылки.
    
        2. СВЯЗИ. Какие выводы из кластера A подтверждаются или опровергаются
           кластером B? Приведи конкретные пары с источниками.
    
        3. СЛЕДСТВИЯ. Какие выводы следуют из комбинации фактов, хотя ни в одном
           кластере не сформулированы явно? Помечай [синтез].
           Это то, ради чего существует этот шаг.
    
        4. МЕХАНИЗМЫ. Какие причинно-следственные связи проходят через несколько
           кластеров? Где механизм объяснён, где — только корреляция?
    
        5. ПРОТИВОРЕЧИЯ МЕЖДУ КЛАСТЕРАМИ. Где один кластер утверждает то,
           что другой отрицает? Не внутри кластера, а МЕЖДУ ними.
    
        6. ОБЩАЯ КАРТИНА. Какой вывод складывается из всего? 3–5 предложений.
           Что самое важное и почему?
    
        === ПРАВИЛА ===
        1. Каждый вывод подтверждай source_url или помечай [синтез].
        2. Не повторяй то, что уже было в кластерах. Только связи и следствия.
        3. Если связи нет — не выдумывай.
        4. Формат — markdown. Структура — по вопросам.
    
        Анализы кластеров:
        %s
        """, userQuery, String.join("\n\n---\n\n", clusters));

    log.info("getClustersSynthesis: {}");


    var llmRequest = LlmRequestDto.builder().
       model(llmSynthesisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(6000)
      .temperature(0.5)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getCritic(String userQuery, String synthesis) {
    var prompt = String.format("""
         Ты — скептик. Запрос пользователя: %s

        Твоя задача — РАЗРУШИТЬ выводы ниже. Не защищай, не балансируй.
        Атакуй.
    
        === НАЙДИ СЛАБЫЕ МЕСТА ===
        1. Какие выводы держатся на ненадёжных источниках?
           Укажи конкретные выводы и конкретные источники.
        2. Где авторы противоречат сами себе?
        3. Какие альтернативные объяснения не рассмотрены?
           Что ещё могло бы объяснить те же данные?
        4. Какие допущения не проверены и не обоснованы?
        5. Где данные могли бы опровергнуть вывод, но их нет?
           Что бы вы стали искать, чтобы проверить?
        6. Где корреляция выдаётся за причинность?
        7. Какие выводы [синтез] не обоснованы вообще?
        8. Что могло бы сделать выводы неверными?
           Сформулируй условия falsifiability.
    
        === ПРАВИЛА ===
        1. Не защищай. Только атакуй.
        2. Каждая атака — с обоснованием, а не «мне кажется».
        3. Если вывод выдерживает атаку — скажи это явно.
        4. Формат — markdown. Список слабых мест с обоснованием.
    
        Синтез:
        %s
        """, userQuery, synthesis);

    log.info("getCritic");


    var llmRequest = LlmRequestDto.builder().
       model(llmSynthesisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(5000)
      .temperature(0.6)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  public String getAnalysis(String userQuery, String synthesis, String critic, List<String> clusters) {
      var prompt = String.format("""
        Ты — автор финального аналитического отчёта.
        Запрос пользователя: %s
    
        У тебя есть:
        - кросс-тематический синтез
        - адверсариальная критика синтеза
        - исходные данные по кластерам
    
        === ЗАДАЧА ===
        Напиши отчёт, который отвечает на запрос пользователя.
    
        === СТРУКТУРА (пиши так, как считаешь правильным, это ориентир) ===
        1. Главный вывод: 2–3 предложения. Самое важное.
        2. Чего мы НЕ знаем: сразу после главного вывода.
           Это честность, которая ценится.
        3. Проверяемые факты: только fact / statistic / date.
           Каждый — с источником.
        4. Прогнозы и их допущения: только forecast.
           Для каждого прогноза укажи:
           - источник
           - допущения, на которых он держится
           - условия, при которых он неверен
           - уровень уверенности (высокий / средний / низкий)
        5. Противоречия: где источники расходятся и почему.
           Не «есть разногласия», а конкретные позиции с источниками.
        6. Синтез: выводы, которые следуют из комбинации источников,
           хотя ни в одном не сказаны явно. Помечай [синтез].
        7. Мнения и оценки: только opinion. Субъективные позиции.
        8. Ключевые игроки: кто чаще всего упоминается и что говорит.
        9. Хронология: события по датам.
        10. Что дальше: рекомендации и уточняющие вопросы.
    
        === ПРАВИЛА ===
        1. Учитывай критику. Если вывод не выдержал — не включай
           или включай с явной оговоркой.
        2. Каждый факт — со ссылкой [Источник: url].
        3. Для противоречий: [Группа А: url1, url2 | Группа Б: url3].
        4. Не выдумывай. Если данных нет — пиши «нет данных».
        5. Будь конкретен: цифры, даты, имена. Без воды.
        6. Формат — markdown с заголовками, таблицами, списками.
           Не кучей текста.
    
        === КРОСС-СИНТЕЗ ===
        %s
    
        === КРИТИКА СИНТЕЗА ===
        %s
    
        === ИСХОДНЫЕ ДАННЫЕ ПО КЛАСТЕРАМ===
        %s
    """, userQuery, synthesis, critic, clusters);

    log.info("getAnalysis: {}");


    var llmRequest = LlmRequestDto.builder().
      model(llmAnalysisModel)
      .enableThinking(true)
      .reasoningEffort("medium")
      .maxTokens(8000)
      .temperature(0.3)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest, llmOptions);
  }

  private String fetchModelRequest(LlmRequestDto requestDto, Request.Options options) {
    var model = requestDto.getModel();

    try {
      var response = llmClient.getResponses(requestDto, "Bearer " + llmToken, options);
      if (!Objects.isNull(response.getError())) {
        log.warn("Не удалось получить ответ от", model);
        throw new IllegalStateException("Не удалось получить ответ модели");
      }
      return response.getContent();

    } catch (Exception e) {
      log.warn("Не удалось получить ответ от {}", model, e);
      throw new IllegalStateException("Не удалось получить ответ модели");
    }

  }
}
