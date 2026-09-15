package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.llm.LlmRequestDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.feign.LlmClient;

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

  @Value("${llm.aggregation_model}")
  private String llmAggregationModel;

  @Value("${llm.analysis_model}")
  private String llmAnalysisModel;

  public String getSummary(List<String> chunks) {
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
            "quote": "дословная цитата",
            "entities": ["организации, персоны, продукты"],
            "date": "дата события или публикации",
            "assumptions": ["допущения, на которых держится утверждение"],
            "causal_links": [
              {"cause": "причина", "effect": "следствие", "mechanism": "как именно"}
            ],
            "counterarguments": ["что в тексте говорит против"],
            "conditions": ["при каких условиях утверждение верно"],
            "confidence": "явно | предположительно | оценочно"
          }
        ],
        "contradictions": [
          {"question": "вопрос спора", "position": "позиция автора"}
        ],
        "timeline": [
          {"date": "дата", "event": "событие"}
        ]
      }
  
      === ПРАВИЛА ===
      1. Извлекай ВСЕ утверждения. Лучше 40, чем 10.
      2. Каждый claim атомарен. «Вырос на 20%% и планирует IPO» → два claim.
      3. Для каждого forecast ОБЯЗАТЕЛЬНО заполни assumptions.
         Если в тексте допущение не указано явно — выведи его сам и пометь [выведено].
      4. Сохраняй все цифры, даты, имена дословно.
      5. Никакого текста вне JSON. Без markdown-обёрток.
  
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

    return fetchModelRequest(llmRequest);
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

    var llmRequest = LlmRequestDto.builder().
       model(llmSummaryModel)
      .maxTokens(2000)
      .temperature(0.3)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .responseFormat(new LlmRequestDto.ResponseFormat("json_object"))
      .build();

    return fetchModelRequest(llmRequest);
  }

  public String getAnalysisCluster(String userQuery,String cluster, List<SearchResponseDtoWithContent> contents) {
    var summaries = contents.stream().map(content -> content.getContent()).toList();
    log.warn("summuries {}", summaries);
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

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(4000)
      .temperature(0.5)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest);
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

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(5000)
      .temperature(0.7)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest);
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

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(2000)
      .temperature(0.7)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest);
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

    var llmRequest = LlmRequestDto.builder().
      model(llmSummaryModel)
      .maxTokens(7000)
      .temperature(0.9)
      .messages(List.of(new LlmRequestDto.Message("user", prompt)))
      .build();

    return fetchModelRequest(llmRequest);
  }


//  public String getAggregationReport(List<String> summaries) {
//    var aggregatorPrompt = String.format("""
//      Ты — агрегатор. На вход подаётся JSON-массив извлечённых данных
//      из 30–50 источников на одну тему. Каждый элемент массива — объект
//      с полями: source_url, topic, summary, claims[], contradictions[], timeline[].
//
//      Создай итоговый JSON-отчёт по схеме ниже. Все данные — ТОЛЬКО из входного массива.
//
//      === КАК РАБОТАТЬ С ВХОДОМ ===
//      1. Каждый claim из поля claims — это отдельное утверждение с полями
//         text, type, quote, entities, date.
//      2. Каждый claim привязан к source_url своего JSON-объекта — это его источник.
//      3. Если одинаковые по смыслу claims встречаются в разных JSON-объектах —
//         объедини их в один и собери все source_url в массив sources.
//      4. sources_count = число УНИКАЛЬНЫХ URL, подтверждающих claim.
//      5. Поле type у claim используй для классификации:
//         fact / statistic / date → в consensus или unique_facts,
//         opinion → в contradictions (если есть противоположные мнения),
//         forecast → в timeline.trends или в claims сущностей.
//
//      === СХЕМА JSON ===
//      {
//        "topic": "строка (главная тема, 1 предложение)",
//        "consensus": [
//          {
//            "claim": "строка (утверждение из >60%% источников)",
//            "sources_count": "число (уникальных URL)",
//            "sources": ["массив строк (URL из source_url)"]
//          }
//        ],
//        "contradictions": [
//          {
//            "question": "строка (вопрос, по которому есть разногласия)",
//            "group_a": {
//              "position": "строка (позиция группы А)",
//              "sources": ["массив строк (URL)"],
//              "count": "число"
//            },
//            "group_b": {
//              "position": "строка (позиция группы Б)",
//              "sources": ["массив строк (URL)"],
//              "count": "число"
//            }
//          }
//        ],
//        "unique_facts": [
//          {
//            "fact": "строка (факт из 1-2 источников)",
//            "source": "строка (URL)",
//            "importance": "high | medium | low"
//          }
//        ],
//        "clusters": {
//          "название_кластера": ["массив строк (URL)"]
//        },
//        "timeline": {
//          "events": [
//            {
//              "date": "строка (дата из claim.date или timeline)",
//              "event": "строка (событие)",
//              "sources_count": "число"
//            }
//          ],
//          "trends": ["массив строк (тренды)"]
//        },
//        "entities": {
//          "название_сущности": {
//            "mentions": "число (сколько раз упомянута во входе)",
//            "claims": ["массив строк (утверждения о сущности)"]
//          }
//        },
//        "missing_gaps": ["массив строк (вопросы без ответа)"],
//        "generated_questions": ["массив строк (уточняющие вопросы для пользователя)"]
//      }
//
//      === ПРАВИЛА ===
//      1. Все факты ТОЛЬКО из входного JSON — НЕ ВЫДУМЫВАЙ.
//      2. Каждому утверждению обязательно сопоставь source_url из входных данных.
//      3. Если данных для секции нет — пиши null или пустой массив.
//      4. Названия кластеров придумай сам, отражая суть позиции источников.
//      5. Все числа (sources_count, mentions) — реальный подсчёт по входу, не выдуманные.
//      6. Дедуплицируй claims: одинаковые по смыслу — в одну запись.
//      7. Claim в consensus попадает, только если подтверждён >60%% уникальных источников.
//      8. Claim, подтверждённый 1–2 источниками, идёт в unique_facts, а не в consensus.
//      9. Не пересказывай вход — агрегируй. Никакого текста вне JSON.
//
//      Входной JSON-массив:
//      %s
//      """, String.join(" ,", summaries));
//    return fetchModelRequest(aggregatorPrompt, llmAggregationModel, 2500, false);
//  }

//  public String getQueryAnalysis(String userQuery, String aggregationReport) {
//    var prompt = String.format("""
//      Ты — аналитик. У тебя есть JSON-отчёт по 30–50 источникам.
//      Создай структурированный ответ пользователю.
//      Каждый пересказ в конце содержит источник в виде урла. Используй его в тексте как ссылку для сносок.
//      === ЗАПРОС ПОЛЬЗОВАТЕЛЯ ===
//      %s
//      === JSON-ОТЧЁТ ===
//      %s
//      === СТРУКТУРА ОТВЕТА ===
//      ## Главный вывод
//      (2-3 предложения, самый важный инсайт из всех источников)
//      ## Детальный анализ по темам
//      (Разбей на 3-5 логических блоков)
//      - Тема 1: факты, количество источников, примеры URL
//      - Тема 2: факты, количество источников, примеры URL
//      ## Противоречия и спорные моменты
//      (Где источники расходятся?)
//      - Вопрос: ...
//        - Группа А (X источников): позиция → URL
//        - Группа Б (Y источников): позиция → URL
//      ## Уникальные инсайты
//      (Что нашли только в 1-2 источниках?)
//      - Инсайт 1 → источник
//      - Инсайт 2 → источник
//      ## Ключевые игроки и их позиции
//      (Компании/персоны, которые упоминаются чаще всего)
//      - Игрок 1: позиция, ключевые заявления
//      - Игрок 2: позиция, ключевые заявления
//      ## Хронология и тренды
//      (Как менялась ситуация во времени?)
//      - Ключевые события по датам
//      - Основные тренды
//      ## Чего не хватает
//      (Какие вопросы остались без ответа?)
//      ## Рекомендации и следующие шаги
//      (Что делать на основе анализа?)
//      - Рекомендация 1
//      - Уточняющий вопрос для пользователя
//      === ПРАВИЛА ===
//      1. Каждый факт подтверждай источниками: [Источник: url.com]
//      2. Для противоречий: [Группа А: url1, url2 | Группа Б: url3, url4]
//      3. НЕ ВЫДУМЫВАЙ факты, которых нет в отчёте
//      4. Будь конкретен: цифры, даты, имена
//      5. Если данных для раздела нет — пропусти его
//      6. В конце не надо блок Уточняющий вопрос для пользователя
//      7. Сразу делай красивое форматирование, а не кучей текст.
//      8. При анализе источников укажи их ранжирование по авторитетности
//      """, userQuery, aggregationReport);
//    return fetchModelRequest(prompt, llmAnalysisModel, 2000, true);
//  }

  private String fetchModelRequest(LlmRequestDto requestDto) {
    var model = requestDto.getModel();

    try {
      var response = llmClient.getResponses(requestDto, "Bearer " + llmToken);
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
